package fr.pillulier.app.ui.semaine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.pillulier.app.temps.Horloge
import fr.pillulier.app.usecase.EtatSemaine
import fr.pillulier.app.usecase.ObserverSemaine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SemaineViewModel @Inject constructor(
    observerSemaine: ObserverSemaine,
    horloge: Horloge,
) : ViewModel() {

    private val _debut = MutableStateFlow(
        horloge.aujourdhui().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    )
    val debut: StateFlow<LocalDate> = _debut.asStateFlow()

    val etat: StateFlow<EtatSemaine> = _debut
        .flatMapLatest { observerSemaine(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EtatSemaine())

    fun semainePrecedente() = _debut.update { it.minusWeeks(1) }

    fun semaineSuivante() = _debut.update { it.plusWeeks(1) }
}
