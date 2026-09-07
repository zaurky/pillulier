package fr.pillulier.app.usecase

import fr.pillulier.app.ui.formaterDose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class Alerte(
    val medicamentId: Long,
    val nom: String,
    val message: String,
)

/**
 * Les médicaments à renouveler, dérivés de l'écran Stock plutôt que recalculés :
 * la projection, la date d'alerte et la décision d'alerter vivent dans
 * [ObserverStock], et cette classe ne fait que les mettre en phrase.
 */
class ObserverAlertes @Inject constructor(
    private val observerStock: ObserverStock,
) {
    operator fun invoke(): Flow<List<Alerte>> = observerStock().map { lignes ->
        lignes.filter { it.enAlerte }.map { ligne ->
            Alerte(
                medicamentId = ligne.medicamentId,
                nom = ligne.nom,
                message = when {
                    ligne.aLaDemande -> "il reste ${formaterDose(ligne.unitesRestantes)} unités"
                    ligne.joursRestants == null || ligne.joursRestants <= 0L -> "stock épuisé"
                    else -> "plus que ${ligne.joursRestants} jours"
                },
            )
        }
    }
}
