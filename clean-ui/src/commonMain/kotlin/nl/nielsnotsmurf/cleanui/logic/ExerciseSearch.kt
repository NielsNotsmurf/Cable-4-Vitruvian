package nl.nielsnotsmurf.cleanui.logic

import nl.nielsnotsmurf.cleanui.model.ExerciseCard

/** Muscle names, descriptions and search for the exercise library. */
object ExerciseSearch {

    /** Library muscle id ("upper_back") to its label ("Upper back"). */
    fun muscleLabel(raw: String): String =
        raw.trim().replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

    /** Equipment id ("SHORT_BAR") to its label ("Short bar"). */
    fun equipmentLabel(raw: String): String = muscleLabel(raw)

    /** Other words people search with for a muscle. */
    private val SYNONYMS = mapOf(
        "Core" to listOf("abs", "abdominals", "stomach"),
        "Obliques" to listOf("abs", "waist"),
        "Shoulders" to listOf("delts", "deltoids"),
        "Lats" to listOf("back", "latissimus"),
        "Upper back" to listOf("back", "rhomboids"),
        "Lower back" to listOf("back", "erectors"),
        "Traps" to listOf("trapezius", "neck"),
        "Forearms" to listOf("forearm", "grip", "wrist"),
        "Biceps" to listOf("arms", "bicep"),
        "Triceps" to listOf("arms", "tricep"),
        "Glutes" to listOf("butt", "bum", "hips"),
        "Quads" to listOf("quadriceps", "thighs", "legs"),
        "Hamstrings" to listOf("hamstring", "legs"),
        "Calves" to listOf("calf", "legs"),
        "Abductors" to listOf("hips", "outer thigh"),
        "Chest" to listOf("pecs", "pectorals")
    )

    /** A short description built from the muscles and equipment, for exercises without one. */
    fun describe(name: String, muscles: List<String>, equipment: List<String>, region: String): String {
        val works = when (muscles.size) {
            0 -> region.takeIf { it.isNotBlank() }?.let { "A ${it.lowercase()} exercise" } ?: name
            1 -> "Works the ${muscles[0].lowercase()}"
            else -> "Works the " + muscles.dropLast(1).joinToString(", ") { it.lowercase() } + " and " + muscles.last().lowercase()
        }
        val with = equipment.filterNot { it.contains("cables", ignoreCase = true) }
        return if (with.isEmpty()) "$works." else "$works. Uses " + with.joinToString(", ") { it.lowercase() } + "."
    }

    /** True when every word of [query] appears in the exercise's name, muscles, region, equipment or description. */
    fun matches(exercise: ExerciseCard, query: String): Boolean {
        val words = query.lowercase().split(' ', ',').filter { it.isNotBlank() }
        if (words.isEmpty()) return true
        val text = buildString {
            append(exercise.name).append(' ')
            append(exercise.region).append(' ')
            append(exercise.muscleGroup).append(' ')
            append(exercise.equipment).append(' ')
            append(exercise.description).append(' ')
            exercise.muscles.forEach { m -> append(m).append(' '); SYNONYMS[m]?.forEach { append(it).append(' ') } }
        }.lowercase()
        return words.all { it in text }
    }
}
