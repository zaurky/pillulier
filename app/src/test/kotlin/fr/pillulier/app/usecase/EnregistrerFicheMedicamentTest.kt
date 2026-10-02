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
class EnregistrerFicheMedicamentTest {

    private lateinit var base: PillulierDatabase
    private lateinit var medicaments: DepotMedicaments
    private lateinit var ordonnances: DepotOrdonnances
    private lateinit var fiche: EnregistrerFicheMedicament
    private lateinit var creer: EnregistrerMedicament
    private val programmateur = ProgrammateurEspion()
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
            programmateur = programmateur,
            horloge = horloge,
        )
        val rafraichirWidget = RafraichirWidget(ApplicationProvider.getApplicationContext())
        fiche = EnregistrerFicheMedicament(medicaments, reArmer, rafraichirWidget)
        creer = EnregistrerMedicament(medicaments, ordonnances, reArmer, rafraichirWidget)
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

    private suspend fun medicamentQuotidien(): Long = creer(
        medicament = levothyrox(),
        type = TypeOrdonnance.PLANIFIEE,
        rythme = Rythme.TousLesJours,
        dateDebut = LocalDate.of(2026, 1, 1),
        dateFin = null,
        doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
        dateEffet = LocalDate.of(2026, 1, 1),
    )

    @Test
    fun `renommer n ecrit aucune version d ordonnance`() = runTest {
        val id = medicamentQuotidien()
        val avant = ordonnances.versionsDe(id)

        fiche(medicaments.parId(id)!!.copy(nom = "Levothyrox 100", stockUnites = 12.0))

        assertEquals("Levothyrox 100", medicaments.parId(id)!!.nom)
        assertEquals(12.0, medicaments.parId(id)!!.stockUnites)
        assertEquals(avant, ordonnances.versionsDe(id), "la fiche ne doit pas toucher aux ordonnances")
    }

    @Test
    fun `passer la fiche en critique rearme en alarme`() = runTest {
        val id = medicamentQuotidien()
        programmateur.programmees.clear()

        fiche(medicaments.parId(id)!!.copy(critique = true))

        assertTrue(programmateur.programmees.isNotEmpty(), "critique change le canal de rappel : il faut rearmer")
        assertTrue(programmateur.programmees.all { it.critique }, "les rappels rearmes doivent etre des alarmes")
    }

    @Test
    fun `un nom vide est refuse`() = runTest {
        assertFailsWith<IllegalArgumentException> { fiche(levothyrox().copy(nom = "  ")) }
        assertTrue(medicaments.tous().isEmpty())
    }

    @Test
    fun `une boite sans unite est refusee`() = runTest {
        assertFailsWith<IllegalArgumentException> { fiche(levothyrox().copy(unitesParBoite = 0)) }
        assertTrue(medicaments.tous().isEmpty())
    }

    @Test
    fun `un stock negatif est refuse`() = runTest {
        assertFailsWith<IllegalArgumentException> { fiche(levothyrox().copy(stockUnites = -1.0)) }
        assertTrue(medicaments.tous().isEmpty())
    }
}
