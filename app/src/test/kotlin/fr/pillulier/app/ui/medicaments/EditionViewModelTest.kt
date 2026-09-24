package fr.pillulier.app.ui.medicaments

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
import fr.pillulier.app.usecase.ArchiverMedicament
import fr.pillulier.app.usecase.EnregistrerMedicament
import fr.pillulier.app.usecase.HorlogeFigee
import fr.pillulier.app.usecase.ProgrammateurEspion
import fr.pillulier.app.usecase.ReArmerRappels
import fr.pillulier.app.widget.RafraichirWidget
import fr.pillulier.domain.Moment
import fr.pillulier.domain.Rythme
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
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EditionViewModelTest {

    private lateinit var contexte: Context
    private lateinit var base: PillulierDatabase
    private lateinit var medicaments: DepotMedicaments
    private lateinit var ordonnances: DepotOrdonnances
    private lateinit var vue: EditionViewModel
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 5, 7, 0))

    @Before
    fun preparer() {
        // `viewModelScope` lance sur le dispatcher Main, en pause sous Robolectric.
        Dispatchers.setMain(UnconfinedTestDispatcher())

        contexte = ApplicationProvider.getApplicationContext()
        base = Room.inMemoryDatabaseBuilder(contexte, PillulierDatabase::class.java)
            .addCallback(momentsParDefaut)
            .allowMainThreadQueries()
            .build()

        medicaments = DepotMedicaments(base.medicaments())
        ordonnances = DepotOrdonnances(base.ordonnances(), base)
        val reArmer = ReArmerRappels(
            ordonnances = ordonnances,
            medicaments = medicaments,
            moments = DepotMoments(base.moments()),
            evenements = DepotEvenements(base.evenements()),
            programmateur = ProgrammateurEspion(),
            horloge = horloge,
        )

        val rafraichirWidget = RafraichirWidget(contexte)
        vue = EditionViewModel(
            medicaments = medicaments,
            ordonnances = ordonnances,
            enregistrerMedicament = EnregistrerMedicament(medicaments, ordonnances, reArmer, rafraichirWidget),
            archiverMedicament = ArchiverMedicament(
                medicaments = medicaments,
                ordonnances = ordonnances,
                programmateur = ProgrammateurEspion(),
                notifications = Notifications(contexte),
                reArmerRappels = reArmer,
                horloge = horloge,
                rafraichirWidget = rafraichirWidget,
            ),
            horloge = horloge,
        )
    }

    @After
    fun fermer() {
        base.close()
        Dispatchers.resetMain()
    }

    private fun remplirFormulaireValide() {
        vue.modifierNom("Levothyrox")
        vue.modifierDosage("75 µg")
        vue.modifierUnitesParBoite("30")
        vue.definirDose(Moment.MATIN, 1.0)
    }

    @Test
    fun `un stock illisible affiche une erreur et n enregistre rien`() = runTest {
        remplirFormulaireValide()
        vue.modifierStock("3O")

        vue.enregistrer().join()

        assertNotNull(vue.etat.value.erreur, "une saisie illisible doit etre signalee")
        assertTrue(medicaments.tous().isEmpty(), "rien ne doit etre ecrit en base")
        assertEquals(false, vue.etat.value.enregistre)
    }

    @Test
    fun `un stock laisse vide vaut zero et enregistre`() = runTest {
        remplirFormulaireValide()
        vue.modifierStock("")

        vue.enregistrer().join()

        assertNull(vue.etat.value.erreur)
        assertEquals(0.0, medicaments.tous().single().stockUnites)
    }

    @Test
    fun `un stock a virgule est accepte`() = runTest {
        remplirFormulaireValide()
        vue.modifierStock("9,5")

        vue.enregistrer().join()

        assertNull(vue.etat.value.erreur)
        assertEquals(9.5, medicaments.tous().single().stockUnites)
    }

    @Test
    fun `un second chargement du meme medicament ne rejette pas la saisie en cours`() = runTest {
        remplirFormulaireValide()
        vue.enregistrer().join()
        val id = medicaments.tous().single().id

        // Premier passage : l'écran s'ouvre sur le médicament existant.
        vue.charger(id).join()
        assertEquals("Levothyrox", vue.etat.value.nom)

        // L'utilisateur tape, puis fait tourner le téléphone : le
        // `LaunchedEffect` de l'écran relance `charger` avec le même
        // identifiant, et ne doit plus rien écraser.
        vue.modifierNom("Levothyrox 100")
        vue.charger(id).join()

        assertEquals("Levothyrox 100", vue.etat.value.nom)
    }

    @Test
    fun `un intervalle de trois jours enregistre un jour sur trois`() = runTest {
        remplirFormulaireValide()
        vue.choisirUnJourSurN()
        vue.modifierIntervalle("3")

        vue.enregistrer().join()

        assertNull(vue.etat.value.erreur)
        val id = medicaments.tous().single().id
        assertEquals(Rythme.UnJourSurN(3), ordonnances.versionsDe(id).single().ordonnance.rythme)
    }

    @Test
    fun `un intervalle illisible affiche une erreur et n enregistre rien`() = runTest {
        remplirFormulaireValide()
        vue.choisirUnJourSurN()
        vue.modifierIntervalle("trois")

        vue.enregistrer().join()

        assertNotNull(vue.etat.value.erreur, "une saisie illisible doit etre signalee")
        assertTrue(medicaments.tous().isEmpty(), "rien ne doit etre ecrit en base")
        assertEquals(false, vue.etat.value.enregistre)
    }

    @Test
    fun `charger repeuple le champ intervalle depuis l ordonnance`() = runTest {
        remplirFormulaireValide()
        vue.choisirUnJourSurN()
        vue.modifierIntervalle("4")
        vue.enregistrer().join()
        val id = medicaments.tous().single().id

        vue.charger(id).join()

        assertEquals("4", vue.etat.value.intervalleJours)
        assertEquals(Rythme.UnJourSurN(4), vue.etat.value.rythme)
    }

    @Test
    fun `creer un medicament avec une date de debut passee ancre le rythme a cette date`() = runTest {
        remplirFormulaireValide()
        vue.modifierDateDebut(LocalDate.of(2026, 1, 1))

        vue.enregistrer().join()

        assertNull(vue.etat.value.erreur)
        val id = medicaments.tous().single().id
        assertEquals(
            LocalDate.of(2026, 1, 1),
            ordonnances.versionsDe(id).single().ordonnance.dateAncrage,
            "une creation n a pas de passe a proteger : elle doit ancrer le rythme a la date saisie, pas a aujourd hui",
        )
    }

    @Test
    fun `la date d effet part sur aujourd hui`() = runTest {
        assertEquals(horloge.aujourdhui(), vue.etat.value.dateEffet)
    }

    @Test
    fun `la date d effet saisie est celle transmise a l enregistrement`() = runTest {
        remplirFormulaireValide()
        vue.enregistrer().join()
        val id = medicaments.tous().single().id
        vue.charger(id).join()

        vue.modifierDateEffet(LocalDate.of(2026, 1, 10))
        vue.definirDose(Moment.SOIR, 2.0)
        vue.enregistrer().join()

        val versions = ordonnances.versionsDe(id).sortedBy { it.ordonnance.dateDebut }
        assertEquals(LocalDate.of(2026, 1, 10), versions.last().ordonnance.dateDebut)
    }

    @Test
    fun `charger remet la date d effet sur aujourd hui`() = runTest {
        remplirFormulaireValide()
        vue.enregistrer().join()
        val id = medicaments.tous().single().id

        vue.charger(id).join()

        assertEquals(horloge.aujourdhui(), vue.etat.value.dateEffet)
    }
}
