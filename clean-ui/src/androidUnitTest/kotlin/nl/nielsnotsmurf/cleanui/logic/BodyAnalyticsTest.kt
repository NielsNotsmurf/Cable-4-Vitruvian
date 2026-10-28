package nl.nielsnotsmurf.cleanui.logic

import nl.nielsnotsmurf.cleanui.model.ExerciseCard
import nl.nielsnotsmurf.cleanui.model.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyAnalyticsTest {
    private val utc = "UTC"
    private val day = 24 * 60 * 60 * 1000L
    private val hour = 60 * 60 * 1000L

    // Wednesday 2026-10-14, 12:00 UTC.
    private val now = 1_791_979_200_000L
    private val monday = now - 2 * day - 12 * hour

    private val exercises = listOf(
        ExerciseCard("bench", "Bench Press", "Chest", "Bar", region = "Chest"),
        ExerciseCard("row", "Bent Over Row", "Back", "Bar", region = "Back"),
        ExerciseCard("squat", "Squat", "Legs", "Bar", region = "Legs")
    )
    private val regionOf = BodyAnalytics.regionLookup(exercises)
    private val regions = listOf("Chest", "Back", "Shoulders", "Arms", "Legs", "Core")

    private fun entry(id: String, name: String, at: Long, kg: Float?, reps: Int = 20, sets: Int = 2) = HistoryEntry(
        id = "$id-$at", lifterId = "a", exerciseName = name, timestamp = at, mode = "Old School",
        sets = sets, reps = reps, topKgPerCable = kg, durationMs = 10 * 60_000L, groupId = "$id-$at", exerciseId = id
    )

    @Test
    fun weekStartsOnMondayAndComparesWithTheWeekBefore() {
        val r = BodyAnalytics.range(Window.WEEK, now, utc)
        assertEquals(monday, r.start)
        assertEquals(monday + 7 * day, r.end)
        assertEquals(monday - 7 * day, r.previousStart)
        // Last week is cut to Monday–Wednesday, like this week so far.
        assertEquals(monday - 4 * day, r.previousEnd)
        assertNull(BodyAnalytics.range(Window.ALL, now, utc, first = now - 400 * day).previousStart)
    }

    @Test
    fun regionsAddUpPerRegionWithTheHeaviestLoad() {
        val e = listOf(
            entry("bench", "Bench Press", now - hour, 40f),
            entry("bench", "Bench Press", now - 2 * hour, 45f),
            entry("row", "Bent Over Row", now - hour, 50f, reps = 40),
            entry("", "Squat", now - hour, 80f) // no id: found by name
        )
        val stats = BodyAnalytics.regions(e, regionOf, Measure.REPS, regions).associateBy { it.region }
        assertEquals(40f, stats.getValue("Chest").value, 0f)
        assertEquals(45f, stats.getValue("Chest").bestKg!!, 0f)
        assertEquals(40f, stats.getValue("Back").value, 0f)
        assertEquals(80f, stats.getValue("Legs").bestKg!!, 0f)
        assertEquals(0.4f, stats.getValue("Back").share, 0.001f)
        assertEquals(0f, stats.getValue("Core").value, 0f)
        assertNull(stats.getValue("Core").bestKg)
    }

    @Test
    fun weekTrendHasSevenDaysAndOnlyCountsTheWindow() {
        val e = listOf(
            entry("bench", "Bench Press", monday + hour, 40f, reps = 10),
            entry("bench", "Bench Press", now - hour, 40f, reps = 12),
            entry("bench", "Bench Press", monday - day, 40f, reps = 99)
        )
        val r = BodyAnalytics.range(Window.WEEK, now, utc)
        val bars = BodyAnalytics.trend(e, Measure.REPS, Window.WEEK, r, utc)
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), bars.map { it.label })
        assertEquals(listOf(10f, 0f, 12f, 0f, 0f, 0f, 0f), bars.map { it.value })
    }

    @Test
    fun measuresAddUpTheWayTheyRead() {
        val e = listOf(entry("bench", "Bench Press", now, 40f, reps = 10, sets = 2), entry("bench", "Bench Press", now, null, reps = 5, sets = 1))
        assertEquals(15f, BodyAnalytics.value(e, Measure.REPS), 0f)
        assertEquals(3f, BodyAnalytics.value(e, Measure.SETS), 0f)
        assertEquals(400f, BodyAnalytics.value(e, Measure.VOLUME), 0f)
        assertEquals(40f, BodyAnalytics.value(e, Measure.HEAVIEST), 0f)
        assertEquals(1f, BodyAnalytics.value(e, Measure.WORKOUTS), 0f)
        assertEquals(20f, BodyAnalytics.value(e, Measure.TIME), 0f)
        assertEquals(50, BodyAnalytics.change(15f, 10f))
        assertNull(BodyAnalytics.change(15f, 0f))
    }

    @Test
    fun insightsCompareWithLastWeekAndPointOutSkippedRegions() {
        val e = listOf(
            entry("bench", "Bench Press", now - hour, 40f, reps = 30),
            entry("squat", "Squat", now - 8 * day, 80f, reps = 20)
        )
        val r = BodyAnalytics.range(Window.WEEK, now, utc)
        val titles = BodyAnalytics.insights(e, regionOf, Measure.REPS, Window.WEEK, r, regions, now, { "${it.toInt()}" }, { "$it kg" })
            .map { it.title }
        assertTrue(titles.toString(), "Reps up 50%" in titles)
        assertTrue(titles.toString(), "No legs this week" in titles)
    }
}
