package nl.nielsnotsmurf.cleanui.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PersonalDataTest {
    @Test
    fun webFileGivesItsProfilesAndRemapsTheirValues() {
        val host = """{"lifters":[{"id":"w1","name":"Qiqou","color":5}],"history":[]}"""
        val text = PersonalData.encode(
            mapOf("orm|w1::bench" to 50f, "pin|w1" to "h", "last_account" to "w1", "routines" to """[{"overridesKg":{"w1::bench":40.0}}]""", "default_sets" to 3),
            "web", host, 1L
        )
        val parsed = assertNotNull(PersonalData.decode("﻿" + text))
        assertEquals(listOf("w1" to "Qiqou"), parsed.lifters.map { it.id to it.name })
        val moved = PersonalData.remap(parsed.clean, mapOf("w1" to "a9"))
        assertEquals(50f, moved["orm|a9::bench"])
        assertEquals("h", moved["pin|a9"])
        assertEquals("a9", moved["last_account"])
        assertEquals("""[{"overridesKg":{"a9::bench":40.0}}]""", moved["routines"])
        assertEquals(3, moved["default_sets"])
    }

    @Test
    fun oldAndroidBackupIsRead() {
        val text = """{"version":2,"exportedAt":"x","appVersion":"1.3","data":{"profiles":[{"id":"niels","name":"Niels","colorInt":1,"createdAt":0}]}}"""
        val parsed = assertNotNull(PersonalData.decode(text))
        assertEquals("android", parsed.platform)
        assertEquals("Niels", parsed.lifters.single().name)
    }
}
