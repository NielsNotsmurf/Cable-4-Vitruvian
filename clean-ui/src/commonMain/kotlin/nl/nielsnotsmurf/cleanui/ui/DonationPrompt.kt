package nl.nielsnotsmurf.cleanui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.nielsnotsmurf.cleanui.data.CleanUiStore
import nl.nielsnotsmurf.cleanui.data.DonationPromptState
import nl.nielsnotsmurf.cleanui.platform.nowMillis
import nl.nielsnotsmurf.cleanui.sound.clickSounds
import nl.nielsnotsmurf.cleanui.theme.CleanColors

/**
 * Asks for a donation once a month (first start of each calendar month), with "Later" (again in a
 * week) and "Don't show again" (no extra reminders; the monthly one still comes). App and web.
 */
@Composable
fun DonationPrompt(store: CleanUiStore, allowed: Boolean) {
    val saved by store.donationPrompt.collectAsState(null)
    var handled by rememberSaveable { mutableStateOf(false) }
    val now = remember { nowMillis() }
    val s = saved ?: return
    if (handled || !allowed || !s.isDue(now)) return
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val month = DonationPromptState.monthOf(now)

    fun close(next: DonationPromptState) {
        handled = true
        scope.launch { store.setDonationPrompt(next) }
    }

    AlertDialog(
        onDismissRequest = { close(s.copy(shownMonth = month, snoozeUntil = now + DonationPromptState.SNOOZE_MS)) },
        modifier = Modifier.clickSounds(),
        containerColor = CleanColors.Background,
        title = { Text("Keep Cable going") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PriceTile("Official app", "€35", "a month", Modifier.weight(1f), highlight = false)
                    PriceTile("Cable", "Free", "to use", Modifier.weight(1f), highlight = true)
                }
                Text("Every donation goes to more free tools and new features.", style = MaterialTheme.typography.bodyMedium)
                Text("Everyone gets a say in what it's spent on.", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                close(s.copy(shownMonth = month, snoozeUntil = 0L))
                uriHandler.openUri(DONATE_URL)
            }) { Text("Donate", fontWeight = FontWeight.SemiBold, color = CleanColors.Accent) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { close(s.copy(shownMonth = month, snoozeUntil = 0L, noExtra = true)) }) {
                    Text("Don't show again", color = CleanColors.Muted)
                }
                TextButton(onClick = {
                    close(s.copy(shownMonth = month, snoozeUntil = now + DonationPromptState.SNOOZE_MS))
                }) { Text("Later") }
            }
        }
    )
}

@Composable
private fun PriceTile(label: String, price: String, unit: String, modifier: Modifier, highlight: Boolean) {
    Column(
        modifier
            .background(if (highlight) CleanColors.AccentSoft else CleanColors.Surface, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = CleanColors.Muted)
        Text(
            price,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = if (highlight) CleanColors.AccentInk else CleanColors.Ink
        )
        Text(unit, style = MaterialTheme.typography.bodySmall, color = CleanColors.Muted)
    }
}
