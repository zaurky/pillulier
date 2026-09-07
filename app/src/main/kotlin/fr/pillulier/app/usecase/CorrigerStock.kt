package fr.pillulier.app.usecase

import fr.pillulier.app.data.db.MedicamentDao
import javax.inject.Inject

class CorrigerStock @Inject constructor(private val medicaments: MedicamentDao) {

    suspend operator fun invoke(medicamentId: Long, unites: Double) {
        require(unites >= 0.0) { "un stock recompte ne peut pas etre negatif, recu $unites" }
        medicaments.definirStock(medicamentId, unites)
    }
}
