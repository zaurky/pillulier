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
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Ordonnance
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.StatutPrise
import fr.pillulier.domain.TypeOrdonnance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ObserverJourneeTest {

    private lateinit var base: PillulierDatabase
    private lateinit var medicaments: DepotMedicaments
    private lateinit var ordonnances: DepotOrdonnances
    private lateinit var observer: ObserverJournee
    private lateinit var enregistrer: EnregistrerPrise
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 5, 13, 0))

    @Before
    fun preparer() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        ).addCallback(momentsParDefaut).allowMainThreadQueries().build()

        medicaments = DepotMedicaments(base.medicaments())
        ordonnances = DepotOrdonnances(base.ordonnances(), base)
        enregistrer = EnregistrerPrise(base, base.evenements(), base.medicaments(), horloge)
        observer = ObserverJournee(
            medicaments = medicaments,
            ordonnances = ordonnances,
            moments = DepotMoments(base.moments()),
            evenements = DepotEvenements(base.evenements()),
            horloge = horloge,
        )
    }

    @After
    fun fermer() = base.close()

    private suspend fun planifie(nom: String, doses: List<DosePrescrite>): Long {
        val id = medicaments.enregistrer(
            Medicament(
                id = 0,
                nom = nom,
                dosage = "500 mg",
                forme = Forme.COMPRIME,
                unitesParBoite = 30,
                stockUnites = 30.0,
                seuilAlerteJours = 7,
                seuilAlerteUnites = null,
                critique = false,
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
    fun `la journee est triee par heure et porte le nom du medicament`() = runTest {
        planifie("Levothyrox", listOf(DosePrescrite(Moment.SOIR, 1.0)))
        planifie("Metformine", listOf(DosePrescrite(Moment.MATIN, 2.0)))

        val lignes = observer(LocalDate.of(2026, 1, 5)).first()

        assertEquals(listOf("Metformine", "Levothyrox"), lignes.map { it.nom })
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(19, 0)), lignes.map { it.heure })
    }

    @Test
    fun `la dose est mise en forme pour l affichage`() = runTest {
        planifie("Levothyrox", listOf(DosePrescrite(Moment.MATIN, 0.5)))

        val ligne = observer(LocalDate.of(2026, 1, 5)).first().single()

        assertEquals("½ comprimé", ligne.libelleDose)
    }

    @Test
    fun `les statuts refletent l heure courante et le journal`() = runTest {
        val id = planifie(
            "Metformine",
            listOf(
                DosePrescrite(Moment.MATIN, 1.0),
                DosePrescrite(Moment.MIDI, 1.0),
                DosePrescrite(Moment.SOIR, 1.0),
            ),
        )
        enregistrer(id, LocalDate.of(2026, 1, 5), Moment.MATIN, dose = 1.0)

        val lignes = observer(LocalDate.of(2026, 1, 5)).first()

        assertEquals(
            listOf(StatutPrise.PRISE, StatutPrise.EN_RETARD, StatutPrise.A_VENIR),
            lignes.map { it.statut },
        )
    }

    @Test
    fun `une journee passee sans evenement est oubliee`() = runTest {
        planifie("Levothyrox", listOf(DosePrescrite(Moment.MATIN, 1.0)))

        val lignes = observer(LocalDate.of(2026, 1, 4)).first()

        assertEquals(StatutPrise.OUBLIEE, lignes.single().statut)
    }

    @Test
    fun `un medicament sans ordonnance planifiee n apparait pas`() = runTest {
        medicaments.enregistrer(
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

        assertEquals(emptyList(), observer(LocalDate.of(2026, 1, 5)).first())
    }
}
