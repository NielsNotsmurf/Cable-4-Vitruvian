package nl.nielsnotsmurf.cleanui.logic

private fun jsDecimalSeparator(): String =
    js("(1.5).toLocaleString((typeof navigator !== 'undefined' && navigator.language) || 'en-US').charAt(1)")

// The browser's language decides, as the phone's does on Android.
internal actual fun decimalSeparator(): Char = jsDecimalSeparator().firstOrNull() ?: '.'
