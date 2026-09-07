package fr.pillulier.app.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.db.MedicamentEntity
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.domain.Forme
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StockTest {

    private lateinit var base: PillulierDatabase
    private lateinit var ajouterBoite: AjouterBoite
    private lateinit var corrigerStock: CorrigerStock

    @Before
    fun ouvrir() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        ).addCallback(momentsParDefaut).allowMainThreadQueries().build()
        ajouterBoite = AjouterBoite(base.medicaments())
        corrigerStock = CorrigerStock(base.medicaments())
    }

    @After
    fun fermer() = base.close()

    private suspend fun medicament(stock: Double, unitesParBoite: Int): Long =
        base.medicaments().inserer(
            MedicamentEntity(
                nom = "Doliprane",
                dosage = "500 mg",
                forme = Forme.COMPRIME,
                unitesParBoite = unitesParBoite,
                stockUnites = stock,
                seuilAlerteJours = null,
                seuilAlerteUnites = 5,
                critique = false,
            ),
        )

    @Test
    fun `ajouter une boite ajoute ses unites au stock`() = runTest {
        val id = medicament(stock = 4.0, unitesParBoite = 16)

        ajouterBoite(id)

        assertEquals(20.0, base.medicaments().parId(id)!!.stockUnites)
    }

    @Test
    fun `corriger le stock ecrase la valeur`() = runTest {
        val id = medicament(stock = 4.0, unitesParBoite = 16)

        corrigerStock(id, 12.5)

        assertEquals(12.5, base.medicaments().parId(id)!!.stockUnites)
    }

    @Test
    fun `corriger avec une valeur negative est refuse`() = runTest {
        val id = medicament(stock = 4.0, unitesParBoite = 16)

        assertFailsWith<IllegalArgumentException> { corrigerStock(id, -1.0) }
    }

    @Test
    fun `ajouter une boite a un medicament inconnu est refuse`() = runTest {
        assertFailsWith<IllegalStateException> { ajouterBoite(404) }
    }
}
