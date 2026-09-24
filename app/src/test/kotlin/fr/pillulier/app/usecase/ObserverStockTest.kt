package fr.pillulier.app.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.DepotPreferences
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Ordonnance
import fr.pillulier.domain.Rythme
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ObserverStockTest {

    private lateinit var base: PillulierDatabase
    private lateinit var medicaments: DepotMedicaments
    private lateinit var ordonnances: DepotOrdonnances
    private lateinit var observer: ObserverStock
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 5, 9, 0))

    @Before
    fun preparer() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        ).addCallback(momentsParDefaut).allowMainThreadQueries().build()

        medicaments = DepotMedicaments(base.medicaments())
        ordonnances = DepotOrdonnances(base.ordonnances())
        observer = ObserverStock(
            medicaments = medicaments,
            ordonnances = ordonnances,
            preferences = DepotPreferences(ApplicationProvider.getApplicationContext()),
            horloge = horloge,
        )
    }

    @After
    fun fermer() = base.close()

    private suspend fun planifie(nom: String, stock: Double): Long {
        val id = medicaments.enregistrer(
            Medicament(
                id = 0,
                nom = nom,
                dosage = "75 µg",
                forme = Forme.COMPRIME,
                unitesParBoite = 30,
                stockUnites = stock,
                seuilAlerteJours = 7,
                seuilAlerteUnites = null,
                critique = false,
            ),
        )
        ordonnances.enregistrer(
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
        )
        return id
    }

    @Test
    fun `la ligne porte la date d epuisement et les jours restants`() = runTest {
        planifie("Levothyrox", stock = 10.0)

        val ligne = observer().first().single()

        assertEquals(LocalDate.of(2026, 1, 15), ligne.dateEpuisement)
        assertEquals(10L, ligne.joursRestants)
        assertFalse(ligne.aLaDemande)
    }

    @Test
    fun `la date d alerte applique la marge du dimanche`() = runTest {
        planifie("Levothyrox", stock = 10.0)

        // Epuisement le 15 janvier, seuil 7 jours : candidat le 8, un dimanche
        // tombe dans la fenetre le 11, donc l'alerte recule au 7.
        assertEquals(LocalDate.of(2026, 1, 7), observer().first().single().dateAlerte)
    }

    @Test
    fun `une ligne devient en alerte quand la date est atteinte`() = runTest {
        planifie("Levothyrox", stock = 3.0)

        assertTrue(observer().first().single().enAlerte)
    }

    @Test
    fun `un stock confortable n est pas en alerte`() = runTest {
        planifie("Levothyrox", stock = 90.0)

        assertFalse(observer().first().single().enAlerte)
    }

    @Test
    fun `un medicament a la demande n a ni date ni jours restants`() = runTest {
        val id = medicaments.enregistrer(
            Medicament(
                id = 0,
                nom = "Doliprane",
                dosage = "500 mg",
                forme = Forme.COMPRIME,
                unitesParBoite = 16,
                stockUnites = 4.0,
                seuilAlerteJours = null,
                seuilAlerteUnites = 5,
                critique = false,
            ),
        )
        ordonnances.enregistrer(
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
        )

        val ligne = observer().first().single()

        assertTrue(ligne.aLaDemande)
        assertNull(ligne.dateEpuisement)
        assertNull(ligne.joursRestants)
        assertTrue(ligne.enAlerte, "4 unites sous un seuil de 5")
    }

    @Test
    fun `les unites restantes sont mises en forme`() = runTest {
        planifie("Levothyrox", stock = 9.5)

        assertEquals("9½ comprimés", observer().first().single().libelleUnites)
    }
}
