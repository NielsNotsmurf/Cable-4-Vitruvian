package nl.nielsnotsmurf.cleanui.platform

// Browser versions of Platform.kt, on top of the JS Date and Intl APIs.

private fun jsNow(): Double = js("Date.now()")

private fun jsLanguage(): String = js("(typeof navigator !== 'undefined' && navigator.language) || 'en-US'")

private fun jsDatePart(ms: Double, lang: String, option: String, value: String): String =
    js("{ const o = {}; o[option] = value; return new Date(ms).toLocaleString(lang, o); }")

private fun jsFixed(value: Double, lang: String, decimals: Int): String =
    js("value.toLocaleString(lang, { minimumFractionDigits: decimals, maximumFractionDigits: decimals, useGrouping: false })")

private fun jsGrouped(value: Double, lang: String): String = js("value.toLocaleString(lang, { maximumFractionDigits: 0 })")

private fun jsRandomByte(): Int = js("crypto.getRandomValues(new Uint8Array(1))[0]")

/** 0 year, 1 month, 2 date, 3 hours, 4 minutes, 5 seconds, 6 ms, 7 weekday (0 = Sunday). */
private fun jsField(ms: Double, utc: Boolean, field: Int): Int = js(
    """{
        const d = new Date(ms);
        switch (field) {
            case 0: return utc ? d.getUTCFullYear() : d.getFullYear();
            case 1: return utc ? d.getUTCMonth() : d.getMonth();
            case 2: return utc ? d.getUTCDate() : d.getDate();
            case 3: return utc ? d.getUTCHours() : d.getHours();
            case 4: return utc ? d.getUTCMinutes() : d.getMinutes();
            case 5: return utc ? d.getUTCSeconds() : d.getSeconds();
            case 6: return utc ? d.getUTCMilliseconds() : d.getMilliseconds();
            default: return utc ? d.getUTCDay() : d.getDay();
        }
    }"""
)

/** Like the Date constructor: out-of-range parts roll over. */
private fun jsMake(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int, ms: Int, utc: Boolean): Double =
    js("utc ? Date.UTC(y, mo, d, h, mi, s, ms) : new Date(y, mo, d, h, mi, s, ms).getTime()")

actual fun nowMillis(): Long = jsNow().toLong()

actual fun formatDate(millis: Long, pattern: String): String {
    val ms = millis.toDouble()
    val lang = jsLanguage()
    fun field(i: Int) = jsField(ms, false, i)
    fun two(n: Int) = n.toString().padStart(2, '0')
    val out = StringBuilder()
    var i = 0
    while (i < pattern.length) {
        val c = pattern[i]
        var n = 1
        while (i + n < pattern.length && pattern[i + n] == c) n++
        out.append(
            when (c) {
                'E' -> jsDatePart(ms, lang, "weekday", if (n >= 4) "long" else "short")
                'M' -> when {
                    n >= 4 -> jsDatePart(ms, lang, "month", "long")
                    n == 3 -> jsDatePart(ms, lang, "month", "short")
                    n == 2 -> two(field(1) + 1)
                    else -> (field(1) + 1).toString()
                }
                'd' -> if (n >= 2) two(field(2)) else field(2).toString()
                'y' -> if (n == 2) two(field(0) % 100) else field(0).toString()
                'H' -> if (n >= 2) two(field(3)) else field(3).toString()
                'm' -> two(field(4))
                else -> c.toString().repeat(n)
            }
        )
        i += n
    }
    return out.toString()
}

actual fun formatDecimal(value: Double, decimals: Int, signed: Boolean): String {
    val text = jsFixed(value, jsLanguage(), decimals)
    return if (signed && !text.startsWith("-")) "+$text" else text
}

actual fun formatGrouped(value: Long): String = jsGrouped(value.toDouble(), jsLanguage())

internal actual fun secureRandomBytes(count: Int): ByteArray = ByteArray(count) { jsRandomByte().toByte() }

internal actual fun pbkdf2Sha256(password: String, salt: ByteArray, iterations: Int, keyBits: Int): ByteArray =
    Pbkdf2.derive(password.encodeToByteArray(), salt, iterations, keyBits / 8)

actual class DayCursor actual constructor(millis: Long, private val zoneId: String?) {
    // The browser only knows its own zone and UTC; tests use UTC.
    private val utc = zoneId == "UTC"

    actual var millis: Long = millis

    private fun field(i: Int) = jsField(millis.toDouble(), utc, i)

    private fun set(y: Int, mo: Int, d: Int, keepTime: Boolean = true) {
        millis = jsMake(
            y, mo, d,
            if (keepTime) field(3) else 0, if (keepTime) field(4) else 0,
            if (keepTime) field(5) else 0, if (keepTime) field(6) else 0, utc
        ).toLong()
    }

    private fun daysIn(y: Int, mo: Int): Int = jsField(jsMake(y, mo + 1, 0, 12, 0, 0, 0, utc), utc, 2)

    actual fun startOfDay() = set(year, month, dayOfMonth, keepTime = false)
    actual fun addDays(n: Int) = set(year, month, dayOfMonth + n)

    /** Clamps the day like Calendar does: 31 January plus a month is the end of February. */
    actual fun addMonths(n: Int) {
        val total = year * 12 + month + n
        val y = total.floorDiv(12)
        val mo = total.mod(12)
        set(y, mo, minOf(dayOfMonth, daysIn(y, mo)))
    }

    actual fun addYears(n: Int) = set(year + n, month, minOf(dayOfMonth, daysIn(year + n, month)))
    actual fun setDayOfMonth(day: Int) = set(year, month, day)
    actual fun setDayOfYear(day: Int) = set(year, 0, day)
    actual val dayOfWeek: Int get() = (field(7) + 6) % 7 + 1
    actual val dayOfMonth: Int get() = field(2)
    actual val month: Int get() = field(1)
    actual val year: Int get() = field(0)
    actual fun copy(): DayCursor = DayCursor(millis, zoneId)
}
