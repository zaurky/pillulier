package fr.pillulier.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

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

    @Insert
    suspend fun insererDoses(doses: List<DosePrescriteEntity>)

    @Query("DELETE FROM ordonnance WHERE medicamentId = :medicamentId AND dateDebut >= :dateEffet")
    suspend fun supprimerVersionsDepuis(medicamentId: Long, dateEffet: LocalDate)

    @Query(
        "UPDATE ordonnance SET dateFin = :veille " +
            "WHERE medicamentId = :medicamentId AND dateDebut < :dateEffet " +
            "AND (dateFin IS NULL OR dateFin >= :dateEffet)",
    )
    suspend fun cloturerVersionsAvant(medicamentId: Long, dateEffet: LocalDate, veille: LocalDate)

    @Query(
        "UPDATE ordonnance SET dateFin = :dateFin " +
            "WHERE medicamentId = :medicamentId AND (dateFin IS NULL OR dateFin > :dateFin)",
    )
    suspend fun cloturerOuvertes(medicamentId: Long, dateFin: LocalDate)

    @Query("SELECT COUNT(*) FROM dose_prescrite WHERE ordonnanceId = :ordonnanceId")
    suspend fun comptePourOrdonnance(ordonnanceId: Long): Int
}
