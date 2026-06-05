package com.example.vitruvianredux.data.profiles

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(name = "profile_preferences")

@Singleton
class ProfileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ACTIVE_PROFILE_ID_KEY = stringPreferencesKey("active_profile_id")

    val activeProfileIdFlow: Flow<String> = context.profileDataStore.data
        .map { prefs -> prefs[ACTIVE_PROFILE_ID_KEY] ?: "default" }

    suspend fun setActiveProfile(profileId: String) {
        context.profileDataStore.edit { prefs ->
            prefs[ACTIVE_PROFILE_ID_KEY] = profileId
        }
    }
}
