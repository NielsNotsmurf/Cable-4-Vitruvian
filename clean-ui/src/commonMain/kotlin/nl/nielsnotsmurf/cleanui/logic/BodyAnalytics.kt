package nl.nielsnotsmurf.cleanui.logic

import nl.nielsnotsmurf.cleanui.model.ExerciseCard
import nl.nielsnotsmurf.cleanui.model.HistoryEntry
import nl.nielsnotsmurf.cleanui.platform.DayCursor
import kotlin.math.roundToInt

/** What the Analytics screen measures. Loads are kg per cable. */
enum class Measure(val label: String) {
    REPS("Reps"),
    SETS("Sets"),
    VOLUME("Volume"),
    HEAVIEST("Heaviest"),
    WORKOUTS("Workouts"),
    TIME("Time")
}

/** The period the Analytics screen looks at. Week, month and year are calendar periods; weeks start on Monday. */
enum class Window(val label: String, val shortLabel: String, val now: String, val before: String) {
    WEEK("This week", "Week", "this week", "last week"),
    MONTH("This month", "Month", "this month", "last month"),
    QUARTER("3 months", "3 months", "these 3 months", "the 3 months before"),
    YEAR("This year", "Year", "this year", "last year"),
    ALL("All time", "All", "so far", "before")
}

/** Numbers for the Analytics screen: everything is cut by one [Measure] and one [Window]. */
object BodyAnalytics {
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    /**
     * A calendar period: [start] to [end] (the end of the period, which can be in the future).
     * The period before is cut to the same elapsed time as this one ([previousStart] to [previousEnd]),
     * so "up 20%" on a Wednesday compares Monday–Wednesday with Monday–Wednesday.
     */
    data class Range(val start: Long, val end: Long, val previousStart: Long?, val previousEnd: Long?) {
        operator fun contains(t: Long) = t in start until end
    }

    /** One body region in the window: its [value] for the measure and its heaviest load. */
    data class RegionStat(
        val region: String,
        val value: Float,
        val bestKg: Float?,
        val bestExercise: String?,
        /** Share of the window's total for additive measures, 0–1. */
        val share: Float
    )

    /** One bar of the trend chart. */
    data class Bucket(val start: Long, val label: String, val value: Float)

    data class ExerciseStat(val name: String, val region: String, val value: Float, val bestKg: Float?, val points: List<Float>)

    data class Insight(val title: String, val body: String)

    /** [zone] is a time zone id; null is the device's own. */
    fun range(window: Window, now: Long, zone: String? = null, first: Long? = null): Range {
        val cal = DayCursor(now, zone).apply { startOfDay() }
        val tomorrow = cal.copy().apply { addDays(1) }.millis
        fun shifted(unit: Step, amount: Int): Long = cal.copy().apply { add(unit, amount) }.millis
        fun period(unit: Step, length: Int): Range {
            val start = cal.millis
            val previousStart = shifted(unit, -length)
            return Range(start, shifted(unit, length), previousStart, previousStart + (tomorrow - start))
        }
        return when (window) {
            Window.WEEK -> {
                while (cal.dayOfWeek != 1) cal.addDays(-1)
                period(Step.WEEK, 1)
            }
            Window.MONTH -> {
                cal.setDayOfMonth(1)
                period(Step.MONTH, 1)
            }
            Window.QUARTER -> {
                cal.setDayOfMonth(1)
                cal.addMonths(-2)
                period(Step.MONTH, 3)
            }
            Window.YEAR -> {
                cal.setDayOfYear(1)
                period(Step.YEAR, 1)
            }
            Window.ALL -> {
                cal.millis = minOf(first ?: now, now)
                cal.startOfDay()
                Range(cal.millis, tomorrow, null, null)
            }
        }
    }

    private enum class Step { DAY, WEEK, MONTH, YEAR }

    private fun DayCursor.add(step: Step, amount: Int) = when (step) {
        Step.DAY -> addDays(amount)
        Step.WEEK -> addDays(7 * amount)
        Step.MONTH -> addMonths(amount)
        Step.YEAR -> addYears(amount)
    }

    fun regionLookup(exercises: List<ExerciseCard>): (HistoryEntry) -> String {
        val byId = exercises.associate { it.id to it.region }
        val byName = exercises.associate { it.name.lowercase() to it.region }
        return { e -> byId[e.exerciseId]?.takeIf { it.isNotEmpty() } ?: byName[e.exerciseName.lowercase()].orEmpty() }
    }

    /** True when values of the measure add up (so shares and "up x%" make sense). */
    fun isAdditive(measure: Measure) = measure != Measure.HEAVIEST

