package fr.pillulier.app.ui.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.pillulier.app.data.DepotMoments
import fr.pillulier.app.data.DepotPreferences
import fr.pillulier.app.data.PreferencesPillulier
import fr.pillulier.app.usecase.ReArmerRappels
import fr.pillulier.app.widget.RafraichirWidget
import fr.pillulier.domain.Moment
import kotlinx.coroutines.Job
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

    /** Changer une heure de moment déplace les alarmes : on réarme aussitôt. */
    fun definirHeure(moment: Moment, heure: LocalTime): Job = viewModelScope.launch {
        moments.definir(moment, heure)
        reArmerRappels()
        rafraichirWidget()
    }

    fun definirDelaiPlusTard(minutes: Int): Job = viewModelScope.launch {
        preferences.definirDelaiPlusTard(minutes)
    }

    fun definirIntervalleRelance(minutes: Int): Job = viewModelScope.launch {
        preferences.definirIntervalleRelance(minutes)
    }

    fun definirSeuilJours(jours: Int): Job = viewModelScope.launch {
        preferences.definirSeuilAlerteJours(jours)
    }
}
