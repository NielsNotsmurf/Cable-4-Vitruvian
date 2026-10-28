package nl.nielsnotsmurf.cleanui.platform

/**
 * PBKDF2-HMAC-SHA256 in plain Kotlin. The browser's WebCrypto version is async only,
 * and PinLock needs the same synchronous call Android's javax.crypto gives.
 */
internal object Pbkdf2 {
    fun derive(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        val key = if (password.size > 64) sha256(password) else password
        val out = ByteArray(length)
        var block = 1
        var pos = 0
        while (pos < length) {
            var u = hmac(key, salt + byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
            val t = u.copyOf()
            repeat(iterations - 1) {
                u = hmac(key, u)
                for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
            }
            t.copyInto(out, pos, 0, minOf(32, length - pos))
            pos += 32
            block++
        }
        return out
    }

    private fun hmac(key: ByteArray, message: ByteArray): ByteArray {
        val k = key.copyOf(64)
        val inner = ByteArray(64) { (k[it].toInt() xor 0x36).toByte() }
        val outer = ByteArray(64) { (k[it].toInt() xor 0x5c).toByte() }
        return sha256(outer + sha256(inner + message))
    }

    private val K = intArrayOf(
        0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b, 0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
        -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
        -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039, -0x391ff40d, -0x2a586eb9, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
        -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d, -0x2e6d17e7, -0x2966f9dc, -0xbf1ca7b, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8, -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e
    )

    fun sha256(message: ByteArray): ByteArray {
        val h = intArrayOf(0x6a09e667, -0x4498517b, 0x3c6ef372, -0x5ab00ac6, 0x510e527f, -0x64fa9774, 0x1f83d9ab, 0x5be0cd19)
        val bitLength = message.size.toLong() * 8
        val padded = ByteArray(((message.size + 9 + 63) / 64) * 64)
        message.copyInto(padded)
        padded[message.size] = 0x80.toByte()
        for (i in 0 until 8) padded[padded.size - 1 - i] = (bitLength ushr (8 * i)).toByte()
        val w = IntArray(64)
        for (chunk in padded.indices step 64) {
            for (i in 0 until 16) {
                val j = chunk + i * 4
                w[i] = (padded[j].toInt() and 0xff shl 24) or (padded[j + 1].toInt() and 0xff shl 16) or
                    (padded[j + 2].toInt() and 0xff shl 8) or (padded[j + 3].toInt() and 0xff)
            }
            for (i in 16 until 64) {
                val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] ushr 3)
                val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }
            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
            var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
            for (i in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = hh + s1 + ch + K[i] + w[i]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj
                hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
            }
            h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh
        }
        return ByteArray(32) { (h[it / 4] ushr (24 - 8 * (it % 4))).toByte() }
    }
}
