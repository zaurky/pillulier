package fr.pillulier.app.data

import fr.pillulier.app.data.db.MedicamentDao
import fr.pillulier.domain.Medicament
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DepotMedicaments @Inject constructor(private val dao: MedicamentDao) {

    fun observerTous(): Flow<List<Medicament>> =
        dao.observerTous().map { entites -> entites.map { it.versDomaine() } }

    fun observerActifs(): Flow<List<Medicament>> =
        dao.observerActifs().map { entites -> entites.map { it.versDomaine() } }

    suspend fun tous(): List<Medicament> = dao.tous().map { it.versDomaine() }

    suspend fun parId(id: Long): Medicament? = dao.parId(id)?.versDomaine()

    /** Insère si l'identifiant est nul, met à jour sinon. Renvoie l'identifiant. */
    suspend fun enregistrer(medicament: Medicament): Long =
        if (medicament.id == 0L) {
            dao.inserer(medicament.versEntite())
        } else {
            dao.mettreAJour(medicament.versEntite())
            medicament.id
        }

    suspend fun archiver(id: Long, quand: Instant) = dao.archiver(id, quand)
}
