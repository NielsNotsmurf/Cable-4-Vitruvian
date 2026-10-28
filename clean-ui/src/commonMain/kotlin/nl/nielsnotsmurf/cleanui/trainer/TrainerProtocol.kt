package nl.nielsnotsmurf.cleanui.trainer

/**
 * The Vitruvian trainer's Bluetooth protocol: the packets the app and the web app send, and
 * the parsing of what the trainer sends back. Shared so both platforms speak to the trainer
 * with exactly the same bytes (the app's ProtocolBuilder delegates here).
 */
object TrainerProtocol {
    const val SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
    const val COMMAND_CHAR = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
    /** Cable positions and loads; read by polling, it does not notify. */
    const val SAMPLE_CHAR = "90e991a6-c548-44ed-969b-eb541014eae3"
    const val REPS_CHAR = "8308f2a6-0875-4a94-a86f-5c5c5e1b068a"
    const val DEVICE_NAME_PREFIX = "Vee"

    const val HEART_RATE_SERVICE = "0000180d-0000-1000-8000-00805f9b34fb"
    const val HEART_RATE_CHAR = "00002a37-0000-1000-8000-00805f9b34fb"

    /** Load profile per mode, written at 0x30 of the program frame. */
    enum class Profile { OLD_SCHOOL, PUMP, TUT, TUT_BEAST, ECCENTRIC_ONLY }

    enum class EchoLevel(val gain: Float, val cap: Float) {
        HARD(1.0f, 50.0f), HARDER(1.25f, 40.0f), HARDEST(1.667f, 30.0f), EPIC(3.333f, 15.0f)
    }

    fun init(): ByteArray = byteArrayOf(0x0A, 0x00, 0x00, 0x00)

    fun initPreset(): ByteArray = byteArrayOf(
        0x11, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,
        0xCD.toByte(), 0xCC.toByte(), 0xCC.toByte(), 0x3E.toByte(), // 0.4 as float32 LE
        0xFF.toByte(), 0x00, 0x4C, 0xFF.toByte(),
        0x23, 0x8C.toByte(), 0xFF.toByte(), 0x8C.toByte(),
        0x8C.toByte(), 0xFF.toByte(), 0x00, 0x4C,
        0xFF.toByte(), 0x23, 0x8C.toByte(), 0xFF.toByte(),
        0x8C.toByte(), 0x8C.toByte()
    )

    fun start(): ByteArray = byteArrayOf(0x03, 0x00, 0x00, 0x00)
    fun stop(): ByteArray = byteArrayOf(0x05, 0x00, 0x00, 0x00)
    /** The official app's stop: halts the set but the trainer keeps reporting. */
    fun officialStop(): ByteArray = byteArrayOf(0x50, 0x00)
    /** Init/reset, what the app sends to end a workout. */
    fun reset(): ByteArray = init()

    /**
     * The 96-byte program frame. [unlimited] is Just Lift or AMRAP (no rep target).
     * Weight is per cable; the trainer gets it at 0x58 and again with a 10 kg offset at 0x54.
     */
    fun programParams(
        reps: Int,
        warmupReps: Int,
        unlimited: Boolean,
        profile: Profile,
        weightPerCableKg: Float,
        progressionKg: Float
    ): ByteArray {
        val f = ByteArray(96)
        f[0] = 0x04
        f[0x04] = if (unlimited) 0xFF.toByte() else (reps + warmupReps).toByte()
        f[5] = 0x03
        f[6] = 0x03
        f.putFloat(0x08, 5.0f)
        f.putFloat(0x0c, 5.0f)
        f.putFloat(0x1c, 5.0f)
        for ((at, v) in listOf(0x14 to 0xFA, 0x16 to 0xFA, 0x18 to 0xC8, 0x1a to 0x1E,
            0x24 to 0xFA, 0x26 to 0xFA, 0x28 to 0xC8, 0x2a to 0x1E, 0x2c to 0xFA, 0x2e to 0x50)) {
            f[at] = v.toByte()
        }
        modeProfile(profile).copyInto(f, 0x30)
        val adjusted = if (progressionKg != 0f) weightPerCableKg - progressionKg else weightPerCableKg
        f.putFloat(0x54, adjusted + 10.0f)
        f.putFloat(0x58, adjusted)
        f.putFloat(0x5c, progressionKg)
        return f
    }

    /** The 32-byte Echo frame. */
    fun echoControl(
        level: EchoLevel,
        warmupReps: Int = 3,
        targetReps: Int = 2,
        unlimited: Boolean = false,
        eccentricPct: Int = 75
    ): ByteArray {
        val f = ByteArray(32)
        f.putInt(0x00, 0x4E)
        f[0x04] = warmupReps.toByte()
        f[0x05] = if (unlimited) 0xFF.toByte() else targetReps.toByte()
        f.putShort(0x08, eccentricPct)
        f.putShort(0x0a, 50) // concentric, constant
        f.putFloat(0x0c, 0.1f) // smoothing
        f.putFloat(0x10, level.gain)
        f.putFloat(0x14, level.cap)
        f.putFloat(0x18, 0.0f) // floor
        f.putFloat(0x1c, -100.0f) // negative limit
        return f
    }

