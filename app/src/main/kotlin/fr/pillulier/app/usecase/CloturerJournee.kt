package fr.pillulier.app.usecase

import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.rappels.Notifications
import fr.pillulier.app.rappels.ProgrammateurAlarmes
import fr.pillulier.app.temps.Horloge
import fr.pillulier.domain.CleRappel
import fr.pillulier.domain.Moment
import javax.inject.Inject

/**
 * Ferme la veille : les relances sont annulées et les notifications retirées.
 * Rien n'est écrit en base — une prise non cochée devient oubliée par la seule
 * absence d'événement, dérivée à l'affichage.
 */
class CloturerJournee @Inject constructor(
    private val medicaments: DepotMedicaments,
    private val programmateur: ProgrammateurAlarmes,
    private val notifications: Notifications,
    private val horloge: Horloge,
) {
    suspend operator fun invoke() {
        val veille = horloge.aujourdhui().minusDays(1)

        // Même surensemble que le réarmement : une dose retirée de l'ordonnance
        // depuis hier ne serait plus produite par le planning, et sa notification
        // resterait dans le volet.
        medicaments.tous().forEach { medicament ->
            Moment.entries.forEach { moment ->
                val cle = CleRappel(medicament.id, veille, moment)
                programmateur.annuler(cle)
                notifications.retirer(cle)
            }
        }

        Moment.entries.forEach(notifications::retirerGroupe)
    }
}
