package fr.pillulier.app.usecase

import fr.pillulier.app.data.DepotMedicaments
import fr.pillulier.app.data.DepotOrdonnances
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Medicament
import fr.pillulier.domain.Ordonnance
import fr.pillulier.domain.Rythme
import fr.pillulier.domain.TypeOrdonnance
import java.time.LocalDate
import javax.inject.Inject

/**
 * Écrit le médicament et son ordonnance, puis réarme la fenêtre : toute
 * modification de posologie doit se refléter tout de suite dans les alarmes.
 */
class EnregistrerMedicament @Inject constructor(
    private val medicaments: DepotMedicaments,
    private val ordonnances: DepotOrdonnances,
    private val reArmerRappels: ReArmerRappels,
) {
    suspend operator fun invoke(
        medicament: Medicament,
        type: TypeOrdonnance,
        rythme: Rythme,
        dateDebut: LocalDate,
        dateFin: LocalDate?,
        doses: List<DosePrescrite>,
    ): Long {
        require(medicament.nom.isNotBlank()) { "le nom du medicament est obligatoire" }
        require(medicament.unitesParBoite > 0) { "une boite contient au moins une unite" }
        require(dateFin == null || !dateFin.isBefore(dateDebut)) {
            "la date de fin ne peut pas preceder la date de debut"
        }
        if (type == TypeOrdonnance.PLANIFIEE) {
            require(doses.isNotEmpty()) { "une ordonnance planifiee doit porter au moins une dose" }
            require(doses.all { it.dose > 0.0 }) { "une dose doit etre strictement positive" }
            require(doses.map { it.moment }.distinct().size == doses.size) {
                "un moment ne peut porter qu une dose"
            }
        }

        val id = medicaments.enregistrer(medicament)

        ordonnances.enregistrer(
            medicamentId = id,
            ordonnance = Ordonnance(
                id = 0,
                medicamentId = id,
                type = type,
                rythme = rythme,
                dateDebut = dateDebut,
                dateFin = dateFin,
            ),
            doses = if (type == TypeOrdonnance.PLANIFIEE) doses else emptyList(),
        )

        reArmerRappels()
        return id
    }
}

class SupprimerMedicament @Inject constructor(
    private val medicaments: DepotMedicaments,
    private val reArmerRappels: ReArmerRappels,
) {
    suspend operator fun invoke(medicamentId: Long) {
        medicaments.supprimer(medicamentId)
        reArmerRappels()
    }
}
