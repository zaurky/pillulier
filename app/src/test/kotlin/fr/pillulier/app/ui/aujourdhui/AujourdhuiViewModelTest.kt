package fr.pillulier.app.ui.aujourdhui

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.DepotEvenements
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotMoments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.DepotPreferences
import fr.pillulier.app.data.db.MedicamentEntity
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.app.rappels.Notifications
import fr.pillulier.app.usecase.EnregistrerPrise
import fr.pillulier.app.usecase.HorlogeFigee
import fr.pillulier.app.usecase.LigneJournee
import fr.pillulier.app.usecase.ObserverAlertes
import fr.pillulier.app.usecase.ObserverJournee
import fr.pillulier.app.usecase.ObserverStock
import fr.pillulier.app.usecase.ProgrammateurEspion
import fr.pillulier.app.usecase.ReArmerRappels
import fr.pillulier.app.widget.RafraichirWidget
import fr.pillulier.domain.CleRappel
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.StatutPrise
import fr.pillulier.domain.codeRequete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AujourdhuiViewModelTest {

    private lateinit var contexte: Context
    private lateinit var base: PillulierDatabase
    private lateinit var notifications: Notifications
    private lateinit var gestionnaire: NotificationManager
    private lateinit var vue: AujourdhuiViewModel

    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 5, 8, 0))
    private val cle = CleRappel(1, LocalDate.of(2026, 1, 5), Moment.MATIN)

    @Before
    fun preparer() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        contexte = ApplicationProvider.getApplicationContext()
        base = Room.inMemoryDatabaseBuilder(contexte, PillulierDatabase::class.java)
            .addCallback(momentsParDefaut)
            .allowMainThreadQueries()
            .build()

        val medicaments = DepotMedicaments(base.medicaments())
        val ordonnances = DepotOrdonnances(base.ordonnances(), base)
        val moments = DepotMoments(base.moments())
        val evenements = DepotEvenements(base.evenements())
        val preferences = DepotPreferences(contexte)

        notifications = Notifications(contexte)
        notifications.creerCanaux()
        gestionnaire = contexte.getSystemService(NotificationManager::class.java)

        vue = AujourdhuiViewModel(
            observerJournee = ObserverJournee(medicaments, ordonnances, moments, evenements, horloge),
            observerAlertes = ObserverAlertes(
                ObserverStock(medicaments, ordonnances, preferences, horloge),
            ),
            medicaments = medicaments,
            ordonnances = ordonnances,
            enregistrerPrise = EnregistrerPrise(base, base.evenements(), base.medicaments(), horloge),
            reArmerRappels = ReArmerRappels(
                ordonnances = ordonnances,
                medicaments = medicaments,
                moments = moments,
                evenements = evenements,
                programmateur = ProgrammateurEspion(),
                horloge = horloge,
            ),
            notifications = notifications,
            horloge = horloge,
            rafraichirWidget = RafraichirWidget(contexte),
        )
    }

    @After
    fun fermer() {
        base.close()
        Dispatchers.resetMain()
    }

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

    private fun ligneInsuline() = LigneJournee(
        medicamentId = 1,
        nom = "Insuline",
        dosage = "10 UI",
        moment = Moment.MATIN,
        heure = LocalTime.of(8, 0),
        dose = 1.0,
        libelleDose = "1 injection",
        statut = StatutPrise.EN_RETARD,
    )

    private fun medicamentCritique() = Medicament(
        id = 1,
        nom = "Insuline",
        dosage = "10 UI",
        forme = Forme.INJECTION,
        unitesParBoite = 5,
        stockUnites = 5.0,
        seuilAlerteJours = 7,
        seuilAlerteUnites = null,
        critique = true,
    )

    @Test
    fun `cocher une prise retire la notification deja postee`() = runTest {
        insererInsuline()
        // Une prise critique est postée `setOngoing` : impossible à balayer, elle
        // resterait affichée jusqu'à la clôture si l'on ne la retirait pas ici.
        notifications.posterRappel(cle, medicamentCritique(), dose = 1.0, critique = true)
        assertTrue(shadowOf(gestionnaire).activeNotifications.any { it.id == cle.codeRequete() })

        vue.cocher(ligneInsuline()).join()

        assertEquals(
            0,
            shadowOf(gestionnaire).activeNotifications.count { it.id == cle.codeRequete() },
            "la notification de la prise cochee doit disparaitre",
        )
    }

    @Test
    fun `cocher une prise l enregistre au journal`() = runTest {
        insererInsuline()

        vue.cocher(ligneInsuline()).join()

        assertEquals(1, base.evenements().duJour(LocalDate.of(2026, 1, 5)).size)
    }
}
