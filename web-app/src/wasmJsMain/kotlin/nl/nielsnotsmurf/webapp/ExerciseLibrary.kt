package nl.nielsnotsmurf.webapp

import com.example.vitruvianredux.domain.model.ExerciseBucket
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nl.nielsnotsmurf.cleanui.logic.ExerciseSearch
import nl.nielsnotsmurf.cleanui.model.ExerciseCard
import nl.nielsnotsmurf.cleanui.model.ExerciseGuide

/**
 * The app's bundled exercise library (exercise_dump.json), mapped the way the Android app's
 * importer and CleanUiBridge do it, so cards, regions and guides read the same.
 */
class ExerciseLibrary(val cards: List<ExerciseCard>, private val guides: Map<String, ExerciseGuide>) {

    fun guide(exerciseId: String): ExerciseGuide? = guides[exerciseId]

    companion object {
        fun parse(json: String): ExerciseLibrary {
            val cards = mutableListOf<ExerciseCard>()
            val guides = mutableMapOf<String, ExerciseGuide>()
            for (element in Json.parseToJsonElement(json).jsonArray) {
                val e = element.jsonObject
                if (e.bool("archived") == true) continue
                val id = e.text("id") ?: continue
                val name = e.text("name") ?: continue
                val muscleGroups = e.list("muscleGroups")
                val muscles = e.list("muscles")
                val equipment = e.list("equipment")
                val region = ExerciseBucket.from(muscles, e.text("movement"), muscleGroups)?.region() ?: ""
                val muscleLabels = muscles.map(ExerciseSearch::muscleLabel)
                val aliases = e.list("aliases")
                cards += ExerciseCard(
                    id = id,
                    name = name,
                    muscleGroup = muscleGroups.firstOrNull() ?: "",
                    equipment = equipment.firstOrNull() ?: "",
                    cableConfig = when (e.text("sidedness")?.lowercase()) {
                        "unilateral" -> "SINGLE"
                        "alternating" -> "EITHER"
                        else -> "DOUBLE"
                    },
                    popularity = (e["popularity"] as? JsonPrimitive)?.floatOrNull ?: 0f,
                    region = region,
                    muscles = muscleLabels,
                    description = ExerciseSearch.describe(name, muscleLabels, equipment.map(ExerciseSearch::equipmentLabel), region) +
                        aliases.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = " Also called ", postfix = ".").orEmpty()
                )
                // The short angle clips loop like a GIF, as in the app.
                val videos = (e["videos"] as? JsonArray)?.map { it.jsonObject }.orEmpty()
                val clip = videos.firstOrNull { it.text("angle") == "FRONT" } ?: videos.firstOrNull()
                guides[id] = ExerciseGuide(
                    description = e.text("description").orEmpty(),
                    videoUrl = clip?.text("video"),
                    thumbnailUrl = clip?.text("thumbnail")
                )
            }
            return ExerciseLibrary(cards, guides)
        }

        private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
        private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
        private fun JsonObject.list(key: String): List<String> =
            (this[key] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()

        /** Same grouping as CleanUiBridge (posterior chain counts as back). */
        private fun ExerciseBucket.region(): String = when (this) {
            ExerciseBucket.CHEST -> "Chest"
            ExerciseBucket.BACK, ExerciseBucket.POSTERIOR_CHAIN -> "Back"
            ExerciseBucket.SHOULDERS -> "Shoulders"
            ExerciseBucket.BICEPS, ExerciseBucket.TRICEPS -> "Arms"
            ExerciseBucket.QUADS, ExerciseBucket.HAMSTRINGS_GLUTES, ExerciseBucket.CALVES -> "Legs"
            ExerciseBucket.CORE -> "Core"
        }
    }
}
