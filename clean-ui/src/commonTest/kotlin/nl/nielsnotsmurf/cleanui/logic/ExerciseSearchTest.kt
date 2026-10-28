package nl.nielsnotsmurf.cleanui.logic

import nl.nielsnotsmurf.cleanui.model.ExerciseCard
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class ExerciseSearchTest {

    private val wristCurl = ExerciseCard(
        id = "1", name = "SA Wrist Curl", muscleGroup = "ARMS", equipment = "HANDLES",
        region = "Arms", muscles = listOf("Forearms")
    )

    @Test
    fun findsByMuscleAndSynonym() {
        assertTrue(ExerciseSearch.matches(wristCurl, "forearms"))
        assertTrue(ExerciseSearch.matches(wristCurl, "grip"))
        assertTrue(ExerciseSearch.matches(wristCurl, "wrist handles"))
        assertFalse(ExerciseSearch.matches(wristCurl, "chest"))
    }

    @Test
    fun describesFromMusclesAndEquipment() {
        assertEquals("Works the forearms. Uses handles.", ExerciseSearch.describe("SA Wrist Curl", listOf("Forearms"), listOf("Handles"), "Arms"))
        assertEquals("Upper back", ExerciseSearch.muscleLabel("upper_back"))
    }
}
