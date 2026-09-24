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
import fr.pillulier.app.usecase.ArchiverMedicament
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
import fr.pillulier.domain.Ordonnance
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.StatutPrise
import fr.pillulier.domain.TypeOrdonnance
import fr.pillulier.domain.codeRequete
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
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
    private lateinit var medicaments: DepotMedicaments
    private lateinit var ordonnances: DepotOrdonnances
    private lateinit var archiver: ArchiverMedicament
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

        medicaments = DepotMedicaments(base.medicaments())
        ordonnances = DepotOrdonnances(base.ordonnances(), base)

        notifications = Notifications(contexte)
        notifications.creerCanaux()
        gestionnaire = contexte.getSystemService(NotificationManager::class.java)

        archiver = ArchiverMedicament(
            medicaments = medicaments,
            ordonnances = ordonnances,
            programmateur = ProgrammateurEspion(),
            notifications = notifications,
            reArmerRappels = reArmerRappels(),
            horloge = horloge,
            rafraichirWidget = RafraichirWidget(contexte),
        )

        creerVue()
    }

    private fun reArmerRappels() = ReArmerRappels(
        ordonnances = ordonnances,
        medicaments = medicaments,
        moments = DepotMoments(base.moments()),
        evenements = DepotEvenements(base.evenements()),
        programmateur = ProgrammateurEspion(),
        horloge = horloge,
    )

    /**
     * L ecran se construit sur la date du jour : rouvrir la vue est le seul
     * moyen de rejouer l ecran d un autre jour apres avoir avance l horloge.
     */
    private fun creerVue() {
        val moments = DepotMoments(base.moments())
        val evenements = DepotEvenements(base.evenements())
        vue = AujourdhuiViewModel(
            observerJournee = ObserverJournee(medicaments, ordonnances, moments, evenements, horloge),
            observerAlertes = ObserverAlertes(
                ObserverStock(medicaments, ordonnances, DepotPreferences(contexte), horloge),
            ),
            medicaments = medicaments,
            ordonnances = ordonnances,
            enregistrerPrise = EnregistrerPrise(base, base.evenements(), base.medicaments(), horloge),
            reArmerRappels = reArmerRappels(),
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

    private suspend fun insererALaDemande(nom: String): Long {
        val id = medicaments.enregistrer(
            Medicament(
                id = 0,
                nom = nom,
                dosage = "500 mg",
                forme = Forme.COMPRIME,
                unitesParBoite = 16,
                stockUnites = 16.0,
                seuilAlerteJours = null,
                seuilAlerteUnites = 4,
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
        return id
    }

    @Test
    fun `un medicament a la demande en cours est propose sur l ecran du jour`() = runTest {
        insererALaDemande("Doliprane")
        creerVue()

        assertEquals(
            listOf("Doliprane"),
            vue.etat.drop(1).first().aLaDemande.map { it.nom },
            "un traitement a la demande en cours doit etre propose",
        )
    }

    @Test
    fun `un medicament a la demande archive quitte l ecran du jour`() = runTest {
        val doliprane = insererALaDemande("Doliprane")
        // Un second traitement, lui bien vivant : sans lui l etat attendu
        // serait l etat initial du `StateFlow`, qui ne rejoue jamais, et le
        // test ne saurait pas distinguer « filtre » de « rien recu ».
        insererALaDemande("Spasfon")

        archiver(doliprane)
        // L archivage clot l ordonnance ce soir : c est le lendemain que
        // `enVigueur` perd la version couvrante et retombe sur la derniere
        // version connue, encore A_LA_DEMANDE. Sans filtre sur les actifs, le
        // medicament retire resterait propose indefiniment.
        horloge.avancerA(LocalDateTime.of(2026, 1, 6, 8, 0))
        creerVue()

        assertEquals(
            listOf("Spasfon"),
            vue.etat.drop(1).first().aLaDemande.map { it.nom },
            "un medicament archive ne doit plus etre propose a la demande",
        )
    }
}
