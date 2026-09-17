package fr.pillulier.app.widget

import fr.pillulier.app.usecase.LigneJournee
import fr.pillulier.domain.Moment
import fr.pillulier.domain.StatutPrise
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * La prise qu'on vient de cocher, et jusqu'à quand on peut encore la décocher.
 * `date` est le jour exact de la prise enregistrée : sans elle, une annulation
 * qui recalculerait « aujourd'hui » au moment du clic annulerait la prise du
 * jour suivant si le clic tombe après minuit.
 */
data class Annulable(
    val medicamentId: Long,
    val moment: Moment,
    val date: LocalDate,
    val expiration: Instant,
)

/** Une ligne telle que le widget la dessine. */
data class LigneWidget(
    val medicamentId: Long,
    val moment: Moment,
    val nom: String,
    val dosage: String,
    val libelleDose: String,
    val dose: Double,
    val heure: LocalTime,
    val enRetard: Boolean,
    val barree: Boolean,
    /**
     * Le jour que cette ligne désigne : celui du rendu, ou celui de l'annulable
     * quand la ligne est barrée. Il voyage jusqu'aux deux actions, qui refusent
     * d'agir s'il ne correspond plus — un widget peut afficher des pixels figés
     * depuis la veille.
     */
    val date: LocalDate,
)

/**
 * Le widget ne montre que ce qu'il reste à prendre — plus, le temps de la
 * fenêtre d'annulation, la prise qu'on vient de cocher. Celle-ci est passée
 * `PRISE`, donc sortie du filtre : il faut la réinjecter pour l'afficher barrée.
 *
 * L'expiration est absolue et revérifiée ici, et pas seulement effacée par la
 * composition : si le processus a été tué pendant la fenêtre, l'état persisté
 * survit et ne doit pas ressusciter une ligne barrée.
 */
fun lignesDuWidget(
    lignes: List<LigneJournee>,
    annulable: Annulable?,
    jour: LocalDate,
    maintenant: Instant,
): List<LigneWidget> {
    val ouvert = annulable?.takeIf { it.expiration.isAfter(maintenant) }

    return lignes
        .filter { ligne ->
            when (ligne.statut) {
                StatutPrise.EN_RETARD, StatutPrise.A_VENIR -> true
                StatutPrise.PRISE ->
                    ouvert != null &&
                        ouvert.medicamentId == ligne.medicamentId &&
                        ouvert.moment == ligne.moment
                StatutPrise.OUBLIEE -> false
            }
        }
        .map { ligne ->
            LigneWidget(
                medicamentId = ligne.medicamentId,
                moment = ligne.moment,
                nom = ligne.nom,
                dosage = ligne.dosage,
                libelleDose = ligne.libelleDose,
                dose = ligne.dose,
                heure = ligne.heure,
                enRetard = ligne.statut == StatutPrise.EN_RETARD,
                barree = ligne.statut == StatutPrise.PRISE,
                // Une ligne barrée porte la date exacte de l'annulable persisté,
                // pas le jour recalculé : si la fenêtre de dix secondes chevauche
                // minuit, l'annulation doit encore désigner la prise de la veille.
                date = if (ligne.statut == StatutPrise.PRISE) ouvert?.date ?: jour else jour,
            )
        }
}
