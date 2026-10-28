package nl.nielsnotsmurf.cleanui.data

import kotlinx.coroutines.flow.Flow

/**
 * Typed key-value storage behind [CleanUiStore]: DataStore preferences on Android,
 * localStorage on the web. Values are Boolean, Int, Long, Float or String.
 */
interface KeyValueStore {
    val data: Flow<Map<String, Any>>

    /** Changes the stored values in one go; [block] gets the current ones to change. */
    suspend fun edit(block: (MutableMap<String, Any>) -> Unit)
}

/** A named, typed entry in a [KeyValueStore]. */
class Key<T : Any>(val name: String)

@Suppress("UNCHECKED_CAST")
operator fun <T : Any> Map<String, Any>.get(key: Key<T>): T? = this[key.name] as? T

operator fun <T : Any> MutableMap<String, Any>.set(key: Key<T>, value: T) {
    this[key.name] = value
}

fun MutableMap<String, Any>.remove(key: Key<*>) {
    remove(key.name)
}
