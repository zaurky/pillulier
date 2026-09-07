package fr.pillulier.app.usecase

import fr.pillulier.app.data.DepotEvenements
import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotMoments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.app.temps.Horloge
import fr.pillulier.app.ui.libelleDoseComplete
import fr.pillulier.domain.Moment
import fr.pillulier.domain.StatutPrise
import fr.pillulier.domain.prisesAttendues
import fr.pillulier.domain.statut
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject

data class EntreeSemaine(
    val medicamentId: Long,
    val nom: String,
    val libelleDose: String,
    val statut: StatutPrise,
)

data class EtatSemaine(
    val jours: List<LocalDate> = emptyList(),
    val cellules: Map<Pair<LocalDate, Moment>, List<EntreeSemaine>> = emptyMap(),
)

/** La grille du pillulier : sept jours en colonnes, quatre moments en lignes. */
class ObserverSemaine @Inject constructor(
    private val medicaments: DepotMedicaments,
    private val ordonnances: DepotOrdonnances,
    private val moments: DepotMoments,
    private val evenements: DepotEvenements,
    private val horloge: Horloge,
) {
    operator fun invoke(debut: LocalDate): Flow<EtatSemaine> {
        val jours = (0L until 7L).map { debut.plusDays(it) }

        return combine(
            medicaments.observerTous(),
            ordonnances.observerToutes(),
            moments.observer(),
            evenements.observerEntre(jours.first(), jours.last()),
        ) { tousMedicaments, toutesOrdonnances, heures, evenementsSemaine ->
            if (heures.size < Moment.entries.size) return@combine EtatSemaine()

            val parId = tousMedicaments.associateBy { it.id }
            val maintenant = horloge.maintenant()

            val cellules = jours.flatMap { jour ->
                prisesAttendues(jour, toutesOrdonnances, heures).mapNotNull { prise ->
                    val medicament = parId[prise.medicamentId] ?: return@mapNotNull null

                    (jour to prise.moment) to EntreeSemaine(
                        medicamentId = prise.medicamentId,
                        nom = medicament.nom,
                        libelleDose = libelleDoseComplete(prise.dose, medicament.forme),
                        statut = statut(prise, evenementsSemaine, maintenant),
                    )
                }
            }
                .groupBy({ it.first }, { it.second })

            EtatSemaine(jours = jours, cellules = cellules)
        }
    }
}
