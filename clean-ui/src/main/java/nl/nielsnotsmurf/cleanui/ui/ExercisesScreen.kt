package nl.nielsnotsmurf.cleanui.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nl.nielsnotsmurf.cleanui.model.ExerciseCard
import nl.nielsnotsmurf.cleanui.theme.CleanColors
import nl.nielsnotsmurf.cleanui.ui.components.CleanFilterChip
import nl.nielsnotsmurf.cleanui.ui.components.ExerciseRow

private const val ALL = "All"
private const val FAVOURITES = "Favourites"

@Composable
fun ExercisesScreen(
    state: CleanUiState,
    onBack: () -> Unit,
    onOpenExercise: (ExerciseCard) -> Unit,
    onRowShown: (ExerciseCard) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf(ALL) }

    val groups = remember(state.exercises) {
        val counted = state.exercises.groupingBy { it.muscleGroup }.eachCount()
            .filterKeys { it.isNotBlank() }
            .entries.sortedByDescending { it.value }
            .map { it.key }
        listOf(ALL, FAVOURITES) + counted
    }
    val active = state.activeLifters.map { it.id }.toSet()

    val visible = remember(state.exercises, query, group, state.oneRepMaxes, active) {
        val q = query.trim().lowercase()
        state.exercises
            .asSequence()
            .filter { group == ALL || (group == FAVOURITES && it.isFavorite) || it.muscleGroup == group }
            .filter { q.isEmpty() || it.name.lowercase().contains(q) }
            // Things you've done (or have a 1RM for) first, then favourites, then popularity.
            .sortedWith(
                compareByDescending<ExerciseCard> { ex -> active.count { state.oneRepMax(it, ex.id)?.isEstimate == false } }
                    .thenByDescending { it.isFavorite }
                    .thenByDescending { it.lastPerformedAt ?: 0L }
                    .thenByDescending { it.popularity }
            )
            .toList()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Exercises", style = MaterialTheme.typography.headlineLarge)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            placeholder = { Text("Search ${state.exercises.size} exercises") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = CleanColors.Surface,
                focusedContainerColor = CleanColors.Surface,
                unfocusedBorderColor = CleanColors.Surface,
                focusedBorderColor = CleanColors.Ink
            )
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(groups) { g -> CleanFilterChip(g, selected = g == group, onClick = { group = g }) }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp)
        ) {
            if (visible.isEmpty()) {
                item {
                    Text(
                        if (group == FAVOURITES) "No favourites yet." else "No exercises match \"$query\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CleanColors.Muted,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
            items(visible, key = { it.id }) { ex ->
                LaunchedEffect(ex.id, active) { onRowShown(ex) }
                ExerciseRow(
                    exercise = ex,
                    subtitle = listOf(ex.equipment, ex.muscleGroup).filter { it.isNotBlank() }.joinToString(" · "),
                    lifters = state.activeLifters,
                    oneRepMaxFor = { id -> state.oneRepMax(id, ex.id) },
                    unit = state.unit,
                    onClick = { onOpenExercise(ex) }
                )
            }
        }
    }
}
