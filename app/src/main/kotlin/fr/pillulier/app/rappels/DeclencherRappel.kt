package fr.pillulier.app.rappels

import fr.pillulier.app.data.DepotEvenements
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotMoments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.data.DepotPreferences
import fr.pillulier.app.temps.Horloge
import fr.pillulier.app.widget.RafraichirWidget
import fr.pillulier.domain.CleRappel
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Ce que fait une alarme de rappel quand elle se déclenche. Le préavis poste
 * en silence et arme la sonnerie à l'heure de la prise ; la sonnerie poste le
 * rappel sonore, plein écran pour un critique, et arme la relance suivante.
 * La chaîne s'interrompt quand la prise est enregistrée ou à la clôture de la
 * journée, qui annule la clé.
 */
class DeclencherRappel @Inject constructor(
    private val medicaments: DepotMedicaments,
    private val ordonnances: DepotOrdonnances,
    private val moments: DepotMoments,
    private val evenements: DepotEvenements,
    private val preferences: DepotPreferences,
    private val notifications: Notifications,
    private val programmateur: ProgrammateurAlarmes,
    private val horloge: Horloge,
    private val rafraichirWidget: RafraichirWidget,
) {
    suspend operator fun invoke(cle: CleRappel, critique: Boolean, phase: Phase) {
        val maintenant = horloge.maintenant()
        val heure = LocalDateTime.of(cle.date, moments.heures().getValue(cle.moment))
        // Un préavis retardé par le système au-delà de l'heure n'a plus rien
        // d'une annonce : il sonne directement.
        val preavis = phase == Phase.PREAVIS && heure.isAfter(maintenant)

        // « Aucune relance ne survit au lendemain » est un invariant du
        // rappel lui-même, pas une conséquence de la clôture de 00h05 :
        // le travail périodique est reportable, et une chaîne d'hier
        // relancerait sinon toutes les quinze minutes en pleine nuit. Seul
        // le préavis d'une prise juste après minuit tombe la veille.
        val aujourdhui = horloge.aujourdhui()
        if (cle.date != aujourdhui && !(preavis && cle.date == aujourdhui.plusDays(1))) return

        // L'alarme qui nous réveille peut être partie juste avant que la prise
        // soit cochée : `annuler` n'a aucune prise sur un déclenchement en vol.
        // C'est le journal qui tranche, jamais l'alarme — sinon la notification
        // revient et sa relance se perpétue toute la journée.
        val dejaPrise = evenements.entre(cle.date, cle.date).any {
            it.medicamentId == cle.medicamentId && it.moment == cle.moment
        }
        if (dejaPrise) return

        val medicament = medicaments.parId(cle.medicamentId) ?: return
        val dose = ordonnances.enVigueur(cle.medicamentId, cle.date)
            ?.doses
            ?.firstOrNull { it.moment == cle.moment }
            ?.dose
            ?: return

        if (preavis) {
            notifications.posterPreavis(cle, medicament, dose)
            rafraichirWidget()
            programmateur.programmer(cle = cle, quand = heure, critique = critique, phase = Phase.SONNERIE)
        } else {
            // Android n'ouvre l'écran plein écran que pour une notification
            // neuve, jamais pour la mise à jour d'une notification affichée :
            // posté par-dessus le préavis, le rappel critique sonnait sans
            // ouvrir l'alarme. Les relances, elles, restent des mises à jour.
            if (phase != Phase.RELANCE) notifications.retirer(cle)
            notifications.posterRappel(cle, medicament, dose, critique)
            rafraichirWidget()
            val intervalle = preferences.instantane().intervalleRelanceMinutes
            programmateur.programmer(
                cle = cle,
                quand = maintenant.plusMinutes(intervalle.toLong()),
                critique = critique,
                phase = Phase.RELANCE,
            )
        }
    }
}