    fun value(entries: List<HistoryEntry>, measure: Measure): Float = when (measure) {
        Measure.REPS -> entries.sumOf { it.reps }.toFloat()
        Measure.SETS -> entries.sumOf { it.sets }.toFloat()
        Measure.VOLUME -> entries.sumOf { ((it.topKgPerCable ?: 0f) * it.reps).toDouble() }.toFloat()
        Measure.HEAVIEST -> entries.mapNotNull { it.topKgPerCable }.maxOrNull() ?: 0f
        Measure.WORKOUTS -> entries.map { it.exerciseName to it.timestamp / DAY_MS }.distinct().size.toFloat()
        Measure.TIME -> entries.sumOf { it.durationMs } / 60_000f
    }

    fun inRange(entries: List<HistoryEntry>, range: Range) = entries.filter { it.timestamp in range }

    fun previous(entries: List<HistoryEntry>, range: Range): List<HistoryEntry>? {
        val from = range.previousStart ?: return null
        val to = range.previousEnd ?: return null
        return entries.filter { it.timestamp >= from && it.timestamp < to }
    }

    /** Every region in [regions] order, also the ones with nothing in the window. */
    fun regions(
        entries: List<HistoryEntry>,
        regionOf: (HistoryEntry) -> String,
        measure: Measure,
        regions: List<String>
    ): List<RegionStat> {
        val grouped = entries.groupBy(regionOf)
        val total = value(entries.filter { regionOf(it) in regions }, measure)
        return regions.map { r ->
            val list = grouped[r].orEmpty()
            val best = list.filter { it.topKgPerCable != null }.maxByOrNull { it.topKgPerCable!! }
            val v = value(list, measure)
            RegionStat(r, v, best?.topKgPerCable, best?.exerciseName, if (total > 0f && isAdditive(measure)) v / total else 0f)
        }
    }

    /** Bars for the window: days of the week, weeks of a month or quarter, months of a year or all time. */
    fun trend(
        entries: List<HistoryEntry>,
        measure: Measure,
        window: Window,
        range: Range,
        zone: String? = null
    ): List<Bucket> {
        val cal = DayCursor(range.start, zone).apply { startOfDay() }
        val (field, maxBuckets) = when (window) {
            Window.WEEK -> Step.DAY to 7
            Window.MONTH, Window.QUARTER -> Step.WEEK to 14
            Window.YEAR -> Step.MONTH to 12
            Window.ALL -> Step.MONTH to 240
        }
        if (field == Step.WEEK) {
            while (cal.dayOfWeek != 1) cal.addDays(-1)
        }
        if (field == Step.MONTH) cal.setDayOfMonth(1)
        val starts = mutableListOf<Long>()
        while (cal.millis < range.end && starts.size < maxBuckets) {
            starts += cal.millis
            cal.add(field, 1)
        }
        val ends = starts.drop(1) + range.end
        // The first week of a month can start before the month does; only count the window.
        fun count(from: Long, to: Long) = value(entries.filter { it.timestamp >= maxOf(from, range.start) && it.timestamp < to }, measure)
        val days = arrayOf("M", "T", "W", "T", "F", "S", "S")
        val months = arrayOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
        val label = DayCursor(0L, zone)
        var buckets = starts.indices.map { i ->
            label.millis = starts[i]
            val text = when (field) {
                Step.DAY -> days[label.dayOfWeek - 1]
                Step.MONTH -> months[label.month]
                else -> label.dayOfMonth.toString()
            }
            Bucket(starts[i], text, count(starts[i], ends[i]))
        }
        // All time: whole years once there are more than two years of months.
        if (window == Window.ALL && buckets.size > 24) {
            buckets = buckets.groupBy { label.millis = it.start; label.year }
                .map { (year, list) ->
                    val from = list.first().start
                    val to = ends[starts.indexOf(list.last().start)]
                    Bucket(from, "’" + (year % 100).toString().padStart(2, '0'), count(from, to))
                }
        }
        return buckets
    }

    /** Most trained exercises in the window, with their heaviest load per day for a sparkline. */
    fun exercises(entries: List<HistoryEntry>, regionOf: (HistoryEntry) -> String, measure: Measure, limit: Int = 8): List<ExerciseStat> =
        entries.groupBy { it.exerciseName }
            .map { (name, list) ->
                val points = list.filter { it.topKgPerCable != null }
                    .groupBy { it.timestamp / DAY_MS }.entries.sortedBy { it.key }.map { it.value }
                    .map { day -> day.maxOf { it.topKgPerCable!! } }
                ExerciseStat(name, regionOf(list.first()), value(list, measure), list.mapNotNull { it.topKgPerCable }.maxOrNull(), points)
            }
            .sortedByDescending { it.value }
            .take(limit)

    /** Change against the previous window in percent, or null when there is nothing to compare. */
    fun change(current: Float, previous: Float?): Int? =
        if (previous == null || previous <= 0f) null else ((current - previous) / previous * 100f).roundToInt()

    /** Records set inside the range: loads that beat everything before them on that exercise. */
    fun records(all: List<HistoryEntry>, range: Range): List<TrainingStats.Record> =
        TrainingStats.records(all, limit = Int.MAX_VALUE).filter { it.timestamp in range }

