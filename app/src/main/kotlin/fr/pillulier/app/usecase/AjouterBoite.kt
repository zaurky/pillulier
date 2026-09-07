package fr.pillulier.app.usecase

import fr.pillulier.app.data.db.MedicamentDao
import javax.inject.Inject

class AjouterBoite @Inject constructor(private val medicaments: MedicamentDao) {

    suspend operator fun invoke(medicamentId: Long) {
        val medicament = checkNotNull(medicaments.parId(medicamentId)) {
            "medicament $medicamentId inconnu"
        }
        medicaments.ajusterStock(medicamentId, medicament.unitesParBoite.toDouble())
    }
}
