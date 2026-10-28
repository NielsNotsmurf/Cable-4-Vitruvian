package nl.nielsnotsmurf.webapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nl.nielsnotsmurf.cleanui.platform.formatDate
import nl.nielsnotsmurf.cleanui.platform.nowMillis
import nl.nielsnotsmurf.cleanui.theme.CleanColors
import nl.nielsnotsmurf.cleanui.ui.components.PrimaryButton
import nl.nielsnotsmurf.cleanui.ui.components.SectionLabel

private const val INTRO_SEEN = "web_intro_seen"
private const val BLUEFY_URL = "https://apps.apple.com/app/bluefy-web-ble-browser/id1492822055"

fun introSeen(): Boolean = LocalStorage[INTRO_SEEN] != null

/**
 * The web's first-launch intro: what the web version is, that it can lag, how to put it on
 * the home screen for this device, and where the trainer connection works (Bluefy on iOS).
 */
@Composable
fun WebIntro(onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(CleanColors.Background).safeDrawingPadding().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Cable on the web", style = MaterialTheme.typography.headlineLarge)
            Text(
                "This is the full app, running in your browser: profiles, plans, 1RMs, history and live sets. " +
                    "It's a web page rather than an installed app, so it can feel a little slower than the Android app.",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                "For Vitruvian V-Form & Trainer+. Unofficial, not affiliated with Vitruvian.",
                style = MaterialTheme.typography.bodySmall,
                color = CleanColors.Muted
            )
            InstallGuide()
            SectionLabel("Your data", Modifier.padding(top = 8.dp))
            Text(
                "Everything stays in this browser on this device; nothing is sent anywhere. Browsers can clear " +
                    "site data, so save a backup now and then: Settings, Backup and restore.",
                style = MaterialTheme.typography.bodyMedium
            )
            AppStoreGoal()
            PrimaryButton("Start", onClick = { LocalStorage[INTRO_SEEN] = nowMillis().toString(); onDone() })
        }
    }
}

