package fr.pillulier.app.usecase

import fr.pillulier.app.data.DepotEvenements
import fr.pillulier.app.temps.Horloge
import fr.pillulier.domain.CleRappel
import javax.inject.Inject

/**
 * Le journal a le dernier mot sur l'alarme qui reveille le recepteur.
 *
 * Une alarme deja partie ne se rattrape pas : `annuler` n'a aucune prise sur un
 * declenchement en vol. Cocher une prise — au widget, a l'ecran, ou par l'action
 * d'une notification — reprogramme la fenetre, mais le declenchement parti juste
 * avant arrive quand meme. Sans cette relecture, il reposte la notification d'un
 * medicament deja pris et rearme sa relance, qui se perpetue alors toute la
 * journee puisque chaque relance en rearme une autre.
 */
class RappelEncoreDu @Inject constructor(
    private val evenements: DepotEvenements,
    private val horloge: Horloge,
) {
    suspend operator fun invoke(cle: CleRappel): Boolean {
        // « Aucune relance ne survit au lendemain » est un invariant du rappel
        // lui-meme, pas une consequence de la cloture de 00h05 : le travail
        // periodique est reportable, et une chaine d'hier relancerait sinon
        // toutes les quinze minutes en pleine nuit.
        if (cle.date != horloge.aujourdhui()) return false

        return evenements.entre(cle.date, cle.date).none {
            it.medicamentId == cle.medicamentId && it.moment == cle.moment
        }
    }
}
