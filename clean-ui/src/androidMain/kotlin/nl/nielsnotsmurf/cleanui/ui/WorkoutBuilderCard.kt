package nl.nielsnotsmurf.cleanui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.nielsnotsmurf.cleanui.logic.WorkoutBuilder
import nl.nielsnotsmurf.cleanui.theme.CleanColors

/** Body regions, in the order the classic muscle-group picker shows them. */
val BODY_REGIONS = listOf("Chest", "Back", "Shoulders", "Arms", "Legs", "Core")

/**
 * The home screen's main action: pick muscle groups and a time, and the app builds the
 * workout from each lifter's 1RM. Dark, with the LED colour glowing at the edge.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutBuilderCard(
    state: CleanUiState,
    onBuild: (groups: List<String>, minutes: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var minutes by rememberSaveable { mutableIntStateOf(45) }
    val shape = RoundedCornerShape(24.dp)
    val withMax = state.activeLifters.map { it.id }.let { ids ->
        BODY_REGIONS.associateWith { r ->
            state.exercises.count { ex -> ex.region == r && ids.any { state.oneRepMax(it, ex.id)?.isEstimate == false } }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(CleanColors.Ink, CleanColors.Ink, CleanColors.AccentInk)))
            .border(1.5.dp, CleanColors.Glow.copy(alpha = 0.45f), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("BUILD A WORKOUT", style = MaterialTheme.typography.labelSmall, color = CleanColors.Glow)
        Text(
            "What are you training?",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BODY_REGIONS.forEach { r ->
                val on = r in selected
                DarkChip(
                    text = if ((withMax[r] ?: 0) > 0) "$r · ${withMax[r]}" else r,
                    selected = on,
                    onClick = { selected = if (on) selected - r else selected + r }
                )
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkoutBuilder.DURATIONS.forEach { m -> DarkChip("$m min", minutes == m) { minutes = m } }
        }
        Text(
            "Numbers are exercises with a tested 1RM. Loads follow each lifter's 1RM, same equipment stays together.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.65f)
        )
        val ready = selected.isNotEmpty()
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (ready) CleanColors.Glow else Color.White.copy(alpha = 0.12f))
                .clickable(enabled = ready, role = Role.Button) { onBuild(BODY_REGIONS.filter { it in selected }, minutes) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (ready) "Build my workout" else "Pick one or more groups",
                style = MaterialTheme.typography.labelLarge,
                color = if (ready) CleanColors.Ink else Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun DarkChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) Color.White else Color.Transparent)
            .border(1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.3f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 38.dp)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (selected) CleanColors.Ink else Color.White)
    }
}
