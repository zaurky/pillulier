package fr.pillulier.app.ui.medicaments

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.pillulier.app.data.DepotEvenements
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotMoments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.data.db.momentsParDefaut
import fr.pillulier.app.usecase.EnregistrerMedicament
import fr.pillulier.app.usecase.HorlogeFigee
import fr.pillulier.app.usecase.ProgrammateurEspion
import fr.pillulier.app.usecase.ReArmerRappels
import fr.pillulier.app.usecase.SupprimerMedicament
import fr.pillulier.domain.Moment
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
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EditionViewModelTest {

    private lateinit var base: PillulierDatabase
    private lateinit var medicaments: DepotMedicaments
    private lateinit var vue: EditionViewModel
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 5, 7, 0))

    @Before
    fun preparer() {
        // `viewModelScope` lance sur le dispatcher Main, en pause sous Robolectric.
        Dispatchers.setMain(UnconfinedTestDispatcher())

        val contexte = ApplicationProvider.getApplicationContext<android.content.Context>()
        base = Room.inMemoryDatabaseBuilder(contexte, PillulierDatabase::class.java)
            .addCallback(momentsParDefaut)
            .allowMainThreadQueries()
            .build()

        medicaments = DepotMedicaments(base.medicaments())
        val ordonnances = DepotOrdonnances(base.ordonnances())
        val reArmer = ReArmerRappels(
            ordonnances = ordonnances,
            medicaments = medicaments,
            moments = DepotMoments(base.moments()),
            evenements = DepotEvenements(base.evenements()),
            programmateur = ProgrammateurEspion(),
            horloge = horloge,
        )

        vue = EditionViewModel(
            medicaments = medicaments,
            ordonnances = ordonnances,
            enregistrerMedicament = EnregistrerMedicament(medicaments, ordonnances, reArmer),
            supprimerMedicament = SupprimerMedicament(medicaments, reArmer),
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
}
