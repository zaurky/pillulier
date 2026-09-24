package fr.pillulier.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface MedicamentDao {

    @Query("SELECT * FROM medicament ORDER BY nom COLLATE NOCASE")
    fun observerTous(): Flow<List<MedicamentEntity>>

    @Query("SELECT * FROM medicament WHERE archiveLe IS NULL ORDER BY nom COLLATE NOCASE")
    fun observerActifs(): Flow<List<MedicamentEntity>>

    @Query("SELECT * FROM medicament")
    suspend fun tous(): List<MedicamentEntity>

    @Query("SELECT * FROM medicament WHERE id = :id")
    suspend fun parId(id: Long): MedicamentEntity?

    @Insert
    suspend fun inserer(medicament: MedicamentEntity): Long

    @Update
    suspend fun mettreAJour(medicament: MedicamentEntity)

    @Query("UPDATE medicament SET archiveLe = :quand WHERE id = :id")
    suspend fun archiver(id: Long, quand: Instant)

    @Query("UPDATE medicament SET stockUnites = stockUnites + :delta WHERE id = :id")
    suspend fun ajusterStock(id: Long, delta: Double)

    @Query("UPDATE medicament SET stockUnites = :valeur WHERE id = :id")
    suspend fun definirStock(id: Long, valeur: Double)
}
