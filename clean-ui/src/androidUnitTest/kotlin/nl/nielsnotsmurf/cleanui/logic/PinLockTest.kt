package nl.nielsnotsmurf.cleanui.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinLockTest {

    @Test
    fun `right PIN verifies, wrong PIN does not`() {
        val stored = PinLock.hash("2580")
        assertTrue(PinLock.verify("2580", stored))
        assertFalse(PinLock.verify("2581", stored))
        assertFalse(PinLock.verify("25800", stored))
    }

    @Test
    fun `the PIN itself is never stored and every hash is salted`() {
        val a = PinLock.hash("1234")
        val b = PinLock.hash("1234")
        assertFalse(a.contains("1234"))
        assertNotEquals(a, b)
        assertTrue(PinLock.verify("1234", b))
    }

    @Test
    fun `only 4 to 6 digits are valid`() {
        assertTrue(PinLock.isValid("0000"))
        assertTrue(PinLock.isValid("123456"))
        assertFalse(PinLock.isValid("123"))
        assertFalse(PinLock.isValid("1234567"))
        assertFalse(PinLock.isValid("12a4"))
    }

    @Test
    fun `garbage stored values never verify`() {
        assertFalse(PinLock.verify("1234", ""))
        assertFalse(PinLock.verify("1234", "v1:x:y:z"))
        assertFalse(PinLock.verify("1234", "v2:1:AA==:AA=="))
    }

    @Test
    fun `lockout starts after the free tries and is capped`() {
        assertEquals(0, PinLock.lockoutSeconds(PinLock.FREE_TRIES - 1))
        assertEquals(30, PinLock.lockoutSeconds(PinLock.FREE_TRIES))
        assertEquals(60, PinLock.lockoutSeconds(PinLock.FREE_TRIES + 1))
        assertEquals(300, PinLock.lockoutSeconds(PinLock.FREE_TRIES + 20))
    }
}
