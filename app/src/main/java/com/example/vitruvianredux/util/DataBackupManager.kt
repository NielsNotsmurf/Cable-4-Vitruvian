package com.example.vitruvianredux.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.vitruvianredux.BuildConfig
import com.example.vitruvianredux.data.baseline.AdaptiveLearningRepository
import com.example.vitruvianredux.data.baseline.BaselineResults
import com.example.vitruvianredux.data.baseline.BaselineRepository
import com.example.vitruvianredux.data.local.*
import com.example.vitruvianredux.data.preferences.PreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

data class ImportResult(
    val sessionsImported: Int,
    val sessionsSkipped: Int,
    val metricsImported: Int,
    val routinesImported: Int,
    val routinesSkipped: Int,
    val routineExercisesImported: Int,
    val programsImported: Int,
    val programsSkipped: Int,
    val programDaysImported: Int,
    val personalRecordsImported: Int,
    val personalRecordsSkipped: Int,
    val profilesImported: Int = 0,
    val baselinesRestored: Int = 0,
    val preferencesRestored: Boolean = false
) {
    val totalImported: Int
        get() = sessionsImported + metricsImported + routinesImported +
                routineExercisesImported + programsImported + programDaysImported +
                personalRecordsImported + profilesImported + baselinesRestored

    val totalSkipped: Int
        get() = sessionsSkipped + routinesSkipped + programsSkipped + personalRecordsSkipped
}

data class BackupPreview(
    val backupVersion: Int,
    val exportedAt: String,
    val appVersion: String,
    val sessionCount: Int,
    val routineCount: Int,
    val personalRecordCount: Int,
    val profileCount: Int,
    val hasPreferences: Boolean,
    val hasBaselines: Boolean,
    val hasAdaptiveData: Boolean,
    val hasExerciseDefaults: Boolean
)

