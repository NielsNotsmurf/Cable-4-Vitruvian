package nl.nielsnotsmurf.cleanui.data

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

private fun jsGetItem(key: String): String? = js("{ try { return window.localStorage.getItem(key); } catch (e) { return null; } }")

private fun jsSetItem(key: String, value: String): Unit = js("{ try { window.localStorage.setItem(key, value); } catch (e) {} }")

/**
 * localStorage under one key, as {"name": {"t": "b|i|l|f|s", "v": value}} so every value
 * comes back with the type it was saved with.
 */
private class LocalStorageKeyValueStore(private val storageKey: String) : KeyValueStore {
    private val state = MutableStateFlow(load())
    override val data: Flow<Map<String, Any>> = state

    override suspend fun edit(block: (MutableMap<String, Any>) -> Unit) {
        val next = state.value.toMutableMap().also(block).toMap()
        jsSetItem(storageKey, encode(next))
        state.value = next
    }

    private fun load(): Map<String, Any> = runCatching {
        val root = Json.parseToJsonElement(jsGetItem(storageKey) ?: return emptyMap()).jsonObject
        root.mapNotNull { (name, entry) ->
            val e = entry.jsonObject
            val v = e.getValue("v").jsonPrimitive
            val value: Any = when (e.getValue("t").jsonPrimitive.content) {
                "b" -> v.boolean
                "i" -> v.int
                "l" -> v.long
                "f" -> v.float
                else -> v.content
            }
            name to value
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun encode(values: Map<String, Any>): String = JsonObject(
        values.mapValues { (_, value) ->
            val (type, v) = when (value) {
                is Boolean -> "b" to JsonPrimitive(value)
                is Int -> "i" to JsonPrimitive(value)
                is Long -> "l" to JsonPrimitive(value)
                is Float -> "f" to JsonPrimitive(value)
                else -> "s" to JsonPrimitive(value.toString())
            }
            JsonObject(mapOf("t" to JsonPrimitive(type), "v" to v))
        }
    ).toString()
}

/** Set by the web app before it starts: the bundled one_rep_max_map.json. */
var webOneRepMaxJson: String? = null

private val webStore by lazy { CleanUiStore(LocalStorageKeyValueStore("clean_ui")) { webOneRepMaxJson } }

@Composable
actual fun rememberCleanUiStore(): CleanUiStore = webStore
