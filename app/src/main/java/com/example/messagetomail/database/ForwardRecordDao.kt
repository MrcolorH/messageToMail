package com.example.messagetomail.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.messagetomail.model.ForwardRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ForwardRecordDao {

    @Query("SELECT * FROM forward_records ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<ForwardRecord>

    @Query("SELECT * FROM forward_records ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<ForwardRecord>>

    @Insert
    suspend fun insert(record: ForwardRecord)

    @Query("DELETE FROM forward_records")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM forward_records WHERE success = 1")
    suspend fun getSuccessCount(): Int

    @Query("SELECT COUNT(*) FROM forward_records WHERE success = 0")
    suspend fun getFailureCount(): Int

    @Query("SELECT COUNT(*) FROM forward_records WHERE channel = :channel AND success = 1")
    suspend fun getSuccessCountByChannel(channel: String): Int

    @Query("SELECT COUNT(*) FROM forward_records WHERE timestamp >= :startTime")
    suspend fun getCountSince(startTime: Long): Int
}
