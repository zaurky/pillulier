package fr.pillulier.app.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotMoments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.app.rappels.Notifications
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
import android.app.NotificationManager
import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CloturerJourneeTest {

    private lateinit var base: PillulierDatabase
    private lateinit var contexte: Context
    private lateinit var notifications: Notifications
    private lateinit var cloturer: CloturerJournee
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
        cloturer = CloturerJournee(
            medicaments = DepotMedicaments(base.medicaments()),
            programmateur = programmateur,
            notifications = notifications,
            horloge = horloge,
        )
    }

    @After
    fun fermer() = base.close()

    private suspend fun medicamentDuMatin(): Long {
        val depot = DepotMedicaments(base.medicaments())
        val id = depot.enregistrer(
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
        DepotOrdonnances(base.ordonnances()).enregistrer(
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
    fun `la cloture annule les alarmes de la veille`() = runTest {
        val id = medicamentDuMatin()

        cloturer()

        assertTrue(CleRappel(id, LocalDate.of(2026, 1, 5), Moment.MATIN) in programmateur.annulees)
        assertEquals(4, programmateur.annulees.size, "les quatre moments de la veille")
    }

    @Test
    fun `la cloture retire les notifications de la veille`() = runTest {
        val id = medicamentDuMatin()
        val cle = CleRappel(id, LocalDate.of(2026, 1, 5), Moment.MATIN)
        notifications.posterRappel(
            cle,
            DepotMedicaments(base.medicaments()).parId(id)!!,
            dose = 1.0,
            critique = false,
        )

        cloturer()

        val gestionnaire = contexte.getSystemService(NotificationManager::class.java)
        assertEquals(
            0,
            shadowOf(gestionnaire).activeNotifications.count { it.id == cle.codeRequete() },
        )
    }

    @Test
    fun `la cloture n ecrit rien en base`() = runTest {
        medicamentDuMatin()

        cloturer()

        assertTrue(base.evenements().duJour(LocalDate.of(2026, 1, 5)).isEmpty())
    }

    @Test
    fun `la cloture retire la notification d une dose retiree de l ordonnance`() = runTest {
        val id = medicamentDuMatin()
        val cleSoir = CleRappel(id, LocalDate.of(2026, 1, 5), Moment.SOIR)
        notifications.posterRappel(
            cleSoir,
            DepotMedicaments(base.medicaments()).parId(id)!!,
            dose = 1.0,
            critique = false,
        )

        cloturer()

        val gestionnaire = contexte.getSystemService(NotificationManager::class.java)
        assertEquals(
            0,
            shadowOf(gestionnaire).activeNotifications.count { it.id == cleSoir.codeRequete() },
            "le planning courant ne prevoit pas de dose du soir, la notification doit partir quand meme",
        )
    }
}
