package fr.pillulier.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import fr.pillulier.domain.Moment
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface EvenementPriseDao {

    /** Renvoie -1 si la prise planifiée était déjà enregistrée : l'insertion est idempotente. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun inserer(evenement: EvenementPriseEntity): Long

    @Query("SELECT * FROM evenement_prise WHERE date BETWEEN :debut AND :fin ORDER BY date, enregistreLe")
    fun observerEntre(debut: LocalDate, fin: LocalDate): Flow<List<EvenementPriseEntity>>

    @Query("SELECT * FROM evenement_prise WHERE date BETWEEN :debut AND :fin ORDER BY date, enregistreLe")
    suspend fun entre(debut: LocalDate, fin: LocalDate): List<EvenementPriseEntity>

    @Query("SELECT * FROM evenement_prise WHERE date = :date")
    suspend fun duJour(date: LocalDate): List<EvenementPriseEntity>

    @Query(
        "SELECT * FROM evenement_prise " +
            "WHERE medicamentId = :medicamentId AND date = :date AND moment = :moment LIMIT 1",
    )
    suspend fun planifie(medicamentId: Long, date: LocalDate, moment: Moment): EvenementPriseEntity?

    @Query(
        "DELETE FROM evenement_prise " +
            "WHERE medicamentId = :medicamentId AND date = :date AND moment = :moment",
    )
    suspend fun supprimerPlanifie(medicamentId: Long, date: LocalDate, moment: Moment)
}
