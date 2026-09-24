package fr.pillulier.app.usecase

import fr.pillulier.app.widget.RafraichirWidget
import javax.inject.Inject

/**
 * Le passage d'un jour au suivant : fermer la veille, reconstruire la fenetre
 * d'alarmes, remettre le widget sur la bonne journee.
 *
 * Deux declencheurs y menent — l'alarme exacte de minuit, qui porte la
 * ponctualite, et le travail periodique garde en filet. Les enchainer ici
 * plutot que dans chacun evite qu'ils divergent, et les deux etapes sont
 * idempotentes : rien ne souffre d'un double passage.
 */
class ClotureQuotidienne @Inject constructor(
    private val cloturerJournee: CloturerJournee,
    private val reArmerRappels: ReArmerRappels,
    private val rafraichirWidget: RafraichirWidget,
) {
    suspend operator fun invoke() {
        cloturerJournee()
        reArmerRappels()
        rafraichirWidget()
    }
}