    /** LED colours: three RGB colours (0xRRGGBB), sent twice. */
    fun colorScheme(brightness: Float, colors: List<Int>): ByteArray {
        require(colors.size == 3) { "Color scheme must have exactly 3 colors" }
        val f = ByteArray(34)
        f.putInt(0, 0x11)
        f.putFloat(12, brightness)
        var at = 16
        repeat(2) {
            for (c in colors) {
                f[at++] = (c shr 16).toByte()
                f[at++] = (c shr 8).toByte()
                f[at++] = c.toByte()
            }
        }
        return f
    }

    private fun modeProfile(profile: Profile): ByteArray {
        val p = ByteArray(32)
        val v: List<Number> = when (profile) {
            Profile.OLD_SCHOOL -> listOf(0, 20, 3.0f, 75, 600, 50.0f, -1300, -1200, 100.0f, -260, -110, 0.0f)
            Profile.PUMP -> listOf(50, 450, 10.0f, 500, 600, 50.0f, -700, -550, 1.0f, -100, -50, 1.0f)
            Profile.TUT -> listOf(250, 350, 7.0f, 450, 600, 50.0f, -900, -700, 70.0f, -100, -50, 14.0f)
            Profile.TUT_BEAST -> listOf(150, 250, 7.0f, 350, 450, 50.0f, -900, -700, 70.0f, -100, -50, 28.0f)
            Profile.ECCENTRIC_ONLY -> listOf(50, 550, 50.0f, 650, 750, 10.0f, -900, -700, 70.0f, -100, -50, 20.0f)
        }
        // Three groups of (short, short, float) at 0x00, 0x08, 0x10, 0x18.
        for (g in 0 until 4) {
            p.putShort(g * 8, v[g * 3].toInt())
            p.putShort(g * 8 + 2, v[g * 3 + 1].toInt())
            p.putFloat(g * 8 + 4, v[g * 3 + 2].toFloat())
        }
        return p
    }

    /** One reading of both cables. Positions in mm, loads in kg. */
    data class Sample(
        val ticks: Int,
        val positionA: Float,
        val loadA: Float,
        val positionB: Float,
        val loadB: Float,
        val status: Int
    )

    /** Parses the sample characteristic; null when it is too short. */
    fun parseSample(b: ByteArray): Sample? {
        if (b.size < 16) return null
        return Sample(
            ticks = b.u16(0) + (b.u16(2) shl 16),
            positionA = b.s16(4) / 10.0f,
            loadA = b.u16(8) / 100.0f,
            positionB = b.s16(10) / 10.0f,
            loadB = b.u16(14) / 100.0f,
            status = if (b.size >= 18) b.u16(16) else 0
        )
    }

    /** Rep counters. The 24-byte format is the official app's; 6+ bytes is the legacy one. */
    data class Reps(
        val up: Int,
        val down: Int,
        val rangeTop: Float,
        val rangeBottom: Float,
        val romCount: Int,
        val setCount: Int,
        val isLegacy: Boolean
    )

    fun parseReps(b: ByteArray): Reps? = when {
        b.size >= 24 -> Reps(
            up = b.s32(0), down = b.s32(4),
            rangeTop = Float.fromBits(b.s32(8)), rangeBottom = Float.fromBits(b.s32(12)),
            romCount = b.u16(16), setCount = b.u16(20), isLegacy = false
        )
        b.size >= 6 -> Reps(b.u16(0), b.u16(4), 0f, 0f, 0, 0, isLegacy = true)
        else -> null
    }

    /** Heart rate in bpm from the standard Heart Rate Measurement characteristic. */
    fun parseHeartRate(b: ByteArray): Int? {
        if (b.size < 2) return null
        return if (b[0].toInt() and 0x01 != 0) { if (b.size >= 3) b.u16(1) else null } else b[1].toInt() and 0xFF
    }

    private fun ByteArray.putShort(at: Int, v: Int) {
        this[at] = v.toByte()
        this[at + 1] = (v shr 8).toByte()
    }

    private fun ByteArray.putInt(at: Int, v: Int) {
        for (i in 0 until 4) this[at + i] = (v shr (8 * i)).toByte()
    }

    private fun ByteArray.putFloat(at: Int, v: Float) = putInt(at, v.toRawBits())

    private fun ByteArray.u16(at: Int) = (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)
    private fun ByteArray.s16(at: Int) = u16(at).toShort().toInt()
    private fun ByteArray.s32(at: Int) = u16(at) or (u16(at + 2) shl 16)
}
