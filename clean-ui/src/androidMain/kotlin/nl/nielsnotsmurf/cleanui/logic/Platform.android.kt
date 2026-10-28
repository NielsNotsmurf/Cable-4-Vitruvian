package nl.nielsnotsmurf.cleanui.logic

import java.text.DecimalFormatSymbols

internal actual fun decimalSeparator(): Char = DecimalFormatSymbols.getInstance().decimalSeparator
