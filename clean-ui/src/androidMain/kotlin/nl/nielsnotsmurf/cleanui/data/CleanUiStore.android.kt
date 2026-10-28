package nl.nielsnotsmurf.cleanui.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nl.nielsnotsmurf.cleanui.logic.OneRepMaxMap

private val Context.cleanUiDataStore: DataStore<Preferences> by preferencesDataStore(name = "clean_ui")

/** The same "clean_ui" DataStore as before, so stored data carries over unchanged. */
private class DataStoreKeyValueStore(private val store: DataStore<Preferences>) : KeyValueStore {
    override val data: Flow<Map<String, Any>> = store.data.map { prefs -> prefs.asMap().mapKeys { it.key.name } }

    override suspend fun edit(block: (MutableMap<String, Any>) -> Unit) {
        store.edit { prefs ->
            val keys = prefs.asMap().keys.associateBy { it.name }
            val before = prefs.asMap().mapKeys { it.key.name }
            val after = before.toMutableMap().also(block)
            (before.keys - after.keys).forEach { prefs.remove(keys.getValue(it)) }
            after.forEach { (name, value) ->
                if (before[name] == value) return@forEach
                when (value) {
                    is Boolean -> prefs[booleanPreferencesKey(name)] = value
                    is Int -> prefs[intPreferencesKey(name)] = value
                    is Long -> prefs[longPreferencesKey(name)] = value
                    is Float -> prefs[floatPreferencesKey(name)] = value
                    is String -> prefs[stringPreferencesKey(name)] = value
                    else -> error("Unsupported value for $name: $value")
                }
            }
        }
    }
}

@Volatile private var instance: CleanUiStore? = null

fun CleanUiStore.Companion.get(context: Context): CleanUiStore =
    instance ?: synchronized(CleanUiStore) {
        instance ?: context.applicationContext.let { app ->
            CleanUiStore(DataStoreKeyValueStore(app.cleanUiDataStore)) {
                runCatching { app.assets.open(OneRepMaxMap.ASSET).bufferedReader().use { it.readText() } }.getOrNull()
            }
        }.also { instance = it }
    }

@Composable
actual fun rememberCleanUiStore(): CleanUiStore {
    val context = LocalContext.current
    return remember(context) { CleanUiStore.get(context) }
}
