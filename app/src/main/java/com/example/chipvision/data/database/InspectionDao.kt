package com.example.chipvision.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InspectionDao {
    @Query("SELECT * FROM inspection_history ORDER BY timestampMs DESC")
    fun getAllRecords(): Flow<List<InspectionRecord>>

    @Query("SELECT * FROM inspection_history WHERE isPass = 1 ORDER BY timestampMs DESC")
    fun getPassRecords(): Flow<List<InspectionRecord>>

    @Query("SELECT * FROM inspection_history WHERE isPass = 0 ORDER BY timestampMs DESC")
    fun getFailRecords(): Flow<List<InspectionRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: InspectionRecord): Long

    @Query("DELETE FROM inspection_history")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM inspection_history")
    suspend fun getCount(): Int
}
