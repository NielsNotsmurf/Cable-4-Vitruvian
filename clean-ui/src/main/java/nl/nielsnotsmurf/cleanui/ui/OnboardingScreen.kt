package nl.nielsnotsmurf.cleanui.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.nielsnotsmurf.cleanui.theme.CleanColors
import nl.nielsnotsmurf.cleanui.ui.components.GhostButton
import nl.nielsnotsmurf.cleanui.ui.components.NewPinEntry
import nl.nielsnotsmurf.cleanui.ui.components.PinPad
import nl.nielsnotsmurf.cleanui.ui.components.PrimaryButton
import nl.nielsnotsmurf.cleanui.ui.components.rememberScreenLockCheck
import nl.nielsnotsmurf.cleanui.ui.components.SectionLabel

private enum class OnboardingPage { WELCOME, ACCOUNT, PIN, HEART_RATE, TOUR }

/**
 * First launch: what the app is, whose phone it is (an existing profile or a new one),
 * an optional PIN, Garmin heart rate, and an offer of the app tour.
 */
@Composable
fun OnboardingScreen(
    state: CleanUiState,
    account: AccountState,
    onCreateAccount: (name: String, colorArgb: Int, onCreated: (String) -> Unit) -> Unit,
    checkPin: (lifterId: String, pin: String, onOk: () -> Unit, onWrong: (Int) -> Unit) -> Unit,
    onSetPin: (lifterId: String, pin: String) -> Unit,
    onPairHeartRate: ((lifterId: String) -> Unit)?,
    onFinish: (lifterId: String, takeTour: Boolean) -> Unit
) {
    var page by rememberSaveable { mutableStateOf(OnboardingPage.WELCOME) }
    var chosenId by rememberSaveable { mutableStateOf<String?>(null) }
    val chosen = state.lifters.firstOrNull { it.id == chosenId }

    BackHandler(enabled = page != OnboardingPage.WELCOME) {
        page = OnboardingPage.entries[page.ordinal - 1]
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {
        Progress(page)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 24.dp)) {
            when (page) {
                OnboardingPage.WELCOME -> Welcome()
                OnboardingPage.ACCOUNT -> ChooseAccount(
                    state = state,
                    chosenId = chosenId,
                    onChoose = { chosenId = it },
                    onCreate = { name, color -> onCreateAccount(name, color) { id -> chosenId = id } }
                )
                OnboardingPage.PIN -> chosen?.let { c ->
                    if (account.hasPin(c.id)) {
                        ExistingPin(c, checkPin) { page = OnboardingPage.HEART_RATE }
                    } else {
                        ChoosePin(c, onSet = { pin -> onSetPin(c.id, pin); page = OnboardingPage.HEART_RATE })
                    }
                }
                OnboardingPage.HEART_RATE -> HeartRateIntro(chosen, onPairHeartRate)
                OnboardingPage.TOUR -> TourOffer(chosen?.name)
            }
        }
        when (page) {
            OnboardingPage.WELCOME -> PrimaryButton("Get started", onClick = { page = OnboardingPage.ACCOUNT }, accent = true)
            OnboardingPage.ACCOUNT -> PrimaryButton(
                if (chosen == null) "Pick or add a profile" else "Continue as ${chosen.name}",
                onClick = { page = OnboardingPage.PIN },
                enabled = chosen != null,
                accent = true
            )
            OnboardingPage.PIN -> if (chosen != null && !account.hasPin(chosen.id)) {
                GhostButton("Not now, no PIN", onClick = { page = OnboardingPage.HEART_RATE }, modifier = Modifier.fillMaxWidth())
            }
            OnboardingPage.HEART_RATE -> PrimaryButton("Next", onClick = { page = OnboardingPage.TOUR }, accent = true)
            OnboardingPage.TOUR -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Show me around", onClick = { chosen?.let { onFinish(it.id, true) } }, accent = true)
                GhostButton("Skip the tour", onClick = { chosen?.let { onFinish(it.id, false) } }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Progress(page: OnboardingPage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OnboardingPage.entries.forEach { p ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (p.ordinal <= page.ordinal) CleanColors.Accent else CleanColors.Line)
            )
        }
    }
}

