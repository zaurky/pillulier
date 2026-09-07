package fr.pillulier.app.rappels

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotPreferences
import fr.pillulier.app.data.db.MedicamentEntity
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.app.usecase.EnregistrerPrise
import fr.pillulier.app.usecase.HorlogeFigee
import fr.pillulier.app.usecase.ProgrammateurEspion
import fr.pillulier.domain.CleRappel
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Moment
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

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AlarmeViewModelTest {

    private lateinit var base: PillulierDatabase
    private lateinit var vue: AlarmeViewModel
    private val programmateur = ProgrammateurEspion()
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 5, 8, 0))
    private val cle = CleRappel(1, LocalDate.of(2026, 1, 5), Moment.MATIN)

    @Before
    fun preparer() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        ).addCallback(momentsParDefaut).allowMainThreadQueries().build()

        vue = AlarmeViewModel(
            medicaments = DepotMedicaments(base.medicaments()),
            enregistrerPrise = EnregistrerPrise(base, base.evenements(), base.medicaments(), horloge),
            preferences = DepotPreferences(ApplicationProvider.getApplicationContext()),
            programmateur = programmateur,
            notifications = Notifications(ApplicationProvider.getApplicationContext()),
            horloge = horloge,
        )
    }

    @After
    fun fermer() = base.close()

    private suspend fun insererInsuline() {
        base.medicaments().inserer(
            MedicamentEntity(
                id = 1,
                nom = "Insuline",
                dosage = "10 UI",
                forme = Forme.INJECTION,
                unitesParBoite = 5,
                stockUnites = 5.0,
                seuilAlerteJours = 7,
                seuilAlerteUnites = null,
                critique = true,
            ),
        )
    }

    @Test
    fun `charger expose le nom et la dose lisible`() = runTest {
        insererInsuline()

        vue.charger(cle, dose = 1.0)

        val etat = vue.etat.value
        assertEquals("Insuline", etat.nom)
        assertEquals("10 UI", etat.dosage)
        assertEquals("1 injection", etat.libelleDose)
        assertEquals("Matin", etat.momentLisible)
    }

    @Test
    fun `valider enregistre la prise annule l alarme et termine`() = runTest {
        insererInsuline()
        vue.charger(cle, dose = 1.0)

        vue.valider()

        assertEquals(1, base.evenements().duJour(LocalDate.of(2026, 1, 5)).size)
        assertEquals(4.0, base.medicaments().parId(1)!!.stockUnites)
        assertTrue(cle in programmateur.annulees)
        assertTrue(vue.etat.value.termine)
    }

    @Test
    fun `reporter reprogramme l alarme sans enregistrer de prise`() = runTest {
        insererInsuline()
        vue.charger(cle, dose = 1.0)

        vue.reporter()

        assertEquals(0, base.evenements().duJour(LocalDate.of(2026, 1, 5)).size)
        assertEquals(
            LocalDateTime.of(2026, 1, 5, 8, 15),
            programmateur.programmees.single().quand,
        )
        assertTrue(vue.etat.value.termine)
    }
}
