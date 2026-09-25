package fr.pillulier.app.ui.aujourdhui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.rappels.Notifications
import fr.pillulier.app.temps.Horloge
import fr.pillulier.app.usecase.Alerte
import fr.pillulier.app.usecase.EnregistrerPrise
import fr.pillulier.app.usecase.LigneJournee
import fr.pillulier.app.usecase.ObserverAlertes
import fr.pillulier.app.usecase.ObserverJournee
import fr.pillulier.app.usecase.ReArmerRappels
import fr.pillulier.app.widget.RafraichirWidget
import fr.pillulier.domain.CleRappel
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.TypeOrdonnance
import fr.pillulier.domain.enVigueur
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EtatAujourdhui(
    val lignes: List<LigneJournee> = emptyList(),
    val alertes: List<Alerte> = emptyList(),
    val aLaDemande: List<Medicament> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AujourdhuiViewModel @Inject constructor(
    observerJournee: ObserverJournee,
    observerAlertes: ObserverAlertes,
    medicaments: DepotMedicaments,
    ordonnances: DepotOrdonnances,
    private val enregistrerPrise: EnregistrerPrise,
    private val reArmerRappels: ReArmerRappels,
    private val notifications: Notifications,
    private val horloge: Horloge,
    private val rafraichirWidget: RafraichirWidget,
) : ViewModel() {

    // La date se relit au fil de l'eau, elle ne se grave pas à la construction :
    // un ViewModel vit aussi longtemps que son activité, et l'app laissée
    // ouverte la nuit affichait encore la veille au matin.
    val etat: StateFlow<EtatAujourdhui> = horloge.jours().flatMapLatest { aujourdhui ->
        combine(
            observerJournee(aujourdhui),
            observerAlertes(),
            // Les archivés doivent quitter cette liste : leur ordonnance est close,
            // donc `enVigueur` retombe à jamais sur la dernière version connue —
            // encore « à la demande » — et le médicament retiré resterait proposé.
            medicaments.observerActifs(),
            ordonnances.observerToutes(),
        ) { lignes, alertes, actifs, toutesOrdonnances ->
            // Les médicaments à la demande n'ont aucune prise planifiée : ils
            // sont proposés à part, pour un enregistrement ponctuel.
            val idsALaDemande = actifs
                .map { it.id }
                .filter { id ->
                    toutesOrdonnances.enVigueur(id, aujourdhui)?.ordonnance?.type ==
                        TypeOrdonnance.A_LA_DEMANDE
                }
                .toSet()

            EtatAujourdhui(
                lignes = lignes,
                alertes = alertes,
                aLaDemande = actifs.filter { it.id in idsALaDemande },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EtatAujourdhui())

    fun cocher(ligne: LigneJournee) = viewModelScope.launch {
        val jour = horloge.aujourdhui()
        enregistrerPrise(ligne.medicamentId, jour, ligne.moment, ligne.dose)
        // Le réarmement annule l'alarme mais pas la notification déjà postée :
        // celle d'une prise critique est `setOngoing`, donc impossible à
        // balayer, et resterait affichée jusqu'à la clôture de la journée.
        notifications.retirer(CleRappel(ligne.medicamentId, jour, ligne.moment))
        reArmerRappels()
        rafraichirWidget()
    }

    fun enregistrerALaDemande(medicamentId: Long, dose: Double) = viewModelScope.launch {
        enregistrerPrise(medicamentId, horloge.aujourdhui(), moment = null, dose = dose)
        rafraichirWidget()
    }
}