@Singleton
class DataBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workoutDao: WorkoutDao,
    private val personalRecordDao: PersonalRecordDao,
    private val userProfileDao: UserProfileDao,
    private val baselineRepository: BaselineRepository,
    private val adaptiveLearningRepository: AdaptiveLearningRepository,
    private val preferencesManager: PreferencesManager
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    suspend fun exportAllData(): BackupData = withContext(Dispatchers.IO) {
        val sessions = workoutDao.getAllSessionsSync()
        val metrics = workoutDao.getAllMetricsSync()
        val routines = workoutDao.getAllRoutinesSync()
        val routineExercises = workoutDao.getAllRoutineExercisesSync()
        val programs = workoutDao.getAllProgramsSync()
        val programDays = workoutDao.getAllProgramDaysSync()
        val personalRecords = personalRecordDao.getAllPRsSync()
        val profiles = userProfileDao.getAllProfilesSync()

        val profileIds = profiles.map { it.id }.ifEmpty { listOf("default") }

        val baselines = baselineRepository.getAllBaselines(profileIds).map { it.toBackup() }

        val adaptiveCorrections = profileIds.mapNotNull { profileId ->
            adaptiveLearningRepository.exportCorrectionDataAsJson(profileId)?.let { json ->
                AdaptiveCorrectionBackup(profileId = profileId, correctionsJson = json)
            }
        }

        val appPreferences = preferencesManager.exportPreferencesSnapshot()

        val exerciseDefaults = profileIds.map { profileId ->
            preferencesManager.exportExerciseDefaults(profileId)
        }.filter { it.justLiftDefaultsJson != null || it.singleExerciseDefaultsMapJson != null }

        Timber.d(
            "Export: sessions=${sessions.size}, routines=${routines.size}, " +
            "profiles=${profiles.size}, baselines=${baselines.size}, corrections=${adaptiveCorrections.size}"
        )

        BackupData(
            version = 2,
            exportedAt = dateFormat.format(Date()),
            appVersion = BuildConfig.VERSION_NAME,
            data = BackupContent(
                workoutSessions = sessions.map { it.toBackup() },
                workoutMetrics = metrics.map { it.toBackup() },
                routines = routines.map { it.toBackup() },
                routineExercises = routineExercises.map { it.toBackup() },
                weeklyPrograms = programs.map { it.toBackup() },
                programDays = programDays.map { it.toBackup() },
                personalRecords = personalRecords.map { it.toBackup() },
                profiles = profiles.map { it.toBackup() },
                baselines = baselines,
                adaptiveCorrections = adaptiveCorrections,
                appPreferences = appPreferences,
                exerciseDefaults = exerciseDefaults
            )
        )
    }

    suspend fun saveToDownloads(backup: BackupData): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val jsonString = json.encodeToString(backup)
            val timestamp = fileDateFormat.format(Date())
            val fileName = "vitruvian_backup_$timestamp.json"

            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/VitruvianRedux")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw Exception("Failed to create file in Downloads")
                resolver.openOutputStream(uri)?.use { it.write(jsonString.toByteArray()) }
                uri
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "VitruvianRedux"
                )
                downloadsDir.mkdirs()
                val file = File(downloadsDir, fileName)
                file.writeText(jsonString)
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }

            Result.success(uri)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save backup to Downloads")
            Result.failure(e)
        }
    }

    suspend fun saveToCache(backup: BackupData): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val jsonString = json.encodeToString(backup)
            val timestamp = fileDateFormat.format(Date())
            val fileName = "vitruvian_backup_$timestamp.json"
            val file = File(context.cacheDir, fileName)
            file.writeText(jsonString)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            Result.success(uri)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save backup to cache")
            Result.failure(e)
        }
    }

    /**
     * Parse a backup file and return a preview summary without importing anything.
     */
    suspend fun previewBackup(uri: Uri): Result<BackupPreview> = withContext(Dispatchers.IO) {
        try {
            val jsonString = readJsonFromUri(uri)
            val backup = json.decodeFromString<BackupData>(jsonString)
            Result.success(
                BackupPreview(
                    backupVersion = backup.version,
                    exportedAt = backup.exportedAt,
                    appVersion = backup.appVersion,
                    sessionCount = backup.data.workoutSessions.size,
                    routineCount = backup.data.routines.size,
                    personalRecordCount = backup.data.personalRecords.size,
                    profileCount = backup.data.profiles.size,
                    hasPreferences = backup.data.appPreferences != null,
                    hasBaselines = backup.data.baselines.isNotEmpty(),
                    hasAdaptiveData = backup.data.adaptiveCorrections.isNotEmpty(),
                    hasExerciseDefaults = backup.data.exerciseDefaults.isNotEmpty()
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to preview backup")
            Result.failure(e)
        }
    }

    /**
     * Import data from a backup JSON string.
     * Room entities: skip duplicates by ID.
     * DataStore data: skip-if-exists per profile.
     */
    suspend fun importFromJson(jsonString: String): Result<ImportResult> = withContext(Dispatchers.IO) {
        try {
            val backup = json.decodeFromString<BackupData>(jsonString)
            Timber.d("Importing backup v${backup.version} from app ${backup.appVersion}")
            doImport(backup)
        } catch (e: Exception) {
            Timber.e(e, "Failed to import backup")
            Result.failure(e)
        }
    }

    /**
     * Import data from a backup file URI.
     */
    suspend fun importFromUri(uri: Uri): Result<ImportResult> = withContext(Dispatchers.IO) {
        try {
            val jsonString = readJsonFromUri(uri)
            importFromJson(jsonString)
        } catch (e: Exception) {
            Timber.e(e, "Failed to read backup file")
            Result.failure(e)
        }
    }

    /**
     * Read a backup JSON string from URI without parsing — for the two-step preview+confirm flow.
     */
    suspend fun readJsonFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val stream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Cannot open file")
        stream.bufferedReader().use { it.readText() }
    }

    private suspend fun doImport(backup: BackupData): Result<ImportResult> {
        val existingSessionIds = workoutDao.getAllSessionIds().toSet()
        val existingRoutineIds = workoutDao.getAllRoutineIds().toSet()
        val existingProgramIds = workoutDao.getAllProgramIds().toSet()
        val existingPRIds = personalRecordDao.getAllPRIds().toSet()
        val existingProfileIds = userProfileDao.getAllProfilesSync().map { it.id }.toSet()

        // Sessions
        var sessionsImported = 0; var sessionsSkipped = 0
        backup.data.workoutSessions.forEach { s ->
            if (s.id !in existingSessionIds) { workoutDao.insertSessionIgnore(s.toEntity()); sessionsImported++ }
            else sessionsSkipped++
        }

        // Metrics — only for imported sessions
        var metricsImported = 0
        val importedSessionIds = backup.data.workoutSessions
            .filter { it.id !in existingSessionIds }.map { it.id }.toSet()
        backup.data.workoutMetrics.forEach { m ->
            if (m.sessionId in importedSessionIds) { workoutDao.insertMetricIgnore(m.toEntity()); metricsImported++ }
        }

        // Routines
        var routinesImported = 0; var routinesSkipped = 0
        backup.data.routines.forEach { r ->
            if (r.id !in existingRoutineIds) { workoutDao.insertRoutineIgnore(r.toEntity()); routinesImported++ }
            else routinesSkipped++
        }

        // Routine exercises — only for imported routines
        var routineExercisesImported = 0
        val importedRoutineIds = backup.data.routines
            .filter { it.id !in existingRoutineIds }.map { it.id }.toSet()
        backup.data.routineExercises.forEach { e ->
            if (e.routineId in importedRoutineIds) { workoutDao.insertRoutineExerciseIgnore(e.toEntity()); routineExercisesImported++ }
        }

        // Weekly programs
        var programsImported = 0; var programsSkipped = 0
        backup.data.weeklyPrograms.forEach { p ->
            if (p.id !in existingProgramIds) { workoutDao.insertProgramIgnore(p.toEntity()); programsImported++ }
            else programsSkipped++
        }

        // Program days — only for imported programs
        var programDaysImported = 0
        val importedProgramIds = backup.data.weeklyPrograms
            .filter { it.id !in existingProgramIds }.map { it.id }.toSet()
        backup.data.programDays.forEach { d ->
            if (d.programId in importedProgramIds) { workoutDao.insertProgramDayIgnore(d.toEntity()); programDaysImported++ }
        }

        // Personal records
        var personalRecordsImported = 0; var personalRecordsSkipped = 0
        backup.data.personalRecords.forEach { pr ->
            if (pr.id !in existingPRIds) { personalRecordDao.insertPRIgnore(pr.toEntity()); personalRecordsImported++ }
            else personalRecordsSkipped++
        }

        // Profiles (skip existing by ID)
        var profilesImported = 0
        backup.data.profiles.forEach { p ->
            if (p.id !in existingProfileIds) {
                userProfileDao.insert(p.toEntity())
                profilesImported++
            }
        }

        // Baselines — skip if profile already has data
        var baselinesRestored = 0
        backup.data.baselines.forEach { b ->
            val results = b.toBaselineResults()
            baselineRepository.restoreBaselineIfEmpty(results)
            baselinesRestored++ // counted as attempted; actual skip logged inside
        }

        // Adaptive corrections — skip if profile already has data
        backup.data.adaptiveCorrections.forEach { c ->
            adaptiveLearningRepository.importCorrectionDataIfEmpty(c.profileId, c.correctionsJson)
        }

        // App preferences — skip-if-exists per key
        backup.data.appPreferences?.let { preferencesManager.restorePreferencesSnapshot(it) }
        val preferencesRestored = backup.data.appPreferences != null

        // Exercise defaults — skip-if-exists per profile
        backup.data.exerciseDefaults.forEach { d ->
            preferencesManager.restoreExerciseDefaults(d)
        }

        val result = ImportResult(
            sessionsImported = sessionsImported,
            sessionsSkipped = sessionsSkipped,
            metricsImported = metricsImported,
            routinesImported = routinesImported,
            routinesSkipped = routinesSkipped,
            routineExercisesImported = routineExercisesImported,
            programsImported = programsImported,
            programsSkipped = programsSkipped,
            programDaysImported = programDaysImported,
            personalRecordsImported = personalRecordsImported,
            personalRecordsSkipped = personalRecordsSkipped,
            profilesImported = profilesImported,
            baselinesRestored = baselinesRestored,
            preferencesRestored = preferencesRestored
        )
        Timber.d("Import complete: $result")
        return Result.success(result)
    }
}

