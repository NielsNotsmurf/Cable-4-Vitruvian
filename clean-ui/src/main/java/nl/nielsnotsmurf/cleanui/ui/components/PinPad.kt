package nl.nielsnotsmurf.cleanui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import nl.nielsnotsmurf.cleanui.sound.clickSounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import nl.nielsnotsmurf.cleanui.logic.PinLock
import nl.nielsnotsmurf.cleanui.theme.CleanColors

/**
 * A number pad for 4 to 6 digit PINs. ✓ submits once 4 digits are in. [clearKey] empties
 * the entry whenever it changes (after a wrong PIN, or between "enter" and "confirm").
 */
@Composable
fun PinPad(
    title: String,
    subtitle: String?,
    error: String?,
    clearKey: Any?,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var pin by remember(clearKey) { mutableStateOf("") }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }
        Row(
            Modifier.padding(vertical = 18.dp).semantics { contentDescription = "${pin.length} digits entered" },
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(maxOf(PinLock.MIN_LENGTH, pin.length)) { i ->
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (i < pin.length) CleanColors.Ink else Color.Transparent)
                        .border(1.5.dp, CleanColors.Ink, CircleShape)
                )
            }
        }
        Text(
            error ?: " ",
            color = CleanColors.Danger,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("del", "0", "ok"))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    row.forEach { key ->
                        when (key) {
                            "del" -> PadKey(enabled && pin.isNotEmpty(), "Delete", onClick = { pin = pin.dropLast(1) }) {
                                Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = null, tint = CleanColors.Ink)
                            }
                            "ok" -> PadKey(enabled && PinLock.isValid(pin), "Done", filled = true, onClick = { onSubmit(pin) }) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                            }
                            else -> PadKey(enabled && pin.length < PinLock.MAX_LENGTH, key, onClick = { pin += key }) {
                                Text(key, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = CleanColors.Ink)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PadKey(
    enabled: Boolean,
    label: String,
    filled: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(
                when {
                    filled && enabled -> CleanColors.Accent
                    filled -> CleanColors.Line
                    else -> CleanColors.Surface
                }
            )
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) { content() }
}

/**
 * Asks [name]'s PIN. [check] gets the PIN plus what to do when it's right or wrong
 * (with the seconds to wait before the next try).
 */
@Composable
fun UnlockPinDialog(
    name: String,
    reason: String,
    check: (pin: String, onOk: () -> Unit, onWrong: (Int) -> Unit) -> Unit,
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit
) {
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var waitSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(waitSeconds) {
        if (waitSeconds > 0) {
            delay(1000)
            waitSeconds -= 1
            if (waitSeconds == 0) error = null
        }
    }
    PinDialogFrame(onDismiss) {
        PinPad(
            title = "$name's PIN",
            subtitle = reason,
            error = if (waitSeconds > 0) "Too many tries. Wait $waitSeconds s." else error,
            clearKey = attempt,
            enabled = waitSeconds == 0,
            onSubmit = { pin ->
                check(pin, onUnlocked) { wait ->
                    attempt++
                    waitSeconds = wait
                    error = "Wrong PIN"
                }
            }
        )
    }
}

/** Typing a new PIN twice. */
@Composable
fun SetPinDialog(name: String, onSet: (String) -> Unit, onDismiss: () -> Unit) {
    PinDialogFrame(onDismiss) { NewPinEntry(name, onSet) }
}

/** The "choose a PIN, then type it again" steps, also used in onboarding. */
@Composable
fun NewPinEntry(name: String, onSet: (String) -> Unit, modifier: Modifier = Modifier) {
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    PinPad(
        title = if (first == null) "Choose a PIN for $name" else "Type it again",
        subtitle = if (first == null) "${PinLock.MIN_LENGTH} to ${PinLock.MAX_LENGTH} digits" else "To make sure it's right",
        error = error,
        clearKey = attempt,
        modifier = modifier,
        onSubmit = { pin ->
            val f = first
            when {
                f == null -> { first = pin; error = null; attempt++ }
                f == pin -> onSet(pin)
                else -> { first = null; error = "The PINs didn't match. Start again."; attempt++ }
            }
        }
    )
}

@Composable
private fun PinDialogFrame(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clickSounds()
                .clip(RoundedCornerShape(24.dp))
                .background(CleanColors.Background)
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content()
            Box(Modifier.height(8.dp).width(1.dp))
            TextButton(onClick = onDismiss) { Text("Cancel", color = CleanColors.Muted) }
        }
    }
}
