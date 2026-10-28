package nl.nielsnotsmurf.cleanui.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class CryptoTest {
    private fun ByteArray.hex() = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    // Known PBKDF2-HMAC-SHA256 vectors: every platform must derive the same PIN hash.
    @Test
    fun pbkdf2MatchesTheStandardVectors() {
        assertEquals(
            "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
            pbkdf2Sha256("password", "salt".encodeToByteArray(), 1, 256).hex()
        )
        assertEquals(
            "c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a",
            pbkdf2Sha256("password", "salt".encodeToByteArray(), 4096, 256).hex()
        )
    }

    @Test
    fun randomBytesHaveTheRequestedLength() {
        assertEquals(16, secureRandomBytes(16).size)
    }
}
