package nl.nielsnotsmurf.cleanui.platform

import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun formatDate(millis: Long, pattern: String): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))

actual fun formatDecimal(value: Double, decimals: Int, signed: Boolean): String =
    (if (signed) "%+.${decimals}f" else "%.${decimals}f").format(value)

actual fun formatGrouped(value: Long): String = "%,d".format(value)

private val random = SecureRandom()

internal actual fun secureRandomBytes(count: Int): ByteArray = ByteArray(count).also(random::nextBytes)

internal actual fun pbkdf2Sha256(password: String, salt: ByteArray, iterations: Int, keyBits: Int): ByteArray {
    val spec = PBEKeySpec(password.toCharArray(), salt, iterations, keyBits)
    return try {
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    } finally {
        spec.clearPassword()
    }
}

actual class DayCursor actual constructor(millis: Long, private val zoneId: String?) {
    private val cal: Calendar = Calendar.getInstance(zoneId?.let(TimeZone::getTimeZone) ?: TimeZone.getDefault()).apply {
        firstDayOfWeek = Calendar.MONDAY
        timeInMillis = millis
    }

    actual var millis: Long
        get() = cal.timeInMillis
        set(value) { cal.timeInMillis = value }

    actual fun startOfDay() {
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
    }

    actual fun addDays(n: Int) = cal.add(Calendar.DAY_OF_MONTH, n)
    actual fun addMonths(n: Int) = cal.add(Calendar.MONTH, n)
    actual fun addYears(n: Int) = cal.add(Calendar.YEAR, n)
    actual fun setDayOfMonth(day: Int) = cal.set(Calendar.DAY_OF_MONTH, day)
    actual fun setDayOfYear(day: Int) = cal.set(Calendar.DAY_OF_YEAR, day)
    actual val dayOfWeek: Int get() = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
    actual val dayOfMonth: Int get() = cal.get(Calendar.DAY_OF_MONTH)
    actual val month: Int get() = cal.get(Calendar.MONTH)
    actual val year: Int get() = cal.get(Calendar.YEAR)
    actual fun copy(): DayCursor = DayCursor(millis, zoneId)
}
