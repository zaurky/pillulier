package fr.pillulier.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MomentConfigDao {

    @Query("SELECT * FROM moment_config")
    fun observer(): Flow<List<MomentConfigEntity>>

    @Query("SELECT * FROM moment_config")
    suspend fun tous(): List<MomentConfigEntity>

    @Upsert
    suspend fun enregistrer(config: MomentConfigEntity)
}
