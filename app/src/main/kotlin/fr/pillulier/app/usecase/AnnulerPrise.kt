package fr.pillulier.app.usecase

import androidx.room.withTransaction
import fr.pillulier.app.data.db.EvenementPriseDao
import fr.pillulier.app.data.db.MedicamentDao
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.domain.Moment
import java.time.LocalDate
import javax.inject.Inject

/**
 * Efface le journal du réel et rend le stock dans la même transaction. Le stock
 * est recrédité de la dose **réellement enregistrée**, pas de la dose théorique
 * de l'ordonnance : une prise ajustée se rendrait sinon faux.
 *
 * Renvoie `false` si aucune prise planifiée n'était enregistrée, ce qui rend le
 * double appui inoffensif.
 */
class AnnulerPrise @Inject constructor(
    private val base: PillulierDatabase,
    private val evenements: EvenementPriseDao,
    private val medicaments: MedicamentDao,
) {
    suspend operator fun invoke(
        medicamentId: Long,
        date: LocalDate,
        moment: Moment,
    ): Boolean = base.withTransaction {
        val evenement = evenements.planifie(medicamentId, date, moment)
            ?: return@withTransaction false

        evenements.supprimerPlanifie(medicamentId, date, moment)
        medicaments.ajusterStock(medicamentId, evenement.doseReelle)
        true
    }
}
