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
import fr.pillulier.app.widget.RafraichirWidget
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
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EnregistrerPrescriptionTest {

    private lateinit var base: PillulierDatabase
    private lateinit var medicaments: DepotMedicaments
    private lateinit var ordonnances: DepotOrdonnances
    private lateinit var prescrire: EnregistrerPrescription
    private lateinit var creer: EnregistrerMedicament
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 5, 7, 0))

    @Before
    fun preparer() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        ).addCallback(momentsParDefaut).allowMainThreadQueries().build()

        medicaments = DepotMedicaments(base.medicaments())
        ordonnances = DepotOrdonnances(base.ordonnances(), base)
        val reArmer = ReArmerRappels(
            ordonnances = ordonnances,
            medicaments = medicaments,
            moments = DepotMoments(base.moments()),
            evenements = DepotEvenements(base.evenements()),
            programmateur = ProgrammateurEspion(),
            horloge = horloge,
        )
        val rafraichirWidget = RafraichirWidget(ApplicationProvider.getApplicationContext())
        prescrire = EnregistrerPrescription(ordonnances, reArmer, rafraichirWidget)
        creer = EnregistrerMedicament(medicaments, ordonnances, reArmer, rafraichirWidget)
    }

    @After
    fun fermer() = base.close()

    private suspend fun medicamentQuotidien(): Long = creer(
        medicament = Medicament(
            id = 0,
            nom = "Levothyrox",
            dosage = "75 µg",
            forme = Forme.COMPRIME,
            unitesParBoite = 30,
            stockUnites = 30.0,
            seuilAlerteJours = 7,
            seuilAlerteUnites = null,
            critique = false,
        ),
        type = TypeOrdonnance.PLANIFIEE,
        rythme = Rythme.TousLesJours,
        dateDebut = LocalDate.of(2026, 1, 1),
        dateFin = null,
        doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
        dateEffet = LocalDate.of(2026, 1, 1),
    )

    @Test
    fun `modifier la prescription ne touche pas au stock`() = runTest {
        val id = medicamentQuotidien()
        base.medicaments().ajusterStock(id, -4.0)

        prescrire(
            medicamentId = id,
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.SOIR, 2.0)),
            dateEffet = LocalDate.of(2026, 1, 5),
        )

        assertEquals(26.0, medicaments.parId(id)!!.stockUnites)
        assertEquals("Levothyrox", medicaments.parId(id)!!.nom)
        assertEquals(2, ordonnances.versionsDe(id).size)
    }

    @Test
    fun `une correction peut remonter avant le debut de la version courante`() = runTest {
        // Le garde dateEffet >= dateDebut ne vaut qu a la creation : il reste
        // dans EnregistrerMedicament et ne doit pas descendre ici.
        val id = medicamentQuotidien()

        prescrire(
            medicamentId = id,
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.SOIR, 2.0)),
            dateEffet = LocalDate.of(2025, 12, 20),
        )

        assertEquals(
            LocalDate.of(2025, 12, 20),
            ordonnances.versionsDe(id).single().ordonnance.dateDebut,
        )
    }

    @Test
    fun `une ordonnance planifiee sans dose est refusee`() = runTest {
        val id = medicamentQuotidien()

        assertFailsWith<IllegalArgumentException> {
            prescrire(
                medicamentId = id,
                type = TypeOrdonnance.PLANIFIEE,
                rythme = Rythme.TousLesJours,
                dateDebut = LocalDate.of(2026, 1, 1),
                dateFin = null,
                doses = emptyList(),
                dateEffet = LocalDate.of(2026, 1, 5),
            )
        }
        assertEquals(1, ordonnances.versionsDe(id).size)
    }

    @Test
    fun `une date de fin avant la date de debut est refusee`() = runTest {
        val id = medicamentQuotidien()

        assertFailsWith<IllegalArgumentException> {
            prescrire(
                medicamentId = id,
                type = TypeOrdonnance.PLANIFIEE,
                rythme = Rythme.TousLesJours,
                dateDebut = LocalDate.of(2026, 1, 10),
                dateFin = LocalDate.of(2026, 1, 5),
                doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
                dateEffet = LocalDate.of(2026, 1, 10),
            )
        }
    }

    @Test
    fun `une ordonnance a la demande ignore les doses`() = runTest {
        val id = medicamentQuotidien()

        prescrire(
            medicamentId = id,
            type = TypeOrdonnance.A_LA_DEMANDE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
            dateEffet = LocalDate.of(2026, 1, 5),
        )

        val derniere = ordonnances.versionsDe(id).maxBy { it.ordonnance.dateDebut }
        assertEquals(TypeOrdonnance.A_LA_DEMANDE, derniere.ordonnance.type)
        assertTrue(derniere.doses.isEmpty())
    }
}
