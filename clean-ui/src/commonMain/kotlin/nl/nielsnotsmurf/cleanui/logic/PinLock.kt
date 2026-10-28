package nl.nielsnotsmurf.cleanui.logic

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import nl.nielsnotsmurf.cleanui.platform.pbkdf2Sha256
import nl.nielsnotsmurf.cleanui.platform.secureRandomBytes

/**
 * Account PINs. Only a salted PBKDF2 hash is stored, never the PIN itself.
 *
 * This is a privacy lock between people who share the phone, not encryption: the data
 * behind it stays readable to anyone with the device unlocked and a file browser.
 */
@OptIn(ExperimentalEncodingApi::class)
object PinLock {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 6

    /** Wrong tries in a row before [lockoutSeconds] starts counting. */
    const val FREE_TRIES = 5

    private const val VERSION = "v1"
    private const val ITERATIONS = 20_000
    private const val KEY_BITS = 256

    fun isValid(pin: String): Boolean = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    /** "v1:iterations:salt:hash", with salt and hash in Base64. */
    fun hash(pin: String): String {
        require(isValid(pin)) { "A PIN is $MIN_LENGTH to $MAX_LENGTH digits" }
        val salt = secureRandomBytes(16)
        return encode(ITERATIONS, salt, derive(pin, salt, ITERATIONS))
    }

    fun verify(pin: String, stored: String): Boolean {
        val parts = stored.split(':')
        if (parts.size != 4 || parts[0] != VERSION) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = runCatching { Base64.decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { Base64.decode(parts[3]) }.getOrNull() ?: return false
        if (!isValid(pin)) return false
        val actual = derive(pin, salt, iterations)
        // Constant time, so the comparison doesn't leak how many bytes matched.
        var diff = actual.size xor expected.size
        for (i in 0 until minOf(actual.size, expected.size)) diff = diff or (actual[i].toInt() xor expected[i].toInt())
        return diff == 0
    }

    /** Seconds to wait after [wrongTries] wrong PINs in a row: 0 for the first few, then 30 s doubling up to 5 min. */
    fun lockoutSeconds(wrongTries: Int): Int {
        if (wrongTries < FREE_TRIES) return 0
        val doublings = (wrongTries - FREE_TRIES).coerceAtMost(4)
        return (30 shl doublings).coerceAtMost(300)
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray =
        pbkdf2Sha256(pin, salt, iterations, KEY_BITS)

    private fun encode(iterations: Int, salt: ByteArray, hash: ByteArray): String {
        return "$VERSION:$iterations:${Base64.encode(salt)}:${Base64.encode(hash)}"
    }
}
