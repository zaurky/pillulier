package fr.pillulier.app.ui.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.pillulier.app.data.DepotMoments
import fr.pillulier.app.data.DepotPreferences
import fr.pillulier.app.data.PreferencesPillulier
import fr.pillulier.app.debug.JournalDebug // JOURNAL-DEBUG
import fr.pillulier.app.usecase.ReArmerRappels
import fr.pillulier.app.widget.RafraichirWidget
import fr.pillulier.domain.Moment
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

data class EtatPreferences(
    val heures: Map<Moment, LocalTime> = emptyMap(),
    val valeurs: PreferencesPillulier = PreferencesPillulier(),
)

@HiltViewModel
class PreferencesViewModel @Inject constructor(
    private val moments: DepotMoments,
    private val preferences: DepotPreferences,
    private val reArmerRappels: ReArmerRappels,
    private val rafraichirWidget: RafraichirWidget,
) : ViewModel() {

    val etat: StateFlow<EtatPreferences> = combine(
        moments.observer(),
        preferences.preferences,
    ) { heures, valeurs -> EtatPreferences(heures, valeurs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EtatPreferences())

    // JOURNAL-DEBUG — tout le bloc ci-dessous part avec le journal de diagnostic.

    private val _journalAffiche = MutableStateFlow<String?>(null)

    /** Nul tant que l'on n'a pas demande a voir le journal : il peut peser 512 Ko. */
    val journalAffiche: StateFlow<String?> = _journalAffiche.asStateFlow()

    fun afficherJournal(): Job = viewModelScope.launch {
        _journalAffiche.value = withContext(Dispatchers.IO) { JournalDebug.contenu() }
    }

    fun masquerJournal() { _journalAffiche.value = null }

    fun effacerJournal(): Job = viewModelScope.launch {
        withContext(Dispatchers.IO) { JournalDebug.effacer() }
        _journalAffiche.value = null
    }

    suspend fun uriDePartage(): Uri = withContext(Dispatchers.IO) { JournalDebug.uriDePartage() }

    /** Changer une heure de moment déplace les alarmes : on réarme aussitôt. */
    fun definirHeure(moment: Moment, heure: LocalTime): Job = viewModelScope.launch {
        moments.definir(moment, heure)
        reArmerRappels()
        rafraichirWidget()
    }

    fun definirDelaiPlusTard(minutes: Int): Job = viewModelScope.launch {
        preferences.definirDelaiPlusTard(minutes.coerceAtLeast(MINUTES_MINIMUM))
    }

    fun definirIntervalleRelance(minutes: Int): Job = viewModelScope.launch {
        preferences.definirIntervalleRelance(minutes.coerceAtLeast(MINUTES_MINIMUM))
    }

    fun definirSeuilJours(jours: Int): Job = viewModelScope.launch {
        preferences.definirSeuilAlerteJours(jours.coerceAtLeast(JOURS_MINIMUM))
    }

    /**
     * Les bornes vivent ici et non dans l'ecran : `DepotPreferences.ecrire`
     * exige une valeur strictement positive, et la violer leve dans la
     * coroutine — l'ecriture serait perdue sans bruit.
     */
    private companion object {
        const val MINUTES_MINIMUM = 5
        const val JOURS_MINIMUM = 1
    }
}
