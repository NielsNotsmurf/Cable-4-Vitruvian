package nl.nielsnotsmurf.webapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import nl.nielsnotsmurf.cleanui.theme.CleanColors
import nl.nielsnotsmurf.cleanui.ui.components.PrimaryButton

/** The web's heart rate pairing: any strap or watch that broadcasts the standard heart rate service. */
@Composable
fun HeartRatePairing(gateway: WebTrainerGateway, lifterId: String, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var sensor by remember { mutableStateOf(gateway.heartRateSensor(lifterId)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(CleanColors.Background)
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Heart rate", style = MaterialTheme.typography.titleLarge, color = CleanColors.Ink)
            val text = when {
                !gateway.heartRateAvailable ->
                    "This browser has no Bluetooth. Open this page in Chrome or Edge, or in Bluefy on iPhone and iPad, to connect a heart rate strap."
                sensor != null -> "Connected to $sensor."
                else -> "Turn on your strap or watch's heart rate broadcast, then pick it in the list. Connect it again after reopening the page."
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = CleanColors.Muted, textAlign = TextAlign.Center)
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = CleanColors.Danger, textAlign = TextAlign.Center) }
            if (gateway.heartRateAvailable) {
                PrimaryButton(
                    text = if (sensor == null) "Connect a heart rate sensor" else "Connect another sensor",
                    accent = true,
                    loading = busy,
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            sensor = try { gateway.pairHeartRate(lifterId) } catch (e: Throwable) {
                                error = "Couldn't connect: ${e.message ?: "Bluetooth error"}"
                                null
                            }
                            busy = false
                        }
                    }
                )
                if (sensor != null) {
                    PrimaryButton(text = "Disconnect", onClick = { gateway.forgetHeartRate(lifterId); sensor = null })
                }
            }
            PrimaryButton(text = "Done", onClick = onDismiss)
        }
    }
}
