package fr.pillulier.app.data

import fr.pillulier.app.data.db.OrdonnanceDao
import fr.pillulier.domain.DosePrescrite
import fr.pillulier.domain.Ordonnance
import fr.pillulier.domain.OrdonnanceAvecDoses
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DepotOrdonnances @Inject constructor(private val dao: OrdonnanceDao) {

    fun observerToutes(): Flow<List<OrdonnanceAvecDoses>> =
        dao.observerToutes().map { entites -> entites.map { it.versDomaine() } }

    suspend fun toutes(): List<OrdonnanceAvecDoses> = dao.toutes().map { it.versDomaine() }

    suspend fun pourMedicament(medicamentId: Long): OrdonnanceAvecDoses? =
        dao.pourMedicament(medicamentId)?.versDomaine()

    /**
     * Écrit l'ordonnance du médicament et remplace ses doses. Un médicament n'a
     * qu'une ordonnance : réenregistrer met à jour celle qui existe.
     */
    suspend fun enregistrer(
        medicamentId: Long,
        ordonnance: Ordonnance,
        doses: List<DosePrescrite>,
    ) {
        val existante = dao.pourMedicament(medicamentId)
        val id = if (existante == null) {
            dao.insererOrdonnance(ordonnance.copy(id = 0, medicamentId = medicamentId).versEntite())
        } else {
            dao.mettreAJourOrdonnance(
                ordonnance.copy(id = existante.ordonnance.id, medicamentId = medicamentId).versEntite(),
            )
            existante.ordonnance.id
        }

        dao.supprimerDoses(id)
        dao.insererDoses(doses.map { it.versEntite(id) })
    }
}
