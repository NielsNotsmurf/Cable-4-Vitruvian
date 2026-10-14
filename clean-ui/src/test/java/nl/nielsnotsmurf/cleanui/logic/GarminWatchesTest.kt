package nl.nielsnotsmurf.cleanui.logic

import nl.nielsnotsmurf.cleanui.logic.GarminWatches.Watch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GarminWatchesTest {

    private val venu = Watch(1L, "Venu 4")
    private val fenix = Watch(2L, "fēnix 8")

    @Test
    fun `the profile whose heart rate sensor is the watch gets it`() {
        val sensors = mapOf("niels" to "Venu4", "anna" to null)
        assertEquals(venu, GarminWatches.watchFor("niels", listOf(venu), emptyMap(), sensors))
        assertNull(GarminWatches.watchFor("anna", listOf(venu), emptyMap(), sensors))
    }

    @Test
    fun `a chosen watch wins and is never given to someone else`() {
        val sensors = mapOf("niels" to "Venu 4", "anna" to null)
        val chosen = mapOf("anna" to 1L)
        assertEquals(venu, GarminWatches.watchFor("anna", listOf(venu), chosen, sensors))
        assertNull(GarminWatches.watchFor("niels", listOf(venu), chosen, sensors))
    }

    @Test
    fun `opting out sends to no watch`() {
        val sensors = mapOf("niels" to "Venu 4")
        val chosen = mapOf("niels" to GarminWatches.NO_WATCH)
        assertNull(GarminWatches.watchFor("niels", listOf(venu), chosen, sensors))
    }

    @Test
    fun `a single profile with a single watch belongs together`() {
        assertEquals(venu, GarminWatches.watchFor("niels", listOf(venu), emptyMap(), mapOf("niels" to null)))
        assertNull(GarminWatches.watchFor("niels", listOf(venu, fenix), emptyMap(), mapOf("niels" to null)))
        assertNull(GarminWatches.watchFor("niels", listOf(venu), emptyMap(), mapOf("niels" to null, "anna" to null)))
    }

    @Test
    fun `exercises map to Garmin categories, unknown ones stay generic`() {
        assertEquals(0, GarminWatches.categoryFor("Bench Press"))
        assertEquals(15, GarminWatches.categoryFor("Lying Leg Curl"))
        assertEquals(7, GarminWatches.categoryFor("Bicep Curl"))
        assertEquals(23, GarminWatches.categoryFor("Bent Over Row"))
        assertEquals(0, GarminWatches.categoryFor("Narrow Grip Bench Press"))
        assertEquals(28, GarminWatches.categoryFor("Front Squat"))
        assertEquals(8, GarminWatches.categoryFor("Romanian Deadlift"))
        assertEquals(24, GarminWatches.categoryFor("Overhead Press"))
        assertEquals(14, GarminWatches.categoryFor("Reverse Fly"))
        assertEquals(9, GarminWatches.categoryFor("Chest Fly"))
        assertEquals(GarminWatches.UNKNOWN_CATEGORY, GarminWatches.categoryFor("Just Lift"))
    }
}
