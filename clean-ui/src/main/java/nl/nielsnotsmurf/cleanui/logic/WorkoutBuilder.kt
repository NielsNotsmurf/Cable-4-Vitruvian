package nl.nielsnotsmurf.cleanui.logic

import nl.nielsnotsmurf.cleanui.model.ExerciseCard
import kotlin.math.roundToInt

/**
 * Builds a workout from muscle groups and a time budget: picks the exercises, keeps
 * exercises on the same equipment next to each other, and offers a swap on other equipment.
 */
object WorkoutBuilder {
    const val SETS = 3
    const val WORK_SECONDS = 45
    const val REST_SECONDS = 90
    /** Moving to the next exercise; a little more when the equipment changes. */
    const val SWITCH_SECONDS = 60
    const val MAX_EXERCISES = 12
    val DURATIONS = listOf(15, 30, 45, 60, 75, 90)

    /** Minutes one exercise takes when every lifter does [SETS] sets in turn. */
    fun minutesPerExercise(lifters: Int, sets: Int = SETS): Float {
        val turns = sets.coerceAtLeast(1) * lifters.coerceAtLeast(1)
        return (turns * (WORK_SECONDS + REST_SECONDS) + SWITCH_SECONDS) / 60f
    }

    /** How many exercises fit in [minutes]; at least one per chosen group. */
    fun exerciseCount(minutes: Int, lifters: Int, groups: Int, sets: Int = SETS): Int {
        val fit = (minutes / minutesPerExercise(lifters, sets)).roundToInt()
        return maxOf(groups, fit, 1).coerceAtMost(maxOf(MAX_EXERCISES, groups))
    }

    /** Rough length of a workout with [exercises] exercises, in minutes. */
    fun estimatedMinutes(exercises: Int, lifters: Int, sets: Int = SETS): Int =
        (exercises * minutesPerExercise(lifters, sets)).roundToInt()

    /**
     * Picks [count] exercises, taking turns over [groups] so each group gets its share. Within a
     * group the best [score] goes first, preferring muscles not trained yet (biceps, then triceps).
     * Bodyweight exercises are left out: they have no 1RM to load from.
     */
    fun build(
        pool: List<ExerciseCard>,
        groups: List<String>,
        count: Int,
        score: (ExerciseCard) -> Float
    ): List<ExerciseCard> {
        if (groups.isEmpty()) return emptyList()
        val ranked = groups.associateWith { g ->
            pool.filter { it.region == g && !it.isBodyweight }.sortedByDescending(score)
        }
        val picked = mutableListOf<ExerciseCard>()
        var turn = 0
        var misses = 0
        while (picked.size < count && misses < groups.size) {
            val group = groups[turn++ % groups.size]
            val next = nextFor(ranked[group].orEmpty(), picked)
            if (next == null) misses++ else { misses = 0; picked += next }
        }
        return orderByEquipment(picked)
    }

    private fun nextFor(ranked: List<ExerciseCard>, picked: List<ExerciseCard>): ExerciseCard? {
        val ids = picked.map { it.id }.toSet()
        val muscles = picked.map { it.muscleGroup }.toSet()
        val free = ranked.filter { it.id !in ids && it.name !in picked.map { p -> p.name } }
        return free.firstOrNull { it.muscleGroup !in muscles } ?: free.firstOrNull()
    }

    /**
     * Same equipment back to back, in order of first use, so the bar or handles only
     * change when they have to. Order within one piece of equipment is kept.
     */
    fun orderByEquipment(exercises: List<ExerciseCard>): List<ExerciseCard> {
        val order = exercises.map { it.equipment.lowercase() }.distinct()
        return exercises.sortedBy { order.indexOf(it.equipment.lowercase()) }
    }

    /** Equipment changes in [exercises], as done in this order. */
    fun equipmentSwitches(exercises: List<ExerciseCard>): Int =
        exercises.zipWithNext().count { (a, b) -> !a.equipment.equals(b.equipment, ignoreCase = true) }

    /**
     * A swap for [current]: same group (same muscle first), on different equipment, not
     * already in [workout]. Falls back to the same equipment when the group has nothing else.
     */
    fun substitute(
        current: ExerciseCard,
        workout: List<ExerciseCard>,
        pool: List<ExerciseCard>,
        score: (ExerciseCard) -> Float
    ): ExerciseCard? {
        val taken = workout.map { it.id }.toSet()
        val candidates = pool.filter { it.region == current.region && !it.isBodyweight && it.id !in taken }
        val other = candidates.filter { !it.equipment.equals(current.equipment, ignoreCase = true) }
        val from = other.ifEmpty { candidates }
        return from.sortedWith(
            compareByDescending<ExerciseCard> { it.muscleGroup == current.muscleGroup }.thenByDescending(score)
        ).firstOrNull()
    }

    /** Replaces [old] with [new] and puts the workout back in equipment order. */
    fun swap(workout: List<ExerciseCard>, old: ExerciseCard, new: ExerciseCard): List<ExerciseCard> =
        orderByEquipment(workout.map { if (it.id == old.id) new else it })
}
