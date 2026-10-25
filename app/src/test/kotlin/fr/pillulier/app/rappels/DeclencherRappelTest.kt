package fr.pillulier.app.rappels

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
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.app.usecase.EnregistrerPrise
import fr.pillulier.app.usecase.HorlogeFigee
import fr.pillulier.app.usecase.ProgrammateurEspion
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
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DeclencherRappelTest {

    private lateinit var base: PillulierDatabase
    private lateinit var medicaments: DepotMedicaments
    private lateinit var ordonnances: DepotOrdonnances
    private lateinit var moments: DepotMoments
    private lateinit var gestionnaire: NotificationManager
    private lateinit var declencher: DeclencherRappel
    private lateinit var enregistrer: EnregistrerPrise
    private val programmateur = ProgrammateurEspion()
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 5, 7, 30))

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
        gestionnaire = contexte.getSystemService(NotificationManager::class.java)
        val notifications = Notifications(contexte).apply { creerCanaux() }

        enregistrer = EnregistrerPrise(base, base.evenements(), base.medicaments(), horloge)
        declencher = DeclencherRappel(
            medicaments = medicaments,
            ordonnances = ordonnances,
            moments = moments,
            evenements = DepotEvenements(base.evenements()),
            preferences = DepotPreferences(contexte),
            notifications = notifications,
            programmateur = programmateur,
            horloge = horloge,
            rafraichirWidget = RafraichirWidget(contexte),
        )
    }

    @After
    fun fermer() = base.close()

    private suspend fun medicamentDuMatin(nom: String = "Levothyrox"): Long {
        val id = medicaments.enregistrer(
            Medicament(
                id = 0,
                nom = nom,
                dosage = "75 µg",
                forme = Forme.COMPRIME,
                unitesParBoite = 30,
                stockUnites = 30.0,
                seuilAlerteJours = 7,
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

    private fun canalPoste(cle: CleRappel): String? =
        shadowOf(gestionnaire).activeNotifications.firstOrNull { it.id == cle.codeRequete() }?.notification?.channelId

    @Test
    fun `un preavis poste en silence et programme la sonnerie a l heure de la prise`() = runTest {
        val cle = CleRappel(medicamentDuMatin(), LocalDate.of(2026, 1, 5), Moment.MATIN)

        declencher(cle, critique = false, phase = Phase.PREAVIS)

        assertEquals(CANAL_A_VENIR, canalPoste(cle))
        assertEquals(
            listOf(ProgrammateurEspion.Programmation(cle, LocalDateTime.of(2026, 1, 5, 8, 0), false, Phase.SONNERIE)),
            programmateur.programmees,
        )
    }

    @Test
    fun `une sonnerie poste le rappel sonore et programme la relance`() = runTest {
        horloge.avancerA(LocalDateTime.of(2026, 1, 5, 8, 0))
        val cle = CleRappel(medicamentDuMatin(), LocalDate.of(2026, 1, 5), Moment.MATIN)

        declencher(cle, critique = false, phase = Phase.SONNERIE)

        assertEquals(CANAL_RAPPELS, canalPoste(cle))
        assertEquals(
            listOf(ProgrammateurEspion.Programmation(cle, LocalDateTime.of(2026, 1, 5, 8, 15), false, Phase.RELANCE)),
            programmateur.programmees,
        )
    }

    @Test
    fun `une relance poste le rappel sonore et programme la relance suivante`() = runTest {
        horloge.avancerA(LocalDateTime.of(2026, 1, 5, 8, 15))
        val cle = CleRappel(medicamentDuMatin(), LocalDate.of(2026, 1, 5), Moment.MATIN)

        declencher(cle, critique = false, phase = Phase.RELANCE)

        assertEquals(CANAL_RAPPELS, canalPoste(cle))
        assertEquals(
            listOf(ProgrammateurEspion.Programmation(cle, LocalDateTime.of(2026, 1, 5, 8, 30), false, Phase.RELANCE)),
            programmateur.programmees,
        )
    }

    @Test
    fun `un preavis arrive en retard sonne directement`() = runTest {
        horloge.avancerA(LocalDateTime.of(2026, 1, 5, 8, 5))
        val cle = CleRappel(medicamentDuMatin(), LocalDate.of(2026, 1, 5), Moment.MATIN)

        declencher(cle, critique = false, phase = Phase.PREAVIS)

        assertEquals(CANAL_RAPPELS, canalPoste(cle))
        assertEquals(LocalDateTime.of(2026, 1, 5, 8, 20), programmateur.programmees.single().quand)
    }

    @Test
    fun `le preavis d une prise juste apres minuit tombe la veille et reste poste`() = runTest {
        moments.definir(Moment.MATIN, LocalTime.of(0, 10))
        horloge.avancerA(LocalDateTime.of(2026, 1, 4, 23, 40))
        val cle = CleRappel(medicamentDuMatin(), LocalDate.of(2026, 1, 5), Moment.MATIN)

        declencher(cle, critique = false, phase = Phase.PREAVIS)

        assertEquals(CANAL_A_VENIR, canalPoste(cle))
        assertEquals(LocalDateTime.of(2026, 1, 5, 0, 10), programmateur.programmees.single().quand)
    }

    @Test
    fun `une sonnerie d hier ne poste rien et ne relance pas`() = runTest {
        horloge.avancerA(LocalDateTime.of(2026, 1, 6, 0, 30))
        val cle = CleRappel(medicamentDuMatin(), LocalDate.of(2026, 1, 5), Moment.MATIN)

        declencher(cle, critique = false, phase = Phase.SONNERIE)

        assertEquals(null, canalPoste(cle))
        assertTrue(programmateur.programmees.isEmpty())
    }

    // Une alarme déjà partie ne se rattrape pas : `annuler` n'a aucune prise sur
    // un déclenchement en vol. Cochée juste avant qu'il arrive, la prise ne doit
    // ni revenir dans le volet, ni réarmer une relance qui se perpétuerait.
    @Test
    fun `une sonnerie en vol ne relance pas la prise cochee entre temps`() = runTest {
        horloge.avancerA(LocalDateTime.of(2026, 1, 5, 8, 0))
        val cle = CleRappel(medicamentDuMatin(), LocalDate.of(2026, 1, 5), Moment.MATIN)
        enregistrer(cle.medicamentId, cle.date, cle.moment, dose = 1.0)

        declencher(cle, critique = false, phase = Phase.SONNERIE)

        assertEquals(null, canalPoste(cle))
        assertTrue(programmateur.programmees.isEmpty())
    }

    @Test
    fun `un preavis en vol n arme pas la sonnerie d une prise cochee d avance`() = runTest {
        val cle = CleRappel(medicamentDuMatin(), LocalDate.of(2026, 1, 5), Moment.MATIN)
        enregistrer(cle.medicamentId, cle.date, cle.moment, dose = 1.0)

        declencher(cle, critique = false, phase = Phase.PREAVIS)

        assertEquals(null, canalPoste(cle))
        assertTrue(programmateur.programmees.isEmpty())
    }

    @Test
    fun `une prise cochee ne fait taire ni l autre medicament ni l autre moment`() = runTest {
        horloge.avancerA(LocalDateTime.of(2026, 1, 5, 8, 0))
        val coche = medicamentDuMatin("Levothyrox")
        val autre = CleRappel(medicamentDuMatin("Kardegic"), LocalDate.of(2026, 1, 5), Moment.MATIN)
        enregistrer(coche, autre.date, Moment.MATIN, dose = 1.0)
        enregistrer(autre.medicamentId, autre.date, Moment.SOIR, dose = 1.0)

        declencher(autre, critique = false, phase = Phase.SONNERIE)

        assertEquals(CANAL_RAPPELS, canalPoste(autre))
    }
}
