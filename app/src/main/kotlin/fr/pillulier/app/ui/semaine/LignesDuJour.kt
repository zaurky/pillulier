package fr.pillulier.app.ui.semaine

import fr.pillulier.app.usecase.EntreeSemaine
import fr.pillulier.app.usecase.EtatSemaine
import fr.pillulier.domain.Moment
import java.time.LocalDate

/** Un moment d'une journee et ce qu'il porte. Jamais vide. */
data class LigneJour(val moment: Moment, val entrees: List<EntreeSemaine>)

/**
 * Les moments renseignes d'une journee, dans l'ordre chronologique.
 *
 * Les moments vides sont ecartes plutot qu'affiches a blanc : un jour creux
 * occupe alors deux lignes au lieu de quatre, ce qui laisse les sept jours
 * tenir dans un ecran sans defilement horizontal.
 */
fun lignesDuJour(etat: EtatSemaine, jour: LocalDate): List<LigneJour> =
    Moment.entries.mapNotNull { moment ->
        etat.cellules[jour to moment]
            ?.takeIf { it.isNotEmpty() }
            ?.let { LigneJour(moment, it) }
    }
