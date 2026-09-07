package fr.pillulier.app.usecase

import androidx.room.withTransaction
import fr.pillulier.app.data.db.EvenementPriseDao
import fr.pillulier.app.data.db.EvenementPriseEntity
import fr.pillulier.app.data.db.MedicamentDao
import fr.pillulier.app.data.db.PillulierDatabase
import fr.pillulier.app.temps.Horloge
import fr.pillulier.domain.Moment
import java.time.LocalDate
import javax.inject.Inject

/**
 * Écrit le journal du réel et décrémente le stock dans la même transaction.
 * Renvoie `false` si la prise planifiée était déjà enregistrée : l'appel est
 * idempotent, ce qui protège du double appui sur l'action d'une notification.
 */
class EnregistrerPrise @Inject constructor(
    private val base: PillulierDatabase,
    private val evenements: EvenementPriseDao,
    private val medicaments: MedicamentDao,
    private val horloge: Horloge,
) {
    suspend operator fun invoke(
        medicamentId: Long,
        date: LocalDate,
        moment: Moment?,
        dose: Double,
    ): Boolean = base.withTransaction {
        val insere = evenements.inserer(
            EvenementPriseEntity(
                medicamentId = medicamentId,
                date = date,
                moment = moment,
                doseReelle = dose,
                enregistreLe = horloge.instant(),
            ),
        )

        if (insere == -1L) {
            false
        } else {
            medicaments.ajusterStock(medicamentId, -dose)
            true
        }
    }
}
