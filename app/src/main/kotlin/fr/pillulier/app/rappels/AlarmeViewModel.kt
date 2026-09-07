package fr.pillulier.app.rappels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotPreferences
import fr.pillulier.app.temps.Horloge
import fr.pillulier.app.ui.libelleDoseComplete
import fr.pillulier.app.ui.libelleMoment
import fr.pillulier.app.usecase.EnregistrerPrise
import fr.pillulier.domain.CleRappel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EtatAlarme(
    val nom: String = "",
    val dosage: String = "",
    val libelleDose: String = "",
    val momentLisible: String = "",
    val termine: Boolean = false,
)

@HiltViewModel
class AlarmeViewModel @Inject constructor(
    private val medicaments: DepotMedicaments,
    private val enregistrerPrise: EnregistrerPrise,
    private val preferences: DepotPreferences,
    private val programmateur: ProgrammateurAlarmes,
    private val notifications: Notifications,
    private val horloge: Horloge,
) : ViewModel() {

    private val _etat = MutableStateFlow(EtatAlarme())
    val etat: StateFlow<EtatAlarme> = _etat.asStateFlow()

    private var cle: CleRappel? = null
    private var dose: Double = 0.0

    suspend fun charger(cle: CleRappel, dose: Double) {
        this.cle = cle
        this.dose = dose

        val medicament = medicaments.parId(cle.medicamentId) ?: return
        _etat.update {
            it.copy(
                nom = medicament.nom,
                dosage = medicament.dosage,
                libelleDose = libelleDoseComplete(dose, medicament.forme),
                momentLisible = libelleMoment(cle.moment),
            )
        }
    }

    suspend fun valider() {
        val cle = cle ?: return
        enregistrerPrise(cle.medicamentId, cle.date, cle.moment, dose)
        programmateur.annuler(cle)
        notifications.retirer(cle)
        _etat.update { it.copy(termine = true) }
    }

    suspend fun reporter() {
        val cle = cle ?: return
        val delai = preferences.instantane().delaiPlusTardMinutes
        programmateur.programmer(cle, horloge.maintenant().plusMinutes(delai.toLong()), critique = true)
        notifications.retirer(cle)
        _etat.update { it.copy(termine = true) }
    }

    /** Variantes non suspendues, appelées depuis les boutons de l'activité. */
    fun validerDepuisUi() = viewModelScope.launch { valider() }

    fun reporterDepuisUi() = viewModelScope.launch { reporter() }
}
