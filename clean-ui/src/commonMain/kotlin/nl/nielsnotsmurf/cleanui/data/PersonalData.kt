package nl.nielsnotsmurf.cleanui.data

import androidx.compose.runtime.Composable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * One file with a person's data, the same on Android and the web: everything in
 * [CleanUiStore] (1RMs, PINs, routines, Clean settings) plus the host's own data
 * (profiles, workouts, plans, settings) as the text the gateway gives for it.
 * The Clean part restores on any platform; the host part only on the platform that wrote it.
 */
object PersonalData {
    const val FORMAT = "cable-personal-data"
    const val VERSION = 1
    private const val LEGACY_WEB_FORMAT = "cable-web-backup"

    class Parsed(val clean: Map<String, Any>, val platform: String, val host: String?) {
        /** The profiles in the file's host part, so another platform can recreate them by name. */
        val lifters: List<FileLifter> get() = hostLifters(host)
    }

    class FileLifter(val id: String, val name: String, val color: Int)

    fun encode(clean: Map<String, Any>, platform: String, host: String?, exportedAt: Long): String = JsonObject(
        buildMap<String, JsonElement> {
            put("format", JsonPrimitive(FORMAT))
            put("version", JsonPrimitive(VERSION))
            put("exportedAt", JsonPrimitive(exportedAt))
            put("platform", JsonPrimitive(platform))
            put("clean", encodeTyped(clean))
            host?.let { put("host", JsonPrimitive(it)) }
        }
    ).toString()

    /** Null when [text] isn't a Cable data file. */
    fun decode(text: String): Parsed? = runCatching {
        val clean = text.trim().removePrefix("\uFEFF")
        val root = Json.parseToJsonElement(clean).jsonObject
        val format = root["format"]?.jsonPrimitive?.contentOrNull
        // Backups the web app saved before this file format existed.
        if (format == LEGACY_WEB_FORMAT) return Parsed(
            clean = root["clean_ui"]?.jsonPrimitive?.contentOrNull?.let { decodeTyped(Json.parseToJsonElement(it).jsonObject) }.orEmpty(),
            platform = "web",
            host = root["web_gateway"]?.jsonPrimitive?.contentOrNull
        )
        // The Android app's own backup (Download/Cable), from before this file format existed.
        if (format == null && root["data"] is JsonObject && root["appVersion"] != null) return Parsed(emptyMap(), "android", clean)
        if (format != FORMAT) return null
        Parsed(
            clean = decodeTyped(root["clean"]?.jsonObject ?: JsonObject(emptyMap())),
            platform = root["platform"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            host = root["host"]?.jsonPrimitive?.contentOrNull
        )
    }.getOrNull()

    private fun hostLifters(host: String?): List<FileLifter> = runCatching {
        val root = Json.parseToJsonElement(host ?: return emptyList()).jsonObject
        // Web: {"lifters": [{id, name, color}]}; Android: {"data": {"profiles": [{id, name, colorInt}]}}.
        val list = root["lifters"]?.jsonArray ?: root["data"]?.jsonObject?.get("profiles")?.jsonArray ?: return emptyList()
        list.mapNotNull { e ->
            val o = e.jsonObject
            val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val color = (o["color"] ?: o["colorInt"])?.jsonPrimitive?.intOrNull ?: -10354962
            FileLifter(id, name, color)
        }
    }.getOrDefault(emptyList())

    /**
     * Moves the Clean values of the file's profiles to the profiles here ([ids]: file id -> local id):
     * 1RMs, PINs and the like are stored per profile id, and routines hold "lifterId::exerciseId" loads.
     */
    fun remap(clean: Map<String, Any>, ids: Map<String, String>): Map<String, Any> {
        val moved = ids.filter { (from, to) -> from != to }
        if (moved.isEmpty()) return clean
        fun key(name: String): String {
            val bar = name.indexOf('|')
            if (bar < 0) return name
            val rest = name.substring(bar + 1)
            val id = rest.substringBefore(SEP)
            val to = moved[id] ?: return name
            return name.substring(0, bar + 1) + to + rest.removePrefix(id)
        }
        return clean.entries.associate { (name, value) ->
            val v: Any = when {
                name == "last_account" && value is String -> moved[value] ?: value
                name == "routines" && value is String ->
                    moved.entries.fold(value) { text, (from, to) -> text.replace("\"$from$SEP", "\"$to$SEP") }
                else -> value
            }
            key(name) to v
        }
    }

    private const val SEP = "::"

    /** {"name": {"t": "b|i|l|f|s", "v": value}}, so every value comes back with its type. */
    private fun encodeTyped(values: Map<String, Any>) = JsonObject(
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
    )

    private fun decodeTyped(root: JsonObject): Map<String, Any> = root.mapNotNull { (name, entry) ->
        runCatching {
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
        }.getOrNull()
    }.toMap()
}

/** Saving and opening a data file: the system file picker on Android, a download and file input on the web. */
interface DataFiles {
    /** False when the person cancelled or the file couldn't be written. */
    suspend fun save(fileName: String, text: String): Boolean

    /** The picked file's text, or null when cancelled. */
    suspend fun open(): String?
}

@Composable
expect fun rememberDataFiles(): DataFiles
