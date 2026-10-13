package nl.nielsnotsmurf.cleanui.logic

import nl.nielsnotsmurf.cleanui.model.HistoryEntry
import nl.nielsnotsmurf.cleanui.model.OneRepMax
import nl.nielsnotsmurf.cleanui.model.OneRepMaxSource

/** Numbers for the Train and Analytics tabs, worked out from history. */
object TrainingStats {
    private const val DAY_MS = 24 * 60 * 60 * 1000L
    private const val WEEK_MS = 7 * DAY_MS

    /** Sets less than this apart belong to the same training. */
    const val SESSION_GAP_MS = 5 * 60 * 60 * 1000L

    /** A lifter's 1RM on one exercise, with when it was set. */
    data class StandingRecord(val lifterId: String, val exerciseId: String, val oneRepMax: OneRepMax)

    /**
     * The training going on now, oldest first: the latest exercise if it ended less than
     * [gapMs] before [now], plus every exercise before it with no gap of [gapMs] or more.
     * Empty when nothing was done in the last [gapMs].
     */
    fun currentSession(entries: List<HistoryEntry>, now: Long, gapMs: Long = SESSION_GAP_MS): List<HistoryEntry> {
        val newestFirst = entries.sortedByDescending { it.timestamp }
        val latest = newestFirst.firstOrNull() ?: return emptyList()
        if (now - latest.timestamp >= gapMs) return emptyList()
        val session = mutableListOf(latest)
        for (e in newestFirst.drop(1)) {
            if (session.last().timestamp - e.timestamp >= gapMs) break
            session += e
        }
        return session.reversed()
    }

    /**
     * The [limit] 1RMs of [lifterIds] that were set longest ago, oldest first: the ones most worth
     * re-testing. Related-exercise estimates and 1RMs without a date are left out.
     */
    fun longestStanding(
        oneRepMaxes: Map<String, Map<String, OneRepMax>>,
        lifterIds: List<String>,
        limit: Int = 3
    ): List<StandingRecord> =
        lifterIds.flatMap { id ->
            oneRepMaxes[id].orEmpty().map { (exId, orm) -> StandingRecord(id, exId, orm) }
        }
            .filter { it.oneRepMax.source != OneRepMaxSource.RELATED && it.oneRepMax.updatedAt > 0L }
            .sortedBy { it.oneRepMax.updatedAt }
            .take(limit)

    data class Totals(val workouts: Int, val sets: Int, val reps: Int)

    /** One exercise's heaviest load per day, oldest first. */
    data class Progress(val exerciseName: String, val points: List<Pair<Long, Float>>) {
        val best: Float get() = points.maxOf { it.second }
        val first: Float get() = points.first().second
        val last: Float get() = points.last().second
    }

    data class Record(val exerciseName: String, val kg: Float, val timestamp: Long)

    /** Workouts, sets and reps from [since] on. A workout is one exercise on one day. */
    fun totals(entries: List<HistoryEntry>, since: Long): Totals {
        val recent = entries.filter { it.timestamp >= since }
        val workouts = recent.map { it.exerciseName to it.timestamp / DAY_MS }.distinct().size
        return Totals(workouts, recent.sumOf { it.sets }, recent.sumOf { it.reps })
    }

    /**
     * Weeks in a row with at least one workout, counting back from the week of [now].
     * The current week counts once it has a workout, and doesn't break the streak before then.
     */
    fun weekStreak(entries: List<HistoryEntry>, now: Long): Int {
        val weeks = entries.map { (now - it.timestamp).coerceAtLeast(0) / WEEK_MS }.toSet()
        var week = if (0L in weeks) 0L else 1L
        var streak = 0
        while (week in weeks) {
            streak++
            week++
        }
        return streak
    }

    /** The [limit] most trained loaded exercises, each with its heaviest load per day. */
    fun progress(entries: List<HistoryEntry>, limit: Int = 5): List<Progress> =
        entries.filter { it.topKgPerCable != null }
            .groupBy { it.exerciseName }
            .entries.sortedByDescending { it.value.size }
            .take(limit)
            .map { (name, list) ->
                val points = list.groupBy { it.timestamp / DAY_MS }
                    .map { (_, day) -> day.minOf { it.timestamp } to day.maxOf { it.topKgPerCable!! } }
                    .sortedBy { it.first }
                Progress(name, points)
            }

    /** Times a load beat everything before it on that exercise, newest first. The first session isn't a record. */
    fun records(entries: List<HistoryEntry>, limit: Int = 5): List<Record> {
        val out = mutableListOf<Record>()
        entries.filter { it.topKgPerCable != null }.groupBy { it.exerciseName }.forEach { (name, list) ->
            var best: Float? = null
            list.sortedBy { it.timestamp }.forEach { e ->
                val kg = e.topKgPerCable!!
                val previous = best
                if (previous != null && kg > previous) out += Record(name, kg, e.timestamp)
                if (previous == null || kg > previous) best = kg
            }
        }
        return out.sortedByDescending { it.timestamp }.take(limit)
    }
}