@Composable
private fun Headline(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineLarge)
    Text(body, style = MaterialTheme.typography.bodyLarge, color = CleanColors.Muted, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
}

@Composable
private fun Welcome() {
    Text("V-", fontSize = 56.sp, fontWeight = FontWeight.Black, color = CleanColors.Ink)
    Spacer(Modifier.height(12.dp))
    Headline("Welcome to vitruvian-", "Train on your Vitruvian Trainer, straight from your phone.")
    Point("Up to 4 people", "Train together. Sets rotate between you.")
    Point("Loads from your 1RM", "Every exercise starts at a % of your own max.")
    Point("Live heart rate", "From your Garmin watch or a chest strap.")
    Point("Private and offline", "No cloud account. Everything stays on this phone.")
}

@Composable
private fun Point(title: String, body: String) {
    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(CleanColors.Accent))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = CleanColors.Muted)
        }
    }
}

@Composable
private fun ChooseAccount(
    state: CleanUiState,
    chosenId: String?,
    onChoose: (String) -> Unit,
    onCreate: (name: String, colorArgb: Int) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var colorIndex by rememberSaveable { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    Headline("Who are you?", "Your account keeps your workouts, 1RMs and heart rate apart from the others on this phone.")
    if (state.lifters.isNotEmpty()) {
        SectionLabel("Profiles on this phone")
        Column(Modifier.padding(top = 8.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.lifters.forEach { l -> AccountRow(l, selected = l.id == chosenId, hasPin = false) { onChoose(l.id) } }
        }
    }
    SectionLabel("Or add yourself")
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(24) },
            placeholder = { Text("Your name") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CleanColors.Ink),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PROFILE_COLORS.forEachIndexed { i, c ->
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(c)
                        .then(if (i == colorIndex) Modifier.border(3.dp, CleanColors.Ink, CircleShape) else Modifier)
                        .clickable(role = Role.RadioButton) { colorIndex = i }
                )
            }
        }
        GhostButton(
            if (creating) "Adding…" else "Add ${name.trim().ifEmpty { "profile" }}",
            onClick = {
                if (name.isNotBlank() && !creating) {
                    creating = true
                    onCreate(name.trim(), PROFILE_COLORS[colorIndex].toArgb())
                    name = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
    // A new profile shows up in the list above, already picked.
    LaunchedEffect(chosenId) { creating = false }
}

@Composable
internal fun AccountRow(lifter: UiLifter, selected: Boolean, hasPin: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(if (selected) 2.dp else 1.5.dp, if (selected) lifter.color else CleanColors.Line, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(lifter.color), contentAlignment = Alignment.Center) {
            Text(lifter.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
        }
        Text(lifter.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (hasPin) Icon(Icons.Filled.Lock, contentDescription = "Has a PIN", tint = CleanColors.Muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ChoosePin(lifter: UiLifter, onSet: (String) -> Unit) {
    Headline(
        "Protect your data",
        "With a PIN, the app asks for it when it opens as you, and others on this phone can't see your history or add you to a training."
    )
    NewPinEntry(lifter.name, onSet)
    Text(
        "The PIN is a lock between people who share this phone, not encryption. Only a scrambled " +
            "copy is stored. Forgot it? This phone's own screen lock can reset it.",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 16.dp)
    )
}

@Composable
private fun ExistingPin(
    lifter: UiLifter,
    checkPin: (lifterId: String, pin: String, onOk: () -> Unit, onWrong: (Int) -> Unit) -> Unit,
    onOk: () -> Unit
) {
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    PinPad(
        title = "${lifter.name} has a PIN",
        subtitle = "Type it to continue",
        error = error,
        clearKey = attempt,
        onSubmit = { pin ->
            checkPin(lifter.id, pin, onOk) { wait ->
                attempt++
                error = if (wait > 0) "Too many tries. Wait $wait s." else "Wrong PIN"
            }
        }
    )
}

@Composable
private fun HeartRateIntro(lifter: UiLifter?, onPair: ((String) -> Unit)?) {
    Headline("Live heart rate", "See your heart rate while you lift and in your history. Pair now or later from Settings.")
    HeartRateSteps()
    if (lifter != null && onPair != null) {
        Spacer(Modifier.height(16.dp))
        GhostButton("Pair a watch or strap for ${lifter.name}", onClick = { onPair(lifter.id) }, modifier = Modifier.fillMaxWidth())
    }
}

/** How to get a Garmin watch (or a strap) broadcasting, shared by onboarding and Settings. */
@Composable
internal fun HeartRateSteps() {
    val steps = listOf(
        "On your Garmin, open the controls menu (hold the top button) and tap Broadcast Heart Rate. " +
            "On older watches: Settings → Sensors and Accessories → Wrist Heart Rate → Broadcast Heart Rate.",
        "Keep the watch close to the phone. It keeps broadcasting until you stop it.",
        "In vitruvian-, go to Settings → Profiles and heart rate, tap ♥ on your profile and pick your watch.",
        "A chest strap (Polar, Wahoo, Garmin HRM) works the same: put it on and pick it from the list."
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        steps.forEachIndexed { i, s ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(CleanColors.AccentSoft), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", fontWeight = FontWeight.Bold, color = CleanColors.AccentInk, fontSize = 13.sp)
                }
                Text(s, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TourOffer(name: String?) {
    Headline(
        if (name != null) "You're set, $name" else "You're set",
        "Take a one-minute tour? Arrows point out each part of the app. You can watch it again from Settings."
    )
}

/**
 * Shown when the app opens as an account with a PIN, or after Switch account: pick who
 * you are, then type that account's PIN if it has one.
 */
@Composable
fun LockScreen(
    state: CleanUiState,
    account: AccountState,
    checkPin: (lifterId: String, pin: String, onOk: () -> Unit, onWrong: (Int) -> Unit) -> Unit,
    onResetPin: (String) -> Unit,
    onSignIn: (String) -> Unit
) {
    var chosenId by rememberSaveable { mutableStateOf(account.lastAccountId ?: state.lifters.firstOrNull()?.id) }
    val chosen = state.lifters.firstOrNull { it.id == chosenId }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Who's training?", style = MaterialTheme.typography.headlineLarge)
        state.lifters.forEach { l ->
            AccountRow(l, selected = l.id == chosenId, hasPin = account.hasPin(l.id)) {
                chosenId = l.id
                error = null
                attempt++
            }
        }
        Spacer(Modifier.height(8.dp))
        if (chosen != null) {
            if (account.hasPin(chosen.id)) {
                PinPad(
                    title = "PIN for ${chosen.name}",
                    subtitle = null,
                    error = error,
                    clearKey = attempt,
                    onSubmit = { pin ->
                        checkPin(chosen.id, pin, { onSignIn(chosen.id) }) { wait ->
                            attempt++
                            error = if (wait > 0) "Too many tries. Wait $wait s." else "Wrong PIN"
                        }
                    }
                )
                val reset = rememberScreenLockCheck("Reset ${chosen.name}'s PIN") {
                    onResetPin(chosen.id)
                    onSignIn(chosen.id)
                }
                TextButton(onClick = reset) { Text("Forgot PIN? Reset it with the phone's screen lock", color = CleanColors.Muted) }
            } else {
                PrimaryButton("Continue as ${chosen.name}", onClick = { onSignIn(chosen.id) }, accent = true)
            }
        }
    }
}

