package fr.pillulier.app.usecase

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.DepotEvenements
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotMoments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.app.rappels.Notifications
import fr.pillulier.app.widget.RafraichirWidget
import fr.pillulier.domain.CleRappel
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Ordonnance
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.TypeOrdonnance
import fr.pillulier.domain.codeRequete
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Le travail de minuit passe par deux chemins — le travailleur periodique et le
 * recepteur d'alarme — qui doivent produire la meme chose. Les assertions
 * portent sur un effet propre a chaque etape : seule la cloture retire les
 * notifications de la veille, seul le rearmement programme le jour courant.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ClotureQuotidienneTest {

    private lateinit var base: PillulierDatabase
    private lateinit var contexte: Context
    private lateinit var notifications: Notifications
    private lateinit var cloture: ClotureQuotidienne
    private val programmateur = ProgrammateurEspion()
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 6, 0, 5))

    @Before
    fun preparer() {
        contexte = ApplicationProvider.getApplicationContext()
        base = Room.inMemoryDatabaseBuilder(contexte, PillulierDatabase::class.java)
            .addCallback(momentsParDefaut)
            .allowMainThreadQueries()
            .build()
        notifications = Notifications(contexte)
        notifications.creerCanaux()

        val medicaments = DepotMedicaments(base.medicaments())
        val moments = DepotMoments(base.moments())
        cloture = ClotureQuotidienne(
            cloturerJournee = CloturerJournee(medicaments, programmateur, notifications, horloge),
            reArmerRappels = ReArmerRappels(
                ordonnances = DepotOrdonnances(base.ordonnances(), base),
                medicaments = medicaments,
                moments = moments,
                evenements = DepotEvenements(base.evenements()),
                programmateur = programmateur,
                horloge = horloge,
            ),
            rafraichirWidget = RafraichirWidget(contexte),
        )
    }

    @After
    fun fermer() = base.close()

    private suspend fun medicamentDuMatin(): Long {
        val id = DepotMedicaments(base.medicaments()).enregistrer(
            Medicament(
                id = 0,
                nom = "Levothyrox",
                dosage = "75 µg",
                forme = Forme.COMPRIME,
                unitesParBoite = 30,
                stockUnites = 30.0,
                seuilAlerteJours = 7,
                seuilAlerteUnites = null,
                critique = false,
            ),
        )
        DepotOrdonnances(base.ordonnances(), base).enregistrerVersion(
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
    fun `le travail de minuit retire les notifications de la veille`() = runTest {
        val id = medicamentDuMatin()
        val cle = CleRappel(id, LocalDate.of(2026, 1, 5), Moment.MATIN)
        notifications.posterRappel(
            cle,
            DepotMedicaments(base.medicaments()).parId(id)!!,
            dose = 1.0,
            critique = false,
        )

        cloture()

        val gestionnaire = contexte.getSystemService(NotificationManager::class.java)
        assertEquals(
            0,
            shadowOf(gestionnaire).activeNotifications.count { it.id == cle.codeRequete() },
        )
    }

    @Test
    fun `le travail de minuit rearme les alarmes du jour courant`() = runTest {
        val id = medicamentDuMatin()

        cloture()

        assertTrue(
            programmateur.programmees.any {
                it.cle == CleRappel(id, LocalDate.of(2026, 1, 6), Moment.MATIN)
            },
            "la prise du matin du jour courant doit etre reprogrammee",
        )
    }
}
