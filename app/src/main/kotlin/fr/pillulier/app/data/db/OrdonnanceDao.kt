package fr.pillulier.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OrdonnanceDao {

    @Transaction
    @Query("SELECT * FROM ordonnance")
    fun observerToutes(): Flow<List<OrdonnanceAvecDosesEntity>>

    @Transaction
    @Query("SELECT * FROM ordonnance")
    suspend fun toutes(): List<OrdonnanceAvecDosesEntity>

    @Transaction
    @Query("SELECT * FROM ordonnance WHERE medicamentId = :medicamentId ORDER BY dateDebut")
    suspend fun versionsDe(medicamentId: Long): List<OrdonnanceAvecDosesEntity>

    @Insert
    suspend fun insererOrdonnance(ordonnance: OrdonnanceEntity): Long

    @Update
    suspend fun mettreAJourOrdonnance(ordonnance: OrdonnanceEntity)

    @Insert
    suspend fun insererDoses(doses: List<DosePrescriteEntity>)

    @Query("DELETE FROM dose_prescrite WHERE ordonnanceId = :ordonnanceId")
    suspend fun supprimerDoses(ordonnanceId: Long)

    @Query("SELECT COUNT(*) FROM dose_prescrite WHERE ordonnanceId = :ordonnanceId")
    suspend fun comptePourOrdonnance(ordonnanceId: Long): Int
}
