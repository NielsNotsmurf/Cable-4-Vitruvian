package com.example.vitruvianredux

import android.app.Application
import com.example.vitruvianredux.data.profiles.ProfileManager
import com.example.vitruvianredux.data.repository.ExerciseRepository
import com.example.vitruvianredux.data.repository.UserProfileRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class VitruvianApp : Application() {

    @Inject
    lateinit var exerciseRepository: ExerciseRepository

    @Inject
    lateinit var userProfileRepository: UserProfileRepository

    @Inject
    lateinit var profileManager: ProfileManager

    // Application-level coroutine scope
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.d("Vitruvian Trainer Control app initialized")

        applicationScope.launch {
            try {
                exerciseRepository.importExercises()
                    .onSuccess { Timber.d("Exercise library ready") }
                    .onFailure { e -> Timber.e(e, "Failed to import exercises") }
            } catch (e: Exception) {
                Timber.e(e, "Error during exercise import")
            }
        }

        applicationScope.launch {
            try {
                val seeded = userProfileRepository.seedDefaultProfiles()
                if (seeded) profileManager.setActiveProfile("niels")
            } catch (e: Exception) {
                Timber.e(e, "Error seeding default profiles")
            }
        }
    }
}

