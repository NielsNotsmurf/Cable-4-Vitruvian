package com.example.vitruvianredux.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.vitruvianredux.data.local.SuggestionLogEntity

@Dao
interface SuggestionLogDao {

    @Insert
    suspend fun insert(log: SuggestionLogEntity): Long

    @Query("""
        SELECT * FROM suggestion_log
        WHERE profileId = :profileId
          AND exerciseId = :exerciseId
          AND actualWeightKg IS NULL
          AND timestamp > :since
        ORDER BY timestamp DESC
        LIMIT 1
    """)
    suspend fun findPendingLog(
        profileId: String,
        exerciseId: String,
        since: Long
    ): SuggestionLogEntity?

    @Query("UPDATE suggestion_log SET actualWeightKg = :actualKg, correctionDelta = :delta WHERE id = :id")
    suspend fun updateActual(id: Long, actualKg: Float, delta: Float)

    @Query("SELECT * FROM suggestion_log WHERE profileId = :profileId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLogs(profileId: String, limit: Int = 100): List<SuggestionLogEntity>
}
