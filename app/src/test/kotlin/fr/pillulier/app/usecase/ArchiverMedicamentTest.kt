package fr.pillulier.app.usecase

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
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.TypeOrdonnance
import fr.pillulier.domain.prisesAttendues
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
class ArchiverMedicamentTest {

    private lateinit var base: PillulierDatabase
    private lateinit var medicaments: DepotMedicaments
    private lateinit var ordonnances: DepotOrdonnances
    private lateinit var moments: DepotMoments
    private lateinit var enregistrerMedicament: EnregistrerMedicament
    private lateinit var enregistrerPrise: EnregistrerPrise
    private lateinit var archiverMedicament: ArchiverMedicament
    private val programmateur = ProgrammateurEspion()
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 6, 9, 0))

    @Before
    fun preparer() {
        val contexte = ApplicationProvider.getApplicationContext<Context>()
        base = Room.inMemoryDatabaseBuilder(contexte, PillulierDatabase::class.java)
            .addCallback(momentsParDefaut)
            .allowMainThreadQueries()
            .build()

        medicaments = DepotMedicaments(base.medicaments())
        ordonnances = DepotOrdonnances(base.ordonnances(), base)
        moments = DepotMoments(base.moments())
        val reArmer = ReArmerRappels(
            ordonnances = ordonnances,
            medicaments = medicaments,
            moments = moments,
            evenements = DepotEvenements(base.evenements()),
            programmateur = programmateur,
            horloge = horloge,
        )
        val rafraichirWidget = RafraichirWidget(contexte)
        enregistrerMedicament = EnregistrerMedicament(medicaments, ordonnances, reArmer, rafraichirWidget)
        enregistrerPrise = EnregistrerPrise(base, base.evenements(), base.medicaments(), horloge)
        archiverMedicament = ArchiverMedicament(
            medicaments = medicaments,
            ordonnances = ordonnances,
            programmateur = programmateur,
            notifications = Notifications(contexte),
            reArmerRappels = reArmer,
            horloge = horloge,
            rafraichirWidget = rafraichirWidget,
        )
    }

    @After
    fun fermer() = base.close()

    private suspend fun medicamentQuotidienAvecUnePrise(jour: LocalDate): Long {
        enregistrerMedicament(
            medicament = Medicament(
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
            type = TypeOrdonnance.PLANIFIEE,
            rythme = Rythme.TousLesJours,
            dateDebut = LocalDate.of(2026, 1, 1),
            dateFin = null,
            doses = listOf(DosePrescrite(Moment.MATIN, 1.0)),
            dateEffet = LocalDate.of(2026, 1, 1),
        )
        val id = medicaments.tous().single().id
        enregistrerPrise(id, jour, Moment.MATIN, dose = 1.0)
        return id
    }

    @Test
    fun `archiver conserve les prises passees`() = runTest {
        val id = medicamentQuotidienAvecUnePrise(LocalDate.of(2026, 1, 3))

        archiverMedicament(id)

        assertEquals(1, base.evenements().duJour(LocalDate.of(2026, 1, 3)).size)
    }

    @Test
    fun `archiver ferme le planning a partir de demain`() = runTest {
        val id = medicamentQuotidienAvecUnePrise(LocalDate.of(2026, 1, 3))

        archiverMedicament(id)

        suspend fun compte(jour: LocalDate) = prisesAttendues(
            jour,
            ordonnances.toutes(),
            moments.heures(),
        ).count { it.medicamentId == id }

        assertEquals(1, compte(horloge.aujourdhui()), "la journee en cours reste servie")
        assertEquals(0, compte(horloge.aujourdhui().plusDays(1)))
    }

    @Test
    fun `un medicament archive sort de la liste mais reste connu du moteur`() = runTest {
        val id = medicamentQuotidienAvecUnePrise(LocalDate.of(2026, 1, 3))

        archiverMedicament(id)

        assertTrue(medicaments.observerActifs().first().none { it.id == id })
        assertTrue(
            medicaments.tous().any { it.id == id },
            "ReArmerRappels batit son surensemble sur tous() : masquer l archive y laisserait une alarme vivante",
        )
    }
}
