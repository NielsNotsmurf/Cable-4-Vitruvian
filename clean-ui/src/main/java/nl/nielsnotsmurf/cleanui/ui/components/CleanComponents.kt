package nl.nielsnotsmurf.cleanui.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import nl.nielsnotsmurf.cleanui.logic.LoadCalculator
import nl.nielsnotsmurf.cleanui.model.ExerciseCard
import nl.nielsnotsmurf.cleanui.model.OneRepMax
import nl.nielsnotsmurf.cleanui.model.TrainerConnection
import nl.nielsnotsmurf.cleanui.model.WeightUnit
import nl.nielsnotsmurf.cleanui.theme.CleanColors
import nl.nielsnotsmurf.cleanui.ui.UiLifter

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = CleanColors.Muted,
        modifier = modifier
    )
}

@Composable
fun Dot(color: Color, size: Int = 9) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

@Composable
fun LifterChip(lifter: UiLifter, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) CleanColors.Ink else Color.Transparent)
            .border(1.dp, if (selected) CleanColors.Ink else CleanColors.Line, shape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Dot(lifter.color)
        Text(
            lifter.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else CleanColors.Ink
        )
    }
}

@Composable
fun CleanFilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) CleanColors.Ink else Color.Transparent)
            .border(1.dp, if (selected) CleanColors.Ink else CleanColors.Line, shape)
            .clickable(onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (selected) Color.White else CleanColors.Ink)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val bg = when {
        !enabled -> CleanColors.Line
        accent -> CleanColors.Accent
        else -> CleanColors.Ink
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable(enabled = enabled && !loading, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) Color.White else CleanColors.Muted)
        }
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .border(1.5.dp, CleanColors.Line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

/** Cables an exercise pulls on. Every load in the app is per cable; this says how many carry it. */
fun cablesOf(cableConfig: String?): Int = if (cableConfig == "SINGLE") 1 else 2

val ExerciseCard.cables: Int get() = cablesOf(cableConfig)

/**
 * Small cable mark next to a load: one or two cables, each a line with a handle. Every load
 * shown is per cable, so two cables means the trainer pulls that load on each side.
 */
@Composable
fun CableIcon(cables: Int, color: Color = CleanColors.Muted, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = if (cables == 2) 14.dp else 8.dp, height = 16.dp)) {
        val w = 2.dp.toPx()
        val r = 2.5.dp.toPx()
        val xs = if (cables == 2) listOf(size.width * 0.25f, size.width * 0.75f) else listOf(size.width / 2)
        xs.forEach { x ->
            drawLine(color, Offset(x, 0f), Offset(x, size.height - 2 * r), strokeWidth = w, cap = StrokeCap.Round)
            drawCircle(color, r, Offset(x, size.height - r))
        }
    }
}

/** A load with its cable mark, e.g. "50 kg ‖" for 50 kg on each of two cables. */
@Composable
fun LoadText(
    text: String,
    cables: Int,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = CleanColors.Ink,
    modifier: Modifier = Modifier
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = style, color = color, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        CableIcon(cables, color = color.copy(alpha = 0.7f))
    }
}

/**
 * Shown while a set is waiting to start: after a few seconds without a connected trainer it
 * says that's why nothing happens yet, instead of an endless spinner.
 */
@Composable
fun TrainerSearchHint(connection: TrainerConnection, waiting: Boolean, modifier: Modifier = Modifier) {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(waiting) {
        slow = false
        if (waiting) { delay(4000); slow = true }
    }
    if (!waiting || !slow || connection is TrainerConnection.Connected) return
    val text = when (connection) {
        is TrainerConnection.Failed -> "Couldn't connect to your Vitruvian: ${connection.message}"
        else -> "No Vitruvian found yet. The set starts once the trainer is connected. Check it's switched on and nearby."
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = CleanColors.Danger,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth()
    )
}

/** Square-ish shortcut on the Train screen. */
@Composable
fun Tile(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .heightIn(min = 96.dp)
            .clip(shape)
            .border(1.5.dp, CleanColors.Line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CleanColors.Muted)
    }
}

@Composable
fun Stepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(label)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CleanColors.Surface)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StepperKey("−", onMinus)
            Text(
                value,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineMedium
            )
            StepperKey("+", onPlus)
        }
    }
}

@Composable
private fun StepperKey(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ConnectionPill(connection: TrainerConnection) {
    val (color, text) = when (connection) {
        is TrainerConnection.Connected -> CleanColors.Success to connection.deviceName
        TrainerConnection.Connecting -> CleanColors.Accent to "Connecting"
        is TrainerConnection.Failed -> CleanColors.Danger to "Not connected"
        TrainerConnection.Disconnected -> CleanColors.Muted to "Not connected"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, CleanColors.Line, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Dot(color, 8)
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

/** Small orange tag, used for "Benchmark" when a lifter has no 1RM. */
@Composable
fun Tag(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = CleanColors.AccentInk,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(CleanColors.AccentSoft)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/** One line per active lifter: their 1RM in their colour, "~" for related estimates. */
@Composable
fun OneRepMaxColumn(
    lifters: List<UiLifter>,
    oneRepMaxFor: (String) -> OneRepMax?,
    unit: WeightUnit,
    cables: Int
) {
    val missing = lifters.filter { oneRepMaxFor(it.id) == null }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lifters.forEach { l ->
            val orm = oneRepMaxFor(l.id) ?: return@forEach
            LoadText((if (orm.isEstimate) "~" else "") + LoadCalculator.format(orm.kg, unit), cables, color = l.color)
        }
        when {
            missing.isEmpty() -> Unit
            missing.size == lifters.size -> Tag("Benchmark")
            else -> Tag(missing.joinToString { it.name } + ": test")
        }
    }
}

@Composable
fun ExerciseRow(
    exercise: ExerciseCard,
    subtitle: String,
    lifters: List<UiLifter>,
    oneRepMaxFor: (String) -> OneRepMax?,
    unit: WeightUnit,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CleanColors.Surface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    exercise.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = CleanColors.Muted
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            OneRepMaxColumn(lifters, oneRepMaxFor, unit, exercise.cables)
            trailing?.invoke()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(CleanColors.Line))
    }
}
