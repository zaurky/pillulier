package fr.pillulier.app.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.db.MedicamentEntity
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AnnulerPriseTest {

    private lateinit var base: PillulierDatabase
    private lateinit var enregistrer: EnregistrerPrise
    private lateinit var annuler: AnnulerPrise
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 5, 8, 5))
    private val jour = LocalDate.of(2026, 1, 5)

    @Before
    fun ouvrir() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        ).addCallback(momentsParDefaut).allowMainThreadQueries().build()
        enregistrer = EnregistrerPrise(base, base.evenements(), base.medicaments(), horloge)
        annuler = AnnulerPrise(base, base.evenements(), base.medicaments())
    }

    @After
    fun fermer() = base.close()

    private suspend fun medicament(stock: Double = 10.0): Long = base.medicaments().inserer(
        MedicamentEntity(
            nom = "Levothyrox",
            dosage = "75 µg",
            forme = Forme.COMPRIME,
            unitesParBoite = 30,
            stockUnites = stock,
            seuilAlerteJours = 7,
            seuilAlerteUnites = null,
            critique = true,
        ),
    )

    @Test
    fun `annuler supprime l evenement et rend le stock`() = runTest {
        val id = medicament(stock = 10.0)
        enregistrer(id, jour, Moment.MATIN, dose = 1.0)

        assertTrue(annuler(id, jour, Moment.MATIN))

        assertEquals(10.0, base.medicaments().parId(id)!!.stockUnites)
        assertTrue(base.evenements().duJour(jour).isEmpty())
    }

    @Test
    fun `annuler rend la dose reellement enregistree et non la dose theorique`() = runTest {
        val id = medicament(stock = 10.0)
        enregistrer(id, jour, Moment.MATIN, dose = 0.5)

        assertTrue(annuler(id, jour, Moment.MATIN))

        assertEquals(10.0, base.medicaments().parId(id)!!.stockUnites)
    }

    @Test
    fun `annuler une prise inexistante ne fait rien`() = runTest {
        val id = medicament(stock = 10.0)

        assertFalse(annuler(id, jour, Moment.MATIN))

        assertEquals(10.0, base.medicaments().parId(id)!!.stockUnites)
    }

    @Test
    fun `annuler deux fois ne recredite qu une fois`() = runTest {
        val id = medicament(stock = 10.0)
        enregistrer(id, jour, Moment.MATIN, dose = 1.0)

        assertTrue(annuler(id, jour, Moment.MATIN))
        assertFalse(annuler(id, jour, Moment.MATIN))

        assertEquals(10.0, base.medicaments().parId(id)!!.stockUnites)
    }

    @Test
    fun `annuler un moment ne touche pas les autres moments du jour`() = runTest {
        val id = medicament(stock = 10.0)
        enregistrer(id, jour, Moment.MATIN, dose = 1.0)
        enregistrer(id, jour, Moment.SOIR, dose = 1.0)

        assertTrue(annuler(id, jour, Moment.MATIN))

        assertEquals(9.0, base.medicaments().parId(id)!!.stockUnites)
        assertEquals(Moment.SOIR, base.evenements().duJour(jour).single().moment)
    }
}
