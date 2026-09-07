package fr.pillulier.app.ui.medicaments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.domain.Medicament
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MedicamentsViewModel @Inject constructor(
    depot: DepotMedicaments,
) : ViewModel() {

    val medicaments: StateFlow<List<Medicament>> = depot.observerTous()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
