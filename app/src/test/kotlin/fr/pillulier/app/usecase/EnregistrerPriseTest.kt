package fr.pillulier.app.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.db.MedicamentEntity
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.app.temps.Horloge
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Moment
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HorlogeFigee(private var valeur: LocalDateTime) : Horloge {
    override fun maintenant(): LocalDateTime = valeur
    override fun aujourdhui(): LocalDate = valeur.toLocalDate()
    override fun instant(): Instant = valeur.toInstant(java.time.ZoneOffset.UTC)
    fun avancerA(nouvelle: LocalDateTime) { valeur = nouvelle }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EnregistrerPriseTest {

    private lateinit var base: PillulierDatabase
    private lateinit var enregistrer: EnregistrerPrise
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 5, 8, 5))

    @Before
    fun ouvrir() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        ).addCallback(momentsParDefaut).allowMainThreadQueries().build()
        enregistrer = EnregistrerPrise(base, base.evenements(), base.medicaments(), horloge)
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
    fun `enregistrer une prise decremente le stock de la dose`() = runTest {
        val id = medicament(stock = 10.0)

        val enregistree = enregistrer(id, LocalDate.of(2026, 1, 5), Moment.MATIN, dose = 0.5)

        assertTrue(enregistree)
        assertEquals(9.5, base.medicaments().parId(id)!!.stockUnites)
        assertEquals(1, base.evenements().duJour(LocalDate.of(2026, 1, 5)).size)
    }

    @Test
    fun `enregistrer deux fois la meme prise ne decremente qu une fois`() = runTest {
        val id = medicament(stock = 10.0)

        assertTrue(enregistrer(id, LocalDate.of(2026, 1, 5), Moment.MATIN, dose = 1.0))
        assertFalse(enregistrer(id, LocalDate.of(2026, 1, 5), Moment.MATIN, dose = 1.0))

        assertEquals(9.0, base.medicaments().parId(id)!!.stockUnites)
    }

    @Test
    fun `une prise a la demande peut etre enregistree plusieurs fois par jour`() = runTest {
        val id = medicament(stock = 10.0)

        assertTrue(enregistrer(id, LocalDate.of(2026, 1, 5), moment = null, dose = 1.0))
        assertTrue(enregistrer(id, LocalDate.of(2026, 1, 5), moment = null, dose = 1.0))

        assertEquals(8.0, base.medicaments().parId(id)!!.stockUnites)
    }

    @Test
    fun `l instant d enregistrement vient de l horloge`() = runTest {
        val id = medicament()

        enregistrer(id, LocalDate.of(2026, 1, 5), Moment.MATIN, dose = 1.0)

        assertEquals(
            horloge.instant(),
            base.evenements().duJour(LocalDate.of(2026, 1, 5)).single().enregistreLe,
        )
    }

    @Test
    fun `le stock peut devenir negatif si on prend sans avoir mis a jour la boite`() = runTest {
        val id = medicament(stock = 0.0)

        assertTrue(enregistrer(id, LocalDate.of(2026, 1, 5), Moment.MATIN, dose = 1.0))

        assertEquals(-1.0, base.medicaments().parId(id)!!.stockUnites)
    }
}
