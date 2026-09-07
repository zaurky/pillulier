package fr.pillulier.app.data

import fr.pillulier.app.data.db.MomentConfigDao
import fr.pillulier.app.data.db.MomentConfigEntity
import fr.pillulier.domain.Moment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DepotMoments @Inject constructor(private val dao: MomentConfigDao) {

    fun observer(): Flow<Map<Moment, LocalTime>> =
        dao.observer().map { configs -> configs.associate { it.moment to it.heure } }

    suspend fun heures(): Map<Moment, LocalTime> = dao.tous().associate { it.moment to it.heure }

    suspend fun definir(moment: Moment, heure: LocalTime) =
        dao.enregistrer(MomentConfigEntity(moment, heure))
}