// ====== Additional extension functions for new backup types ======

fun UserProfileEntity.toBackup() = UserProfileBackup(
    id = id, name = name, colorInt = colorInt, createdAt = createdAt
)

fun UserProfileBackup.toEntity() = UserProfileEntity(
    id = id, name = name, colorInt = colorInt, createdAt = createdAt
)

fun BaselineResults.toBackup() = BaselineResultsBackup(
    profileId = profileId,
    completedAt = completedAt,
    chestKg = chestKg,
    backKg = backKg,
    posteriorChainKg = posteriorChainKg,
    quadsKg = quadsKg,
    hamstringsGlutesKg = hamstringsGlutesKg,
    shoulderPressKg = shoulderPressKg,
    lateralRaiseKg = lateralRaiseKg,
    bicepsKg = bicepsKg,
    tricepsKg = tricepsKg,
    isComplete = isComplete
)

fun BaselineResultsBackup.toBaselineResults() = BaselineResults(
    profileId = profileId,
    completedAt = completedAt,
    chestKg = chestKg,
    backKg = backKg,
    posteriorChainKg = posteriorChainKg,
    quadsKg = quadsKg,
    hamstringsGlutesKg = hamstringsGlutesKg,
    shoulderPressKg = shoulderPressKg,
    lateralRaiseKg = lateralRaiseKg,
    bicepsKg = bicepsKg,
    tricepsKg = tricepsKg,
    isComplete = isComplete
)
