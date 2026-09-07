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
import java.time.LocalTime
import javax.inject.Inject

data class LigneJournee(
    val medicamentId: Long,
    val nom: String,
    val dosage: String,
    val moment: Moment,
    val heure: LocalTime,
    val dose: Double,
    val libelleDose: String,
    val statut: StatutPrise,
)

/** Les prises d'une date, décorées du nom du médicament et de leur statut. */
class ObserverJournee @Inject constructor(
    private val medicaments: DepotMedicaments,
    private val ordonnances: DepotOrdonnances,
    private val moments: DepotMoments,
    private val evenements: DepotEvenements,
    private val horloge: Horloge,
) {
    operator fun invoke(date: LocalDate): Flow<List<LigneJournee>> = combine(
        medicaments.observerTous(),
        ordonnances.observerToutes(),
        moments.observer(),
        evenements.observerEntre(date, date),
    ) { tousMedicaments, toutesOrdonnances, heures, evenementsDuJour ->
        if (heures.size < Moment.entries.size) return@combine emptyList()

        val parId = tousMedicaments.associateBy { it.id }
        val maintenant = horloge.maintenant()

        prisesAttendues(date, toutesOrdonnances, heures).mapNotNull { prise ->
            val medicament = parId[prise.medicamentId] ?: return@mapNotNull null

            LigneJournee(
                medicamentId = prise.medicamentId,
                nom = medicament.nom,
                dosage = medicament.dosage,
                moment = prise.moment,
                heure = prise.heure.toLocalTime(),
                dose = prise.dose,
                libelleDose = libelleDoseComplete(prise.dose, medicament.forme),
                statut = statut(prise, evenementsDuJour, maintenant),
            )
        }
    }
}
