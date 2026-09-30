package fr.pillulier.app.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.DepotEvenements
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotMoments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.domain.CleRappel
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Forme
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Ordonnance
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.TypeOrdonnance
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Le recepteur de rappel ne peut pas faire confiance a l alarme qui le reveille :
 * une alarme deja partie ne se rattrape pas. Coche depuis le widget, la prise
 * est enregistree et les alarmes reprogrammees — mais un declenchement en vol
 * arrive quand meme, et sans cette relecture du journal il reposte la
 * notification du medicament deja pris, puis rearme sa relance pour toujours.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RappelEncoreDuTest {

    private lateinit var base: PillulierDatabase
    private lateinit var medicaments: DepotMedicaments
    private lateinit var ordonnances: DepotOrdonnances
    private lateinit var enregistrer: EnregistrerPrise
    private lateinit var reArmer: ReArmerRappels
    private lateinit var encoreDu: RappelEncoreDu

    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 5, 8, 10))
    private val jour = LocalDate.of(2026, 1, 5)

    @Before
    fun ouvrir() {
        base = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PillulierDatabase::class.java,
        ).addCallback(momentsParDefaut).allowMainThreadQueries().build()
        medicaments = DepotMedicaments(base.medicaments())
        ordonnances = DepotOrdonnances(base.ordonnances(), base)
        enregistrer = EnregistrerPrise(base, base.evenements(), base.medicaments(), horloge)
        reArmer = ReArmerRappels(
            ordonnances = ordonnances,
            medicaments = medicaments,
            moments = DepotMoments(base.moments()),
            evenements = DepotEvenements(base.evenements()),
            programmateur = ProgrammateurEspion(),
            horloge = horloge,
        )
        encoreDu = RappelEncoreDu(DepotEvenements(base.evenements()), horloge)
    }

    @After
    fun fermer() = base.close()

    private suspend fun insererDuMatin(nom: String): Long {
        val id = medicaments.enregistrer(
            Medicament(
                id = 0,
                nom = nom,
                dosage = "75 ug",
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

    @Test
    fun `une alarme en vol ne relance pas la prise cochee entre temps`() = runTest {
        val premier = insererDuMatin("Levothyrox")
        val second = insererDuMatin("Kardegic")

        // Le clic du widget : on enregistre puis on reprogramme la fenetre.
        enregistrer(premier, jour, Moment.MATIN, dose = 1.0)
        reArmer()

        assertFalse(
            encoreDu(CleRappel(premier, jour, Moment.MATIN)),
            "le medicament deja coche ne doit plus donner lieu a une notification",
        )
        assertTrue(
            encoreDu(CleRappel(second, jour, Moment.MATIN)),
            "le medicament encore en attente doit garder son rappel",
        )
    }

    @Test
    fun `un rappel de la veille ne se declenche jamais`() = runTest {
        val id = insererDuMatin("Levothyrox")

        assertFalse(
            encoreDu(CleRappel(id, jour.minusDays(1), Moment.MATIN)),
            "aucune relance ne survit au lendemain",
        )
    }

    @Test
    fun `une prise d un autre moment ne fait pas taire le rappel du matin`() = runTest {
        val id = insererDuMatin("Levothyrox")
        enregistrer(id, jour, Moment.SOIR, dose = 1.0)

        assertTrue(
            encoreDu(CleRappel(id, jour, Moment.MATIN)),
            "le journal du soir ne dit rien du matin",
        )
    }
}
