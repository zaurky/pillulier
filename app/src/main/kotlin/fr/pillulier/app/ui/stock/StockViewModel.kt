package fr.pillulier.app.ui.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.pillulier.app.usecase.AjouterBoite
import fr.pillulier.app.usecase.CorrigerStock
import fr.pillulier.app.usecase.LigneStock
import fr.pillulier.app.usecase.ObserverStock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StockViewModel @Inject constructor(
    observerStock: ObserverStock,
    private val ajouterBoiteCas: AjouterBoite,
    private val corrigerStockCas: CorrigerStock,
) : ViewModel() {

    val lignes: StateFlow<List<LigneStock>> = observerStock()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun ajouterBoite(medicamentId: Long) = viewModelScope.launch {
        ajouterBoiteCas(medicamentId)
    }

    fun corriger(medicamentId: Long, unites: Double) = viewModelScope.launch {
        corrigerStockCas(medicamentId, unites)
    }
}
