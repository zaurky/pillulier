package fr.pillulier.app.usecase

import fr.pillulier.app.data.DepotEvenements
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotMoments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.rappels.ProgrammateurAlarmes
import fr.pillulier.app.temps.Horloge
import fr.pillulier.domain.CleRappel
import fr.pillulier.domain.Moment
import fr.pillulier.domain.StatutPrise
import fr.pillulier.domain.prisesAttendues
import fr.pillulier.domain.statut
import javax.inject.Inject

/**
 * Reconstruit la fenêtre glissante d'alarmes depuis le moteur. Appelé au
 * démarrage du téléphone, à la clôture quotidienne, et après toute modification
 * d'ordonnance, de médicament ou d'heure de moment.
 */
class ReArmerRappels @Inject constructor(
    private val ordonnances: DepotOrdonnances,
    private val medicaments: DepotMedicaments,
    private val moments: DepotMoments,
    private val evenements: DepotEvenements,
    private val programmateur: ProgrammateurAlarmes,
    private val horloge: Horloge,
) {
    suspend operator fun invoke() {
        val maintenant = horloge.maintenant()
        val aujourdhui = maintenant.toLocalDate()
        val heures = moments.heures()
        val toutes = ordonnances.toutes()
        val tousMedicaments = medicaments.tous()
        val critiques = tousMedicaments.filter { it.critique }.map { it.id }.toSet()

        // On annule un surensemble : tout médicament connu, tout moment, sur la
        // veille et la fenêtre. Dériver les clés du planning courant laisserait
        // vivre l'alarme d'une dose qui vient d'être retirée de l'ordonnance, ou
        // d'une cure dont la date de fin a reculé.
        (-1L until JOURS_FENETRE).forEach { decalage ->
            val jour = aujourdhui.plusDays(decalage)
            tousMedicaments.forEach { medicament ->
                Moment.entries.forEach { moment ->
                    programmateur.annuler(CleRappel(medicament.id, jour, moment))
                }
            }
        }

        val dernierJour = aujourdhui.plusDays(JOURS_FENETRE - 1)
        val dejaPrises = evenements.entre(aujourdhui, dernierJour)

        (0L until JOURS_FENETRE).map { aujourdhui.plusDays(it) }.forEach { jour ->
            prisesAttendues(jour, toutes, heures)
                .filter { statut(it, dejaPrises, maintenant) != StatutPrise.PRISE }
                .forEach { prise ->
                    programmateur.programmer(
                        cle = CleRappel(prise.medicamentId, jour, prise.moment),
                        // Une prise du jour déjà due mais sans réponse repart
                        // tout de suite plutôt que d'être perdue.
                        quand = if (prise.heure.isAfter(maintenant)) prise.heure else maintenant.plusMinutes(1),
                        critique = prise.medicamentId in critiques,
                    )
                }
        }
    }

    companion object {
        const val JOURS_FENETRE = 3L
    }
}
