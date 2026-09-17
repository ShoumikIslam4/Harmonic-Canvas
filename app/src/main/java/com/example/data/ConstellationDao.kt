package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConstellationDao {
    @Query("SELECT * FROM constellations ORDER BY timestamp DESC")
    fun getAllConstellations(): Flow<List<ConstellationEntity>>

    @Query("SELECT * FROM constellations WHERE id = :id LIMIT 1")
    suspend fun getConstellationById(id: Long): ConstellationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConstellation(entity: ConstellationEntity): Long

    @Query("DELETE FROM constellations WHERE id = :id")
    suspend fun deleteConstellationById(id: Long)

    @Query("DELETE FROM constellations")
    suspend fun clearAll()
}
