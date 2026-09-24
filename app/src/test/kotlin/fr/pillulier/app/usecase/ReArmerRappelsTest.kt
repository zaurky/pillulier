package fr.pillulier.app.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.DepotEvenements
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotMoments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.app.rappels.ProgrammateurAlarmes
import fr.pillulier.domain.CleRappel
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Ordonnance
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.TypeOrdonnance
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgrammateurEspion : ProgrammateurAlarmes {
    data class Programmation(val cle: CleRappel, val quand: LocalDateTime, val critique: Boolean)

    val programmees = mutableListOf<Programmation>()
    val annulees = mutableListOf<CleRappel>()

    override fun programmer(cle: CleRappel, quand: LocalDateTime, critique: Boolean) {
        programmees += Programmation(cle, quand, critique)
    }

    override fun annuler(cle: CleRappel) {
        annulees += cle
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ReArmerRappelsTest {

    private lateinit var base: PillulierDatabase
    private lateinit var medicaments: DepotMedicaments
    private lateinit var ordonnances: DepotOrdonnances
    private lateinit var enregistrer: EnregistrerPrise
    private val programmateur = ProgrammateurEspion()
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 5, 7, 0))
    private lateinit var reArmer: ReArmerRappels

    @Before
    fun ouvrir() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        ).addCallback(momentsParDefaut).allowMainThreadQueries().build()
        medicaments = DepotMedicaments(base.medicaments())
        ordonnances = DepotOrdonnances(base.ordonnances(), base)
        enregistrer = EnregistrerPrise(base, base.evenements(), base.medicaments(), horloge)
        reArmer = ReArmerRappels(
            ordonnances = ordonnances,
            medicaments = medicaments,
            moments = DepotMoments(base.moments()),
            evenements = DepotEvenements(base.evenements()),
            programmateur = programmateur,
            horloge = horloge,
        )
    }

    @After
    fun fermer() = base.close()

    private suspend fun medicamentPlanifie(
        nom: String,
        critique: Boolean = false,
        doses: List<DosePrescrite> = listOf(DosePrescrite(Moment.MATIN, 1.0)),
    ): Long {
        val id = medicaments.enregistrer(
            Medicament(
                id = 0,
                nom = nom,
                dosage = "1 mg",
                forme = Forme.COMPRIME,
                unitesParBoite = 30,
                stockUnites = 30.0,
                seuilAlerteJours = 7,
                seuilAlerteUnites = null,
                critique = critique,
            ),
        )
        ordonnances.enregistrerVersion(
            medicamentId = id,
            ordonnance = Ordonnance(
                id = 0,
                medicamentId = id,
                type = TypeOrdonnance.PLANIFIEE,
                rythme = Rythme.TousLesJours,
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
                dateAncrage = LocalDate.of(2026, 1, 1),
            ),
            doses = doses,
            dateEffet = LocalDate.of(2026, 1, 1),
        )
        return id
    }

    @Test
    fun `la fenetre couvre trois jours`() = runTest {
        medicamentPlanifie("Levothyrox")

        reArmer()

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 1, 5, 8, 0),
                LocalDateTime.of(2026, 1, 6, 8, 0),
                LocalDateTime.of(2026, 1, 7, 8, 0),
            ),
            programmateur.programmees.map { it.quand },
        )
    }

    @Test
    fun `le drapeau critique du medicament est transmis a l alarme`() = runTest {
        medicamentPlanifie("Insuline", critique = true)

        reArmer()

        assertTrue(programmateur.programmees.all { it.critique })
    }

    @Test
    fun `une prise deja enregistree n est pas reprogrammee`() = runTest {
        val id = medicamentPlanifie("Levothyrox")
        enregistrer(id, LocalDate.of(2026, 1, 5), Moment.MATIN, dose = 1.0)

        reArmer()

        assertEquals(
            listOf(LocalDate.of(2026, 1, 6), LocalDate.of(2026, 1, 7)),
            programmateur.programmees.map { it.cle.date },
        )
    }

    @Test
    fun `une prise du jour deja passee est reprogrammee juste apres maintenant`() = runTest {
        horloge.avancerA(LocalDateTime.of(2026, 1, 5, 9, 30))
        medicamentPlanifie("Levothyrox")

        reArmer()

        assertEquals(
            LocalDateTime.of(2026, 1, 5, 9, 31),
            programmateur.programmees.first { it.cle.date == LocalDate.of(2026, 1, 5) }.quand,
        )
    }

    @Test
    fun `la veille et la fenetre sont annulees avant d etre reprogrammees`() = runTest {
        medicamentPlanifie("Levothyrox")

        reArmer()

        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 4),
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 1, 6),
                LocalDate.of(2026, 1, 7),
            ),
            programmateur.annulees.map { it.date }.distinct(),
        )
        assertEquals(16, programmateur.annulees.size, "4 jours x 4 moments pour un medicament")
    }

    @Test
    fun `une dose retiree de l ordonnance voit son alarme annulee au rearmement`() = runTest {
        val id = medicamentPlanifie(
            "Metformine",
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0), DosePrescrite(Moment.SOIR, 1.0)),
        )
        reArmer()
        programmateur.programmees.clear()
        programmateur.annulees.clear()

        // L'ordonnance perd sa dose du soir.
        ordonnances.enregistrerVersion(
            medicamentId = id,
            ordonnance = Ordonnance(
                id = 0,
                medicamentId = id,
                type = TypeOrdonnance.PLANIFIEE,
                rythme = Rythme.TousLesJours,
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
                dateAncrage = LocalDate.of(2026, 1, 1),
            ),
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
            dateEffet = LocalDate.of(2026, 1, 1),
        )
        reArmer()

        assertTrue(CleRappel(id, LocalDate.of(2026, 1, 5), Moment.SOIR) in programmateur.annulees)
        assertTrue(programmateur.programmees.none { it.cle.moment == Moment.SOIR })
    }

    @Test
    fun `un medicament a la demande ne produit aucune alarme`() = runTest {
        val id = medicaments.enregistrer(
            Medicament(
                id = 0,
                nom = "Doliprane",
                dosage = "500 mg",
                forme = Forme.COMPRIME,
                unitesParBoite = 16,
                stockUnites = 16.0,
                seuilAlerteJours = null,
                seuilAlerteUnites = 5,
                critique = false,
            ),
        )
        ordonnances.enregistrerVersion(
            medicamentId = id,
            ordonnance = Ordonnance(
                id = 0,
                medicamentId = id,
                type = TypeOrdonnance.A_LA_DEMANDE,
                rythme = Rythme.TousLesJours,
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
                dateAncrage = LocalDate.of(2026, 1, 1),
            ),
            doses = emptyList(),
            dateEffet = LocalDate.of(2026, 1, 1),
        )

        reArmer()

        assertTrue(programmateur.programmees.isEmpty())
    }

    @Test
    fun `deux doses le meme jour donnent deux alarmes distinctes`() = runTest {
        medicamentPlanifie(
            "Metformine",
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0), DosePrescrite(Moment.SOIR, 1.0)),
        )

        reArmer()

        val duJour = programmateur.programmees.filter { it.cle.date == LocalDate.of(2026, 1, 5) }
        assertEquals(setOf(Moment.MATIN, Moment.SOIR), duJour.map { it.cle.moment }.toSet())
    }
}