/** Steps for this device only, plus where the trainer and heart rate connect. */
@Composable
fun InstallGuide() {
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    val platform = remember { webPlatform() }
    val bluefy = remember { isBluefy() }
    val installed = remember { isInstalled() }
    val bluetooth = remember { bluetoothAvailable() }
    var canPrompt by remember { mutableStateOf(canPromptInstall()) }

    SectionLabel("Put it on your home screen", Modifier.padding(top = 8.dp))
    when {
        installed -> Text("You're using the installed app. Open it from its icon on your home screen.", style = MaterialTheme.typography.bodyMedium)
        platform == WebPlatform.IOS && bluefy -> Text(
            "You're in Bluefy, which can't add apps to the home screen. Bookmark this page in Bluefy and open it from there to train with your trainer.",
            style = MaterialTheme.typography.bodyMedium
        )
        platform == WebPlatform.IOS -> Steps(
            "Open this page in Safari.",
            "Tap Share (the square with the arrow pointing up).",
            "Tap Add to Home Screen, then Add.",
            "Open Cable from its new icon."
        )
        canPrompt -> PrimaryButton("Install app", accent = true, onClick = { promptInstall(); canPrompt = false })
        platform == WebPlatform.ANDROID -> Steps(
            "Open this page in Chrome.",
            "Tap the menu (three dots, top right).",
            "Tap Install app or Add to Home screen.",
            "Open Cable from its new icon."
        )
        else -> Steps(
            "Open this page in Chrome or Edge.",
            "Click the install icon at the right of the address bar, or open the menu and choose Install Cable.",
            "Open Cable from your apps."
        )
    }

    SectionLabel("Your trainer and heart rate", Modifier.padding(top = 8.dp))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.5.dp, CleanColors.Line, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (platform == WebPlatform.IOS && !bluefy) {
            Text("On iPhone and iPad, train with your trainer in Bluefy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Apple doesn't allow Bluetooth in Safari or in apps added to the home screen. There you get everything " +
                    "except the trainer and heart rate connection: sets run on a simulated trainer.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "To train with your Vitruvian, install Bluefy (free, App Store) and open this same link in it. " +
                    "Your data doesn't move along: each browser keeps its own, so pick one to train in, or move it with a backup.",
                style = MaterialTheme.typography.bodyMedium
            )
            PrimaryButton("Get Bluefy", accent = true, onClick = { openUrl(BLUEFY_URL) })
            PrimaryButton(if (copied) "Link copied, paste it in Bluefy" else "Copy this link", onClick = {
                scope.launch { copied = copyText(pageUrl()) }
            })
        } else if (bluetooth) {
            Text("Bluetooth works here", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Your trainer connects when you start your first set; pick it in the list the browser shows. " +
                    "Heart rate straps connect from Settings, per profile. After reopening the page, they connect again the same way.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Text("This browser has no Bluetooth", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Everything works except the trainer and heart rate connection: sets run on a simulated trainer. " +
                    "To train with your Vitruvian, use Chrome or Edge (Android, Windows, Mac), or Bluefy on iPhone and iPad.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun Steps(vararg steps: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { i, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${i + 1}.", style = MaterialTheme.typography.bodyLarge, color = CleanColors.AccentInk, fontWeight = FontWeight.SemiBold)
                Text(step, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * The web's "More settings": backup and restore of everything this browser stores (the
 * Android app saves to Download/Cable instead), and the install guide again.
 */
@Composable
fun WebSettings() {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionLabel("Backup and restore", Modifier.padding(top = 8.dp))
            Text(
                "A backup holds every profile, PIN, 1RM, plan, workout and setting this browser keeps. " +
                    "Restoring one replaces what's here now.",
                style = MaterialTheme.typography.bodyMedium
            )
            PrimaryButton("Save a backup", onClick = {
                downloadText("cable-backup-${formatDate(nowMillis(), "yyyy-MM-dd")}.json", WebBackup.export())
                message = "Backup saved to your downloads."
            })
            val pending = confirmRestore
            if (pending == null) {
                PrimaryButton("Restore a backup", onClick = {
                    scope.launch {
                        val text = pickTextFile() ?: return@launch
                        if (WebBackup.isValid(text)) { confirmRestore = text; message = null } else message = "That file isn't a Cable web backup."
                    }
                })
            } else {
                Text("Replace everything in this browser with the backup?", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                PrimaryButton("Yes, restore it", accent = true, onClick = { WebBackup.restore(pending); reloadPage() })
                PrimaryButton("Cancel", onClick = { confirmRestore = null })
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = CleanColors.Muted) }
            SectionLabel("Usage counts", Modifier.padding(top = 8.dp))
            var counting by remember { mutableStateOf(WebStats.enabled) }
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Switch) { counting = !counting; WebStats.enabled = counting },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Count anonymous app use: opening the app, device type, browser, finished workouts and whether " +
                        "the trainer connected. No cookies, no IDs, nothing about you or your workouts. Helps the beta.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = counting,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(checkedTrackColor = CleanColors.Accent, checkedThumbColor = Color.White)
                )
            }
            InstallGuide()
            AppStoreGoal()
        }
    }
}

/** The sponsor goal for real iPhone and Play Store apps; shown in the intro and in Settings. */
@Composable
fun AppStoreGoal() {
    SectionLabel("Toward iPhone and Play Store apps", Modifier.padding(top = 8.dp))
    Text(
        "Cable is free and stays free. Once sponsors cover about €200 (Apple's developer account at \$99 a year " +
            "for two years, and Google Play's one-time \$25), Cable moves to TestFlight on iPhone and iPad and to the Play Store.",
        style = MaterialTheme.typography.bodyMedium
    )
    PrimaryButton("Sponsor on GitHub", onClick = { openUrl(SPONSOR_URL) })
}

const val SPONSOR_URL = "https://github.com/sponsors/NielsNotsmurf"

/** Everything the web app stores, as one JSON file. */
object WebBackup {
    private const val FORMAT = "cable-web-backup"
    private val KEYS = listOf("web_gateway", "clean_ui")

    fun export(): String = JsonObject(
        mapOf("format" to JsonPrimitive(FORMAT), "version" to JsonPrimitive(1), "exportedAt" to JsonPrimitive(nowMillis())) +
            KEYS.mapNotNull { key -> LocalStorage[key]?.let { key to JsonPrimitive(it) } }
    ).toString()

    fun isValid(text: String): Boolean = runCatching {
        Json.parseToJsonElement(text).jsonObject["format"]?.jsonPrimitive?.contentOrNull == FORMAT
    }.getOrDefault(false)

    fun restore(text: String) {
        val root = Json.parseToJsonElement(text).jsonObject
        KEYS.forEach { key -> root[key]?.jsonPrimitive?.contentOrNull?.let { LocalStorage[key] = it } }
    }
}
