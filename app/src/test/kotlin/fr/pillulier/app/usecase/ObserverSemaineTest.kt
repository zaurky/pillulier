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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ObserverSemaineTest {

    private lateinit var base: PillulierDatabase
    private lateinit var medicaments: DepotMedicaments
    private lateinit var ordonnances: DepotOrdonnances
    private lateinit var enregistrer: EnregistrerPrise
    private lateinit var observer: ObserverSemaine

    /** Le 2026-01-07 est un mercredi ; la semaine commence le lundi 5. */
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 7, 13, 0))
    private val lundi = LocalDate.of(2026, 1, 5)

    @Before
    fun preparer() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        ).addCallback(momentsParDefaut).allowMainThreadQueries().build()

        medicaments = DepotMedicaments(base.medicaments())
        ordonnances = DepotOrdonnances(base.ordonnances(), base)
        enregistrer = EnregistrerPrise(base, base.evenements(), base.medicaments(), horloge)
        observer = ObserverSemaine(
            medicaments = medicaments,
            ordonnances = ordonnances,
            moments = DepotMoments(base.moments()),
            evenements = DepotEvenements(base.evenements()),
            horloge = horloge,
        )
    }

    @After
    fun fermer() = base.close()

    private suspend fun planifie(nom: String, rythme: Rythme, doses: List<DosePrescrite>): Long {
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
                critique = false,
            ),
        )
        ordonnances.enregistrerVersion(
            medicamentId = id,
            ordonnance = Ordonnance(
                id = 0,
                medicamentId = id,
                type = TypeOrdonnance.PLANIFIEE,
                rythme = rythme,
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
    fun `la semaine couvre sept jours a partir du debut demande`() = runTest {
        planifie("Levothyrox", Rythme.TousLesJours, listOf(DosePrescrite(Moment.MATIN, 1.0)))

        val etat = observer(lundi).first()

        assertEquals(7, etat.jours.size)
        assertEquals(lundi, etat.jours.first())
        assertEquals(LocalDate.of(2026, 1, 11), etat.jours.last())
    }

    @Test
    fun `une cellule porte le nom et la dose du medicament attendu`() = runTest {
        planifie("Levothyrox", Rythme.TousLesJours, listOf(DosePrescrite(Moment.MATIN, 0.5)))

        val cellule = observer(lundi).first().cellules.getValue(lundi to Moment.MATIN).single()

        assertEquals("Levothyrox", cellule.nom)
        assertEquals("½ comprimé", cellule.libelleDose)
    }

    @Test
    fun `un moment sans prise n a pas de cellule`() = runTest {
        planifie("Levothyrox", Rythme.TousLesJours, listOf(DosePrescrite(Moment.MATIN, 1.0)))

        val cellules = observer(lundi).first().cellules

        assertTrue((lundi to Moment.COUCHER) !in cellules)
    }

    @Test
    fun `les jours passes sans evenement sont oublies et le jour courant reste ouvert`() = runTest {
        planifie("Levothyrox", Rythme.TousLesJours, listOf(DosePrescrite(Moment.SOIR, 1.0)))

        val cellules = observer(lundi).first().cellules

        assertEquals(
            StatutPrise.OUBLIEE,
            cellules.getValue(lundi to Moment.SOIR).single().statut,
        )
        assertEquals(
            StatutPrise.A_VENIR,
            cellules.getValue(LocalDate.of(2026, 1, 7) to Moment.SOIR).single().statut,
        )
    }

    @Test
    fun `une prise enregistree apparait comme prise dans la grille`() = runTest {
        val id = planifie("Levothyrox", Rythme.TousLesJours, listOf(DosePrescrite(Moment.MATIN, 1.0)))
        enregistrer(id, LocalDate.of(2026, 1, 7), Moment.MATIN, dose = 1.0)

        val cellule = observer(lundi).first()
            .cellules
            .getValue(LocalDate.of(2026, 1, 7) to Moment.MATIN)
            .single()

        assertEquals(StatutPrise.PRISE, cellule.statut)
    }

    @Test
    fun `un rythme hebdomadaire ne remplit que ses jours`() = runTest {
        planifie(
            "Methotrexate",
            Rythme.JoursDeSemaine(setOf(DayOfWeek.MONDAY)),
            listOf(DosePrescrite(Moment.MATIN, 1.0)),
        )

        val cellules = observer(lundi).first().cellules

        assertTrue((lundi to Moment.MATIN) in cellules)
        assertTrue((LocalDate.of(2026, 1, 6) to Moment.MATIN) !in cellules)
    }
}
