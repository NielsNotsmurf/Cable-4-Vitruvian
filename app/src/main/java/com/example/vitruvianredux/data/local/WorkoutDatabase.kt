package com.example.vitruvianredux.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.vitruvianredux.data.local.dao.SuggestionLogDao

/**
 * Room database for workout history
 *
 * Version history:
 * - v29: heart_rate_samples table, avg/max heart rate on workout_sessions, paired HR sensor on user_profiles
 * - v22: Added aliases and defaultCableConfig to exercises, isTutorial to exercise_videos
 * - v21: Added exerciseName to WorkoutSessionEntity
 * - v20: Added isAMRAP to routine_exercises for AMRAP workout mode
 * - v19: Schema cleanup - forces fresh DB creation to fix SQL DEFAULT inconsistencies
 * - v18: Added perSetRestTime boolean flag to routine_exercises for toggleable per-set rest time feature
 * - v17: Added setRestSeconds (JSON array) to routine_exercises for per-set rest time configuration
 * - v16: Added routineSessionId and routineName to workout_sessions for grouping routine sets in history
 * - v15: Added exerciseId to workout_sessions for PR tracking
 * - v14: Added ConnectionLogEntity for Bluetooth connection debugging
 * - v13: Added eccentricLoad and echoLevel to workout_sessions for Echo mode persistence
 * - v12: Added setWeights, mode, eccentricLoad, echoLevel to routine_exercises
 * - v11: Added WeeklyProgramEntity and ProgramDayEntity for weekly program scheduling
 * - v10: Added exerciseId to routine_exercises for exercise library integration
 * - v9: Renamed progressionKg to progressionRegressionKg in workout_sessions, added personal_records
 * - v8: Schema cleanup for routine_exercises
 * - v7: Added exercise detail fields to RoutineExerciseEntity (muscleGroup, equipment, defaultCableConfig)
 *       to support Exercise data class instead of enum
 * - v6: Added ExerciseEntity and ExerciseVideoEntity for exercise library
 */
@Database(
    entities = [
        WorkoutSessionEntity::class,
        WorkoutMetricEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        ExerciseEntity::class,
        ExerciseVideoEntity::class,
        PersonalRecordEntity::class,
        WeeklyProgramEntity::class,
        ProgramDayEntity::class,
        ConnectionLogEntity::class,
        com.example.vitruvianredux.data.local.entity.PhaseStatisticsEntity::class,
        com.example.vitruvianredux.data.local.entity.DiagnosticsEntity::class,
        UserProfileEntity::class,
        SuggestionLogEntity::class,
        HeartRateSampleEntity::class
    ],
    version = 29,  // Added heart_rate_samples, session avg/max heart rate, profile heart rate sensor
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun personalRecordDao(): PersonalRecordDao
    abstract fun connectionLogDao(): ConnectionLogDao
    abstract fun phaseStatisticsDao(): com.example.vitruvianredux.data.local.dao.PhaseStatisticsDao
    abstract fun diagnosticsDao(): com.example.vitruvianredux.data.local.dao.DiagnosticsDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun suggestionLogDao(): SuggestionLogDao
}
