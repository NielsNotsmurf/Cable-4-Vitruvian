package com.example.vitruvianredux.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "suggestion_log")
data class SuggestionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val profileId: String,
    val exerciseId: String,
    val mode: String,
    val suggestedWeightKg: Float,
    val confidence: Float,
    val source: String,
    val actualWeightKg: Float? = null,
    val correctionDelta: Float? = null
)
