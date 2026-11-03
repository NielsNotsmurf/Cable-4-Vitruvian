package nl.nielsnotsmurf.cleanui.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import nl.nielsnotsmurf.cleanui.logic.LoadCalculator
import nl.nielsnotsmurf.cleanui.model.ExerciseCard
import nl.nielsnotsmurf.cleanui.model.OneRepMax
import nl.nielsnotsmurf.cleanui.model.WeightUnit
import nl.nielsnotsmurf.cleanui.sound.clickSounds
import nl.nielsnotsmurf.cleanui.theme.CleanColors

/**
 * Type in or correct a lifter's 1RM for one exercise (kg per cable). An estimate is prefilled
 * so it can be adjusted; a real 1RM can also be removed. Used from Analytics and the set setup.
 */
@Composable
fun OneRepMaxDialog(
    lifterName: String,
    exercise: ExerciseCard,
    current: OneRepMax?,
    unit: WeightUnit,
    onSave: (kg: Float?) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(exercise.id, current) {
        mutableStateOf(current?.let { LoadCalculator.format(it.kg, unit, withUnit = false) } ?: "")
    }
    val unitName = if (unit == WeightUnit.LB) "lb" else "kg"
    val entered = text.replace(',', '.').toFloatOrNull()?.takeIf { it > 0f }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.clickSounds(),
        containerColor = CleanColors.Background,
        title = { Text(exercise.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "$lifterName's 1RM in $unitName" + (if (exercise.cables == 2) " per cable." else ".") +
                        if (current?.isEstimate == true) " This is an estimate; change it if you know better." else "",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) },
                    singleLine = true,
                    suffix = { Text(unitName) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = entered != null,
                onClick = { onSave(entered!!.let { if (unit == WeightUnit.LB) it / 2.20462f else it }) }
            ) { Text("Save", color = CleanColors.Ink) }
        },
        dismissButton = {
            Row {
                if (current != null && !current.isEstimate) {
                    TextButton(onClick = { onSave(null) }) { Text("Remove", color = CleanColors.Danger) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel", color = CleanColors.Ink) }
            }
        }
    )
}
