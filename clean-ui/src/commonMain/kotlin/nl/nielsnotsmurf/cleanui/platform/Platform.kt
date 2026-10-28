package nl.nielsnotsmurf.cleanui.platform

/**
 * The few things Android and the browser do differently. Formatting follows the device's
 * language, exactly as String.format and SimpleDateFormat do on Android.
 */
expect fun nowMillis(): Long

/** [pattern] uses SimpleDateFormat letters: EEEE, EEE, d, dd, MMMM, MMM, MM, M, yyyy, yy, HH, H, mm. */
expect fun formatDate(millis: Long, pattern: String): String

/** Like "%.1f" (or "%+.1f" when [signed]): [decimals] fixed decimals, no grouping. */
expect fun formatDecimal(value: Double, decimals: Int, signed: Boolean = false): String

/** Like "%,d": thousands grouped the way the device's language writes them. */
expect fun formatGrouped(value: Long): String

/** "m:ss", as "%d:%02d". */
fun formatClock(seconds: Long): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

fun formatClock(seconds: Int): String = formatClock(seconds.toLong())

internal expect fun secureRandomBytes(count: Int): ByteArray

internal expect fun pbkdf2Sha256(password: String, salt: ByteArray, iterations: Int, keyBits: Int): ByteArray

/**
 * A date cursor in a time zone ([zoneId]; null is the device's), like java.util.Calendar:
 * moving by days, months and years keeps the wall-clock time. Weeks start on Monday.
 */
expect class DayCursor(millis: Long, zoneId: String? = null) {
    var millis: Long
    fun startOfDay()
    fun addDays(n: Int)
    fun addMonths(n: Int)
    fun addYears(n: Int)
    fun setDayOfMonth(day: Int)
    fun setDayOfYear(day: Int)
    /** 1 = Monday ... 7 = Sunday. */
    val dayOfWeek: Int
    val dayOfMonth: Int
    /** 0 = January. */
    val month: Int
    val year: Int
    fun copy(): DayCursor
}

/** A reusable [formatDate] for one pattern, standing in for SimpleDateFormat. */
class DateFormatter(private val pattern: String) {
    fun format(millis: Long): String = formatDate(millis, pattern)
}
