package fr.pillulier.app.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotMoments
import fr.pillulier.app.data.DepotEvenements
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.app.rappels.Notifications
import fr.pillulier.domain.CleRappel
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EnregistrerMedicamentTest {

    private lateinit var base: PillulierDatabase
    private lateinit var medicaments: DepotMedicaments
    private lateinit var ordonnances: DepotOrdonnances
    private lateinit var enregistrer: EnregistrerMedicament
    private lateinit var supprimer: SupprimerMedicament
    private val programmateur = ProgrammateurEspion()
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 5, 7, 0))

    @Before
    fun preparer() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        ).addCallback(momentsParDefaut).allowMainThreadQueries().build()

        medicaments = DepotMedicaments(base.medicaments())
        ordonnances = DepotOrdonnances(base.ordonnances())
        val reArmer = ReArmerRappels(
            ordonnances = ordonnances,
            medicaments = medicaments,
            moments = DepotMoments(base.moments()),
            evenements = DepotEvenements(base.evenements()),
            programmateur = programmateur,
            horloge = horloge,
        )
        enregistrer = EnregistrerMedicament(medicaments, ordonnances, reArmer)
        supprimer = SupprimerMedicament(
            medicaments = medicaments,
            programmateur = programmateur,
            notifications = Notifications(ApplicationProvider.getApplicationContext()),
            reArmerRappels = reArmer,
            horloge = horloge,
        )
    }

    @After
    fun fermer() = base.close()

    private fun levothyrox(id: Long = 0) = Medicament(
        id = id,
        nom = "Levothyrox",
        dosage = "75 µg",
        forme = Forme.COMPRIME,
        unitesParBoite = 30,
        stockUnites = 30.0,
        seuilAlerteJours = 7,
        seuilAlerteUnites = null,
        critique = false,
    )

    @Test
    fun `enregistrer cree le medicament et son ordonnance`() = runTest {
        val id = enregistrer(
            medicament = levothyrox(),
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
        )

        assertEquals("Levothyrox", medicaments.parId(id)!!.nom)
        assertEquals(1, ordonnances.pourMedicament(id)!!.doses.size)
    }

    @Test
    fun `enregistrer rearme les rappels`() = runTest {
        enregistrer(
            medicament = levothyrox(),
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
        )

        assertEquals(3, programmateur.programmees.size)
    }

    @Test
    fun `une ordonnance planifiee sans dose est refusee`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            enregistrer(
                medicament = levothyrox(),
                type = TypeOrdonnance.PLANIFIEE,
                rythme = Rythme.TousLesJours,
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
                doses = emptyList(),
            )
        }
    }

    @Test
    fun `un nom vide est refuse`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            enregistrer(
                medicament = levothyrox().copy(nom = "  "),
                type = TypeOrdonnance.PLANIFIEE,
                rythme = Rythme.TousLesJours,
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
                doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
            )
        }
    }

    @Test
    fun `une date de fin avant la date de debut est refusee`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            enregistrer(
                medicament = levothyrox(),
                type = TypeOrdonnance.PLANIFIEE,
                rythme = Rythme.TousLesJours,
                dateDebut = LocalDate.of(2026, 1, 10),
                dateFin = LocalDate.of(2026, 1, 5),
                doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
            )
        }
    }

    @Test
    fun `une ordonnance a la demande accepte l absence de dose`() = runTest {
        val id = enregistrer(
            medicament = levothyrox().copy(nom = "Doliprane", seuilAlerteJours = null, seuilAlerteUnites = 5),
            type = TypeOrdonnance.A_LA_DEMANDE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = emptyList(),
        )

        assertTrue(ordonnances.pourMedicament(id)!!.doses.isEmpty())
    }

    @Test
    fun `reenregistrer un medicament existant le met a jour sans le dupliquer`() = runTest {
        val id = enregistrer(
            medicament = levothyrox(),
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
        )

        enregistrer(
            medicament = levothyrox(id = id).copy(dosage = "100 µg"),
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.SOIR, 2.0)),
        )

        assertEquals(1, medicaments.tous().size)
        assertEquals("100 µg", medicaments.parId(id)!!.dosage)
        assertEquals(listOf(Moment.SOIR), ordonnances.pourMedicament(id)!!.doses.map { it.moment })
    }

    @Test
    fun `supprimer un medicament supprime son ordonnance`() = runTest {
        val id = enregistrer(
            medicament = levothyrox(),
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
        )

        supprimer(id)

        assertNull(medicaments.parId(id))
        assertNull(ordonnances.pourMedicament(id))
    }

    @Test
    fun `supprimer annule les alarmes du medicament avant de l effacer`() = runTest {
        val id = enregistrer(
            medicament = levothyrox(),
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
        )
        programmateur.annulees.clear()

        supprimer(id)

        // La veille plus la fenêtre, les quatre moments : le réarmement qui suit
        // ne verrait plus cet identifiant dans `medicaments.tous()`.
        val attendues = (-1L until ReArmerRappels.JOURS_FENETRE).flatMap { decalage ->
            Moment.entries.map { moment ->
                CleRappel(id, LocalDate.of(2026, 1, 5).plusDays(decalage), moment)
            }
        }
        assertTrue(
            programmateur.annulees.containsAll(attendues),
            "les alarmes du medicament supprime doivent etre annulees",
        )
    }
}