    /**
     * Plain-language findings for the window, most useful first. [format] writes a measure value
     * with its unit, [kg] a load.
     */
    fun insights(
        all: List<HistoryEntry>,
        regionOf: (HistoryEntry) -> String,
        measure: Measure,
        window: Window,
        range: Range,
        regionNames: List<String>,
        now: Long,
        format: (Float) -> String,
        kg: (Float) -> String
    ): List<Insight> {
        val inWindow = inRange(all, range)
        val out = mutableListOf<Insight>()
        val name = measure.label.lowercase()
        if (inWindow.isEmpty()) {
            val last = all.maxByOrNull { it.timestamp }
            out += Insight(
                "Nothing yet ${window.now}",
                if (last == null) "Finish a set and it shows up here." else "Your last workout was ${daysAgo(last.timestamp, now)}: ${last.exerciseName}."
            )
            return out
        }
        val current = value(inWindow, measure)
        val prevList = previous(all, range)
        val prev = prevList?.let { value(it, measure) }
        change(current, prev)?.let { pct ->
            out += when {
                pct > 0 -> Insight("${measure.label} up $pct%", "${format(current)} ${window.now} against ${format(prev!!)} at the same point ${window.before}.")
                pct < 0 -> Insight("${measure.label} down ${-pct}%", "${format(current)} ${window.now} against ${format(prev!!)} at the same point ${window.before}.")
                else -> Insight("${measure.label} level", "${format(current)}, the same as at this point ${window.before}.")
            }
        }
        val stats = regions(inWindow, regionOf, measure, regionNames)
        val trained = stats.filter { it.value > 0f }
        if (isAdditive(measure)) {
            trained.maxByOrNull { it.share }?.let { top ->
                out += Insight("Most $name: ${top.region.lowercase()}", "${(top.share * 100).roundToInt()}% of your $name ${window.now} went to ${top.region.lowercase()}.")
            }
        } else {
            trained.maxByOrNull { it.value }?.let { top ->
                out += Insight("Heaviest: ${top.region.lowercase()}", "${kg(top.value)} on ${top.bestExercise ?: top.region}.")
            }
        }
        val skipped = stats.filter { it.value == 0f }.map { it.region }
        if (skipped.isNotEmpty() && trained.isNotEmpty()) {
            val before = prevList.orEmpty().map(regionOf).toSet()
            val pick = skipped.firstOrNull { it in before } ?: skipped.first()
            out += Insight(
                "No ${pick.lowercase()} ${window.now}",
                if (pick in before) "You trained it ${window.before}." else "Nothing for ${skipped.joinToString { it.lowercase() }} so far."
            )
        }
        val push = stats.filter { it.region == "Chest" || it.region == "Shoulders" }.sumOf { it.value.toDouble() }.toFloat()
        val pull = stats.firstOrNull { it.region == "Back" }?.value ?: 0f
        if (isAdditive(measure) && push > 0f && pull > 0f) {
            val pushPct = (push / (push + pull) * 100).roundToInt()
            out += Insight(
                "Push $pushPct · pull ${100 - pushPct}",
                when {
                    pushPct >= 60 -> "Chest and shoulders get more than your back. Some extra rows keep the shoulders healthy."
                    pushPct <= 40 -> "Your back gets more than chest and shoulders."
                    else -> "Chest and shoulders against back is well balanced."
                }
            )
        }
        val recs = records(all, range)
        if (recs.isNotEmpty()) {
            val latest = recs.first()
            out += Insight(
                if (recs.size == 1) "1 new record" else "${recs.size} new records",
                "Latest: ${latest.exerciseName}, ${kg(latest.kg)} ${daysAgo(latest.timestamp, now)}."
            )
        }
        if (window != Window.WEEK) {
            val byDay = inWindow.groupBy {
                // Sunday = 1 ... Saturday = 7, the order of DAY_NAMES.
                DayCursor(it.timestamp).dayOfWeek % 7 + 1
            }.mapValues { (_, l) -> l.map { it.timestamp / DAY_MS }.distinct().size }
            byDay.maxByOrNull { it.value }?.takeIf { byDay.size > 1 }?.let { (day, n) ->
                out += Insight("${DAY_NAMES[day - 1]}s are your day", "You trained on $n ${DAY_NAMES[day - 1]}${if (n == 1) "" else "s"} ${window.now}.")
            }
        }
        val streak = TrainingStats.weekStreak(all, now)
        if (streak >= 2) out += Insight("$streak weeks in a row", "At least one workout every week. Keep it going.")
        return out
    }

    private val DAY_NAMES = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    private fun daysAgo(t: Long, now: Long): String = when (val d = ((now - t) / DAY_MS).toInt()) {
        0 -> "today"
        1 -> "yesterday"
        else -> "$d days ago"
    }
}
