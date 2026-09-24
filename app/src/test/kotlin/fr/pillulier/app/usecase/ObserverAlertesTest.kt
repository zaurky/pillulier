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
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ObserverAlertesTest {

    private lateinit var base: PillulierDatabase
    private lateinit var medicaments: DepotMedicaments
    private lateinit var ordonnances: DepotOrdonnances
    private lateinit var observer: ObserverAlertes
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 5, 9, 0))

    @Before
    fun preparer() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        ).addCallback(momentsParDefaut).allowMainThreadQueries().build()

        medicaments = DepotMedicaments(base.medicaments())
        ordonnances = DepotOrdonnances(base.ordonnances(), base)
        observer = ObserverAlertes(
            ObserverStock(
                medicaments = medicaments,
                ordonnances = ordonnances,
                preferences = DepotPreferences(ApplicationProvider.getApplicationContext()),
                horloge = horloge,
            ),
        )
    }

    @After
    fun fermer() = base.close()

    private suspend fun avecStock(nom: String, stock: Double, seuilJours: Int?): Long {
        val id = medicaments.enregistrer(
            Medicament(
                id = 0,
                nom = nom,
                dosage = "1 mg",
                forme = Forme.COMPRIME,
                unitesParBoite = 30,
                stockUnites = stock,
                seuilAlerteJours = seuilJours,
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
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
            dateEffet = LocalDate.of(2026, 1, 1),
        )
        return id
    }

    @Test
    fun `un stock large ne declenche aucune alerte`() = runTest {
        avecStock("Levothyrox", stock = 90.0, seuilJours = 7)

        assertEquals(emptyList(), observer().first())
    }

    @Test
    fun `un stock proche de l epuisement declenche une alerte`() = runTest {
        // 5 comprimes, 1 par jour : epuisement le 10 janvier, seuil 7 jours,
        // donc la date d'alerte est deja passee.
        avecStock("Levothyrox", stock = 5.0, seuilJours = 7)

        val alertes = observer().first()

        assertEquals(1, alertes.size)
        assertEquals("Levothyrox", alertes.single().nom)
        assertTrue(alertes.single().message.isNotBlank())
    }

    @Test
    fun `un medicament a la demande alerte sur son seuil en unites`() = runTest {
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
        ordonnances.enregistrerVersion(
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
            dateEffet = LocalDate.of(2026, 1, 1),
        )

        val alertes = observer().first()

        assertEquals(listOf("Doliprane"), alertes.map { it.nom })
    }

    @Test
    fun `un medicament a la demande au dessus de son seuil n alerte pas`() = runTest {
        val id = medicaments.enregistrer(
            Medicament(
                id = 0,
                nom = "Doliprane",
                dosage = "500 mg",
                forme = Forme.COMPRIME,
                unitesParBoite = 16,
                stockUnites = 12.0,
                seuilAlerteJours = null,
                seuilAlerteUnites = 5,
                critique = false,
            ),
        )
        ordonnances.enregistrerVersion(
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
            dateEffet = LocalDate.of(2026, 1, 1),
        )

        assertEquals(emptyList(), observer().first())
    }
}
