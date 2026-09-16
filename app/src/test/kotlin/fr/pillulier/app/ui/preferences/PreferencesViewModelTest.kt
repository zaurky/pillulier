package fr.pillulier.app.ui.preferences

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
import fr.pillulier.app.usecase.HorlogeFigee
import fr.pillulier.app.usecase.ProgrammateurEspion
import fr.pillulier.app.usecase.ReArmerRappels
import fr.pillulier.app.widget.RafraichirWidget
import fr.pillulier.domain.Moment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import java.time.LocalTime
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PreferencesViewModelTest {

    private lateinit var base: PillulierDatabase
    private lateinit var moments: DepotMoments
    private lateinit var preferences: DepotPreferences
    private lateinit var vue: PreferencesViewModel
    private val programmateur = ProgrammateurEspion()
    private val horloge = HorlogeFigee(LocalDateTime.of(2026, 1, 5, 7, 0))

    @Before
    fun preparer() {
        // `viewModelScope` lance sur le dispatcher Main, en pause sous Robolectric :
        // sans ce remplacement, le `join()` des tests ne rendrait jamais la main.
        Dispatchers.setMain(UnconfinedTestDispatcher())

        val contexte = ApplicationProvider.getApplicationContext<android.content.Context>()
        base = Room.inMemoryDatabaseBuilder(contexte, PillulierDatabase::class.java)
            .addCallback(momentsParDefaut)
            .allowMainThreadQueries()
            .build()

        moments = DepotMoments(base.moments())
        preferences = DepotPreferences(contexte)
        vue = PreferencesViewModel(
            moments = moments,
            preferences = preferences,
            reArmerRappels = ReArmerRappels(
                ordonnances = DepotOrdonnances(base.ordonnances()),
                medicaments = DepotMedicaments(base.medicaments()),
                moments = moments,
                evenements = DepotEvenements(base.evenements()),
                programmateur = programmateur,
                horloge = horloge,
            ),
            rafraichirWidget = RafraichirWidget(contexte),
        )
    }

    @After
    fun fermer() {
        base.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `modifier l heure d un moment la persiste`() = runTest {
        vue.definirHeure(Moment.MATIN, LocalTime.of(7, 15)).join()

        assertEquals(LocalTime.of(7, 15), moments.heures().getValue(Moment.MATIN))
    }

    @Test
    fun `modifier le delai plus tard le persiste`() = runTest {
        vue.definirDelaiPlusTard(20).join()

        assertEquals(20, preferences.instantane().delaiPlusTardMinutes)
    }

    @Test
    fun `modifier l intervalle de relance le persiste`() = runTest {
        vue.definirIntervalleRelance(30).join()

        assertEquals(30, preferences.instantane().intervalleRelanceMinutes)
    }

    @Test
    fun `modifier le seuil d alerte par defaut le persiste`() = runTest {
        vue.definirSeuilJours(10).join()

        assertEquals(10, preferences.instantane().seuilAlerteJoursDefaut)
    }

    @Test
    fun `les autorisations ne sont marquees vues qu une fois demandees`() = runTest {
        assertEquals(false, preferences.autorisationsVues.first())

        preferences.marquerAutorisationsVues()

        assertEquals(true, preferences.autorisationsVues.first())
    }
}
