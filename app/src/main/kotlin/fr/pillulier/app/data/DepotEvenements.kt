package fr.pillulier.app.data

import fr.pillulier.app.data.db.EvenementPriseDao
import fr.pillulier.domain.EvenementPrise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DepotEvenements @Inject constructor(private val dao: EvenementPriseDao) {

    fun observerEntre(debut: LocalDate, fin: LocalDate): Flow<List<EvenementPrise>> =
        dao.observerEntre(debut, fin).map { entites -> entites.map { it.versDomaine() } }

    suspend fun entre(debut: LocalDate, fin: LocalDate): List<EvenementPrise> =
        dao.entre(debut, fin).map { it.versDomaine() }
}
