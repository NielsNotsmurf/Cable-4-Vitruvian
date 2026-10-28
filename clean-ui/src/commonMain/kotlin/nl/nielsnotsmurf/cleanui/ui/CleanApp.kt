package nl.nielsnotsmurf.cleanui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import nl.nielsnotsmurf.cleanui.sound.CleanSounds
import nl.nielsnotsmurf.cleanui.sound.Sfx
import nl.nielsnotsmurf.cleanui.sound.clickSounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import nl.nielsnotsmurf.cleanui.model.LivePhase
import nl.nielsnotsmurf.cleanui.model.LiveWorkout
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.savedstate.read
import nl.nielsnotsmurf.cleanui.model.ExerciseGuide
import nl.nielsnotsmurf.cleanui.data.rememberCleanUiStore
import nl.nielsnotsmurf.cleanui.gateway.TrainerGateway
import nl.nielsnotsmurf.cleanui.theme.CleanColors
import nl.nielsnotsmurf.cleanui.theme.CleanTheme
import nl.nielsnotsmurf.cleanui.ui.components.cables
import nl.nielsnotsmurf.cleanui.ui.components.SetPinDialog
import nl.nielsnotsmurf.cleanui.ui.components.UnlockPinDialog
import nl.nielsnotsmurf.cleanui.ui.tour.LocalTour
import nl.nielsnotsmurf.cleanui.ui.tour.TourController
import nl.nielsnotsmurf.cleanui.ui.tour.TourOverlay
import nl.nielsnotsmurf.cleanui.ui.tour.TourTargets
import nl.nielsnotsmurf.cleanui.ui.tour.tourTarget
import nl.nielsnotsmurf.cleanui.platform.formatClock
import nl.nielsnotsmurf.cleanui.ui.components.PlatformBackHandler

/**
 * Route names the host can rely on. [TRAIN], [ACTIVE_WORKOUT] and [JUST_LIFT] match the
 * classic routes on purpose, so classic screens hosted here keep navigating by their own names
 * (for example muscle-group training pops up to "home" when a set starts).
 */
object CleanRoutes {
    const val TRAIN = "home"
    const val EXERCISES = "clean_exercises"
    const val WORKOUT_PLAN = "clean_workout_plan"
    const val PROFILES = "profiles"
    const val HISTORY = "clean_history"
    const val SETTINGS = "clean_settings"
    const val ANALYTICS = "clean_analytics"
    const val SETUP = "clean_setup"
    const val ONE_REP_MAX_TEST = "clean_1rm_test"
    const val ACTIVE_WORKOUT = "active_workout"
    const val JUST_LIFT = "just_lift"
}

/**
 * Classic screens the Clean layout shows until their clean versions land. Routes match the
 * classic graph. [title] is shown above screens that relied on the classic top bar.
 */
enum class ClassicScreen(val route: String, val title: String?, val showBack: Boolean = false) {
    ANALYTICS("analytics", "Charts and insights", showBack = true),
    SETTINGS("settings", "More settings", showBack = true),
    ASSESSMENT("baseline_assessment", null),
    CONNECTION_LOGS("connection_logs", "Connection logs", showBack = true),
    PROTOCOL_TESTER("protocol_tester", null)
}

/** From this width the tabs move to a rail and the screens stop growing wider. */
private val WIDE_LAYOUT = 600.dp
private val MAX_CONTENT_WIDTH = 720.dp

private data class Tab(val route: String, val label: String, val icon: ImageVector)

/** Train sits in the middle as a round button. */
private val TABS = listOf(
    Tab(CleanRoutes.HISTORY, "History", Icons.Outlined.History),
    Tab(CleanRoutes.ANALYTICS, "Analytics", Icons.AutoMirrored.Outlined.ShowChart),
    Tab(CleanRoutes.TRAIN, "Train", Icons.Outlined.FitnessCenter),
    Tab(CleanRoutes.EXERCISES, "Exercises", Icons.Outlined.Search),
    Tab(CleanRoutes.SETTINGS, "Settings", Icons.Outlined.Settings)
)

/**
 * Entry point of the Clean UI: Train, History, Analytics and Settings tabs.
 *
 * Lifting, Just Lift and the screens in [ClassicScreen] are slots: the host passes the
 * existing classic screens until the clean versions land, so every feature stays reachable.
 */
@Composable
fun CleanApp(
    gateway: TrainerGateway,
    activeWorkoutContent: @Composable (NavController) -> Unit,
    justLiftContent: @Composable (NavController) -> Unit,
    classicContent: @Composable (ClassicScreen, NavController) -> Unit,
    /** The app's heart rate sensor pairing for one lifter (Garmin watch or strap). */
    heartRateContent: (@Composable (lifterId: String, onDismiss: () -> Unit) -> Unit)? = null
) {
    val store = rememberCleanUiStore()
    val vm: CleanViewModel = viewModel(factory = CleanViewModel.factory(gateway, store))
    val state by vm.state.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    val tour = remember { TourController() }
    // Heart rate pairing for one lifter, opened from Profiles, Settings or onboarding.
    var heartRateFor by rememberSaveable { mutableStateOf<String?>(null) }
    // A PIN to type before something happens to a protected profile.
    var pinRequest by remember { mutableStateOf<PinRequest?>(null) }
    var newPinFor by rememberSaveable { mutableStateOf<String?>(null) }

    /** Runs [action] once [lifterId]'s PIN is typed, if it's locked (or [always] when it has one). */
    fun withPin(lifterId: String, reason: String, always: Boolean, action: () -> Unit) {
        val ask = vm.needsPin(lifterId) || (always && account?.hasPin(lifterId) == true)
        if (ask) pinRequest = PinRequest(lifterId, reason, action) else action()
    }

    CleanTheme {
        Surface(color = CleanColors.Background, modifier = Modifier.fillMaxSize()) {
            val a = account
            Box(Modifier.fillMaxSize().edgeGlow(CleanColors.Glow).safeDrawingPadding().clickSounds()) {
                when {
                    // Storage not read yet: blank for a moment beats a flash of the wrong screen.
                    a == null -> Unit
                    !a.onboarded -> OnboardingScreen(
                        state = state,
                        account = a,
                        onCreateAccount = vm::createAccount,
                        checkPin = vm::checkPin,
                        onSetPin = vm::setPin,
                        onPairHeartRate = if (heartRateContent != null) ({ heartRateFor = it }) else null,
                        onFinish = { id, takeTour ->
                            vm.finishOnboarding(id)
                            if (takeTour) tour.start() else vm.setTourStatus(id, done = false)
                        }
                    )
                    a.signedInId == null -> LockScreen(
                        state = state,
                        account = a,
                        checkPin = vm::checkPin,
                        onResetPin = vm::removePin,
                        onSignIn = vm::signIn
                    )
                    else -> CompositionLocalProvider(LocalTour provides tour) {
                        SignedInApp(
                            gateway = gateway,
                            vm = vm,
                            account = a,
                            me = a.signedInId,
                            tour = tour,
                            withPin = { id, reason, always, action -> withPin(id, reason, always, action) },
                            onNewPin = { newPinFor = it },
                            onHeartRate = if (heartRateContent != null) ({ heartRateFor = it }) else null,
                            activeWorkoutContent = activeWorkoutContent,
                            justLiftContent = justLiftContent,
                            classicContent = classicContent
                        )
                    }
                }
            }
        }

        heartRateFor?.let { id -> heartRateContent?.invoke(id) { heartRateFor = null } }
        pinRequest?.let { request ->
            UnlockPinDialog(
                name = state.lifters.firstOrNull { it.id == request.lifterId }?.name.orEmpty(),
                reason = request.reason,
                check = { pin, onOk, onWrong -> vm.checkPin(request.lifterId, pin, onOk, onWrong) },
                onUnlocked = { pinRequest = null; request.action() },
                onDismiss = { pinRequest = null }
            )
        }
        newPinFor?.let { id ->
            SetPinDialog(
                name = state.lifters.firstOrNull { it.id == id }?.name.orEmpty(),
                onSet = { pin -> vm.setPin(id, pin); newPinFor = null },
                onDismiss = { newPinFor = null }
            )
        }
    }
}

/** Something to do once [lifterId]'s PIN has been typed. */
private class PinRequest(val lifterId: String, val reason: String, val action: () -> Unit)

/** The tabs and every screen behind them, once someone has signed in as [me]. */
@Composable
private fun SignedInApp(
    gateway: TrainerGateway,
    vm: CleanViewModel,
    account: AccountState,
    me: String,
    tour: TourController,
    withPin: (lifterId: String, reason: String, always: Boolean, action: () -> Unit) -> Unit,
    onNewPin: (String) -> Unit,
    onHeartRate: ((String) -> Unit)?,
    activeWorkoutContent: @Composable (NavController) -> Unit,
    justLiftContent: @Composable (NavController) -> Unit,
    classicContent: @Composable (ClassicScreen, NavController) -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val setup by vm.setup.collectAsStateWithLifecycle()
    val test by vm.test.collectAsStateWithLifecycle()
    val builtWorkout by vm.workout.collectAsStateWithLifecycle()
    val history by vm.visibleHistory.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val heartRate by vm.heartRate.collectAsStateWithLifecycle()
    val ledScheme by vm.ledScheme.collectAsStateWithLifecycle()
    val uiSounds by vm.uiSounds.collectAsStateWithLifecycle()
    val livePercent by vm.livePercent.collectAsStateWithLifecycle()
    val live by gateway.liveWorkout.collectAsStateWithLifecycle(null)
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    // Exercises takes an optional region; without one it is the Exercises tab.
    val currentRoute = backStack?.destination?.route?.substringBefore('?')
    val isTab = TABS.any { it.route == currentRoute } &&
        !(currentRoute == CleanRoutes.EXERCISES && backStack?.arguments?.read { getStringOrNull("region") } != null)

    fun openTab(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    /** Adding a PIN-protected lifter to the training asks for their PIN; taking anyone out doesn't. */
    fun toggleLifter(lifterId: String) {
        if (state.activeLifters.any { it.id == lifterId }) vm.toggleLifter(lifterId)
        else withPin(lifterId, "To join the training", false) { vm.toggleLifter(lifterId) }
    }

    // The tour opens the screen of each step.
    // A boop on every screen change (tabs, back, forward), after the first screen.
    var lastRoute by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentRoute) {
        if (lastRoute != null && currentRoute != null && currentRoute != lastRoute) CleanSounds.play(Sfx.NAV)
        if (currentRoute != null) lastRoute = currentRoute
    }

    LaunchedEffect(tour.index) {
        val route = tour.step?.route ?: return@LaunchedEffect
        if (nav.currentDestination?.route?.substringBefore('?') == route) return@LaunchedEffect
        // Back to a screen that's already open underneath (Profiles → Settings) is a pop;
        // anything else opens its tab or screen.
        if (nav.popBackStack(route, inclusive = false)) return@LaunchedEffect
        if (TABS.any { it.route == route }) openTab(route) else nav.navigate(route) { launchSingleTop = true }
    }
    // Back steps back through the screens. On Android the NavHost below already does this (its
    // handler is newer, so it wins); the browser's back button needs this one.
    PlatformBackHandler(enabled = backStack != null && nav.previousBackStackEntry != null) { nav.popBackStack() }
    PlatformBackHandler(enabled = tour.isRunning) {
        tour.stop()
        vm.setTourStatus(me, done = false)
    }
    // Someone signing in for the first time on this phone gets the tour offered once.
    var tourOffered by rememberSaveable(me) { mutableStateOf(false) }
    if (!tourOffered && !tour.isRunning && account.tour(me) == TourStatus.NotSeen) {
        val name = state.lifters.firstOrNull { it.id == me }?.name.orEmpty()
        AlertDialog(
            onDismissRequest = { tourOffered = true },
            modifier = Modifier.clickSounds(),
            containerColor = CleanColors.Background,
            title = { Text("Welcome, $name") },
            text = { Text("Take a one-minute tour? Arrows point out each part of the app. It's in Settings too.") },
            confirmButton = { TextButton(onClick = { tourOffered = true; tour.start() }) { Text("Show me around") } },
            dismissButton = {
                TextButton(onClick = { tourOffered = true; vm.setTourStatus(me, done = false) }) { Text("Not now") }
            }
        )
    }

    // Phones get the tab bar at the bottom; tablets, iPads, landscape and desktop a rail on the
    // left, with the screens kept to a readable width.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WIDE_LAYOUT
        Row(Modifier.fillMaxSize()) {
            if (wide && isTab) TabRail(currentRoute, onSelect = ::openTab)
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                NavHost(
                    navController = nav,
                    startDestination = CleanRoutes.TRAIN,
                    modifier = Modifier.weight(1f).widthIn(max = MAX_CONTENT_WIDTH)
                ) {
                    composable(CleanRoutes.TRAIN) {
                        TodayScreen(
                            state = state,
                            history = history,
                            heartRate = heartRate,
                            onToggleLifter = ::toggleLifter,
                            onPickExercise = { openTab(CleanRoutes.EXERCISES) },
                            onOpenExercise = { vm.openSetup(it); nav.navigate(CleanRoutes.SETUP) },
                            onBuild = { groups, minutes ->
                                vm.buildWorkout(groups, minutes)
                                nav.navigate(CleanRoutes.WORKOUT_PLAN)
                            },
                            onAssessment = { vm.openTest(); nav.navigate(CleanRoutes.ONE_REP_MAX_TEST) },
                            onManageProfiles = { nav.navigate(CleanRoutes.PROFILES) },
                            onRetest = { lifterId, exerciseId ->
                                vm.openTest(lifterId, listOf(exerciseId))
                                nav.navigate(CleanRoutes.ONE_REP_MAX_TEST)
                            },
                            onRowShown = vm::requestEstimates
                        )
                    }
                    composable(
                        "${CleanRoutes.EXERCISES}?region={region}",
                        arguments = listOf(navArgument("region") { type = NavType.StringType; nullable = true })
                    ) { entry ->
                        val region = entry.arguments?.read { getStringOrNull("region") }
                        ExercisesScreen(
                            state = state,
                            onBack = if (region != null) ({ nav.popBackStack(); Unit }) else null,
                            onOpenExercise = { vm.openSetup(it); nav.navigate(CleanRoutes.SETUP) },
                            onRowShown = vm::requestEstimates,
                            onToggleFavorite = vm::toggleFavorite,
                            region = region
                        )
                    }
                    composable(CleanRoutes.HISTORY) {
                        HistoryScreen(state = state, history = history, onDelete = vm::deleteWorkouts)
                    }
                    composable(CleanRoutes.ANALYTICS) {
                        AnalyticsScreen(
                            state = state,
                            history = history,
                            onMoreAnalytics = { nav.navigate(ClassicScreen.ANALYTICS.route) },
                            onSetOneRepMax = vm::setManualOneRepMax
                        )
                    }
                    composable(CleanRoutes.SETTINGS) {
                        SettingsScreen(
                            state = state,
                            settings = settings,
                            account = account,
                            ledSchemes = gateway.ledSchemes,
                            ledIndex = ledScheme,
                            onUpdate = vm::updateSettings,
                            onLedScheme = vm::setLedScheme,
                            uiSounds = uiSounds,
                            onUiSounds = vm::setUiSounds,
                            onProfiles = { nav.navigate(CleanRoutes.PROFILES) },
                            onSetPin = { onNewPin(me) },
                            // Changing or removing a PIN asks for the current one, even when signed in.
                            onChangePin = { withPin(me, "Type your current PIN", true) { onNewPin(me) } },
                            onRemovePin = { withPin(me, "Type your PIN to remove it", true) { vm.removePin(me) } },
                            onSwitchAccount = vm::signOut,
                            onStartTour = tour::start,
                            onPairHeartRate = onHeartRate,
                            onBackup = { nav.navigate(ClassicScreen.SETTINGS.route) },
                            onConnectionLogs = { nav.navigate(ClassicScreen.CONNECTION_LOGS.route) },
                            onProtocolTester = { nav.navigate(ClassicScreen.PROTOCOL_TESTER.route) },
                            onSwitchToClassic = vm::switchToClassic
                        )
                    }
                    composable(CleanRoutes.PROFILES) {
                        ProfilesScreen(
                            state = state,
                            account = account,
                            onBack = { nav.popBackStack() },
                            onToggleLifter = ::toggleLifter,
                            onAdd = vm::addLifter,
                            onDelete = { id -> withPin(id, "To delete this profile", true) { vm.deleteLifter(id) } },
                            onHeartRate = onHeartRate?.let { open -> { id: String -> withPin(id, "To pair a heart rate sensor", false) { open(id) } } }
                        )
                    }
                    composable(CleanRoutes.WORKOUT_PLAN) {
                        val w = builtWorkout
                        if (w == null) {
                            LaunchedEffect(Unit) { nav.popBackStack(CleanRoutes.WORKOUT_PLAN, inclusive = true) }
                        } else {
                            WorkoutPlanScreen(
                                state = state,
                                workout = w,
                                loadFor = { id, ex -> vm.workoutLoad(id, ex, w) },
                                onPercent = vm::setWorkoutPercent,
                                onSubstitute = vm::substituteInWorkout,
                                onRemove = vm::removeFromWorkout,
                                onBack = { nav.popBackStack() },
                                onSets = vm::setWorkoutSets,
                                onReps = vm::setWorkoutReps,
                                onNudgeLoad = vm::nudgeWorkoutLoad,
                                onStart = {
                                    vm.startBuiltWorkout {
                                        nav.navigate(CleanRoutes.ACTIVE_WORKOUT) { popUpTo(CleanRoutes.TRAIN) }
                                    }
                                }
                            )
                        }
                    }
                    composable(CleanRoutes.SETUP) {
                        val s = setup
                        if (s == null) {
                            LaunchedEffect(Unit) { nav.popBackStack(CleanRoutes.SETUP, inclusive = true) }
                        } else {
                            SetSetupScreen(
                                state = state,
                                setup = s,
                                loadFor = { id -> vm.loadFor(id, s) },
                                onUpdate = vm::updateSetup,
                                onPercent = vm::setPercent,
                                onNudge = vm::nudgeLoad,
                                onBack = { nav.popBackStack() },
                                onTestOneRepMax = { lifterId ->
                                    vm.openTest(lifterId, listOf(s.exercise.id))
                                    nav.navigate(CleanRoutes.ONE_REP_MAX_TEST)
                                },
                                onStart = {
                                    vm.start {
                                        nav.navigate(CleanRoutes.ACTIVE_WORKOUT) {
                                            popUpTo(CleanRoutes.TRAIN)
                                        }
                                    }
                                }
                            )
                        }
                    }
                    composable(CleanRoutes.ONE_REP_MAX_TEST) {
                        val t = test
                        // Closing clears the test while this screen is still animating out. The
                        // pop below must name this route: a bare popBackStack() from that leftover
                        // frame popped the Train home too and left only the tab bar.
                        val close = { vm.closeTest(); nav.popBackStack(); Unit }
                        if (t == null) {
                            LaunchedEffect(Unit) { nav.popBackStack(CleanRoutes.ONE_REP_MAX_TEST, inclusive = true) }
                        } else {
                            OneRepMaxTestScreen(
                                state = state,
                                test = t,
                                onSelectLifter = vm::testSelectLifter,
                                onBegin = vm::testBegin,
                                onNudge = vm::testNudge,
                                onRecord = vm::testRecord,
                                onTryAgain = vm::testTryAgain,
                                // Back from the lifting screen returns here to record the result.
                                onLiftOnTrainer = { vm.testLiftOnTrainer { nav.navigate(CleanRoutes.ACTIVE_WORKOUT) } },
                                onSave = { if (vm.testSave()) close() },
                                onSkip = { if (vm.testSkip()) close() },
                                onBack = close
                            )
                        }
                    }
                    composable(CleanRoutes.ACTIVE_WORKOUT) {
                        val l = live
                        // Load the demo as soon as the exercise is known, so it's ready by the countdown.
                        val card = state.exercises.firstOrNull { it.name == l?.exerciseName }
                        val guide by produceState<ExerciseGuide?>(null, card?.id, settings.videos) {
                            value = if (card == null || !settings.videos) null
                            else runCatching { gateway.exerciseGuide(card.id) }.getOrNull()
                        }
                        // A 1RM attempt is running while the test is open underneath.
                        val attemptMode = test != null
                        // Leave only once: the screen stays composed while it animates out, and a
                        // second pop would close the test as well.
                        var left by remember { mutableStateOf(false) }
                        val done = { if (!left) { left = true; nav.popBackStack(); gateway.resetForNewWorkout() } }
                        var attemptStarted by remember { mutableStateOf(false) }
                        if (l?.isCleanReady() == true) attemptStarted = true
                        if (attemptMode && attemptStarted && l?.isCleanReady() != true) {
                            // The rep is over and the trainer went idle: back to the test to record
                            // it instead of showing the (blank) classic screen.
                            LaunchedEffect(Unit) { done() }
                            Box(Modifier.fillMaxSize().background(CleanColors.Background))
                        } else if (l == null || !l.isCleanReady()) {
                            activeWorkoutContent(nav)
                        } else {
                            LiveWorkoutScreen(
                                live = l,
                                unit = state.unit,
                                heartRate = heartRate,
                                history = history,
                                connection = state.connection,
                                cables = card?.cables ?: 2,
                                attemptMode = attemptMode,
                                onSkipRest = gateway::skipRest,
                                onContinue = gateway::proceedFromSummary,
                                onNextExercise = gateway::advanceToNextExercise,
                                onStop = { if (!left) { left = true; gateway.stopWorkout(); nav.popBackStack() } },
                                onDone = done,
                                guide = guide,
                                onAdjustWeight = gateway::adjustLiveWeight,
                                percent = livePercent,
                                onPercent = vm::setLivePercent,
                                onFinishSet = gateway::finishSetEarly,
                                onRestartSet = gateway::restartSet,
                                onMoveSet = gateway::moveSet,
                                onMoveExercise = gateway::moveExercise,
                                onLeave = { if (!left) { left = true; nav.popBackStack() } }
                            )
                        }
                    }
                    composable(CleanRoutes.JUST_LIFT) { justLiftContent(nav) }
                    ClassicScreen.entries.forEach { screen ->
                        composable(screen.route) {
                            Column(Modifier.fillMaxSize()) {
                                screen.title?.let { title ->
                                    ScreenTitle(title, onBack = if (screen.showBack) ({ nav.popBackStack() }) else null)
                                }
                                Box(Modifier.weight(1f)) { classicContent(screen, nav) }
                            }
                        }
                    }
                }
                // A workout left running (Back from the lifting screen) is picked up from here.
                val running = live
                if (isTab && test == null && running.isCleanReady() && running?.phase != LivePhase.FINISHED) {
                    Box(Modifier.widthIn(max = MAX_CONTENT_WIDTH)) {
                        ResumeBar(running!!) { nav.navigate(CleanRoutes.ACTIVE_WORKOUT) { launchSingleTop = true } }
                    }
                }
                if (isTab && !wide) {
                    TabBar(currentRoute, onSelect = ::openTab)
                }
            }
        }
        if (tour.isRunning) {
            TourOverlay(tour) { completed -> vm.setTourStatus(me, completed) }
        }
    }
}

@Composable
private fun ResumeBar(live: LiveWorkout, onResume: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CleanColors.Ink)
            .clickable(onClick = onResume)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Workout running", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
            Text(
                listOfNotNull(
                    live.exerciseName.ifBlank { null },
                    if (live.phase == LivePhase.RESTING) "rest ${formatClock(live.restSeconds)}" else null
                ).joinToString(" · ").ifBlank { "Workout" },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1
            )
        }
        Text("Resume", style = MaterialTheme.typography.labelLarge, color = CleanColors.Accent, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ScreenTitle(text: String, onBack: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = if (onBack != null) 4.dp else 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        }
        Text(text, style = MaterialTheme.typography.headlineLarge)
    }
}

@Composable
private fun TabBar(currentRoute: String?, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(CleanColors.Background)) {
        HorizontalDivider(color = CleanColors.Line)
        Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            TABS.forEach { tab ->
                val selected = tab.route == currentRoute
                if (tab.route == CleanRoutes.TRAIN) {
                    TrainButton(tab, selected, Modifier.weight(1f)) { if (!selected) onSelect(tab.route) }
                    return@forEach
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .tourTarget(TourTargets.tab(tab.route))
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Tab) { if (!selected) onSelect(tab.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = null,
                        tint = if (selected) CleanColors.Accent else CleanColors.Muted,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        tab.label,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) CleanColors.Ink else CleanColors.Muted
                    )
                }
            }
        }
    }
}

/** The tabs as a rail down the left side, for wide screens. */
@Composable
private fun TabRail(currentRoute: String?, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxHeight().background(CleanColors.Background)) {
        Column(
            Modifier.fillMaxHeight().width(88.dp).padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
        ) {
            TABS.forEach { tab ->
                val selected = tab.route == currentRoute
                if (tab.route == CleanRoutes.TRAIN) {
                    TrainButton(tab, selected, Modifier.padding(vertical = 8.dp), raised = false) { if (!selected) onSelect(tab.route) }
                    return@forEach
                }
                Column(
                    modifier = Modifier
                        .width(72.dp)
                        .tourTarget(TourTargets.tab(tab.route))
                        .heightIn(min = 60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Tab) { if (!selected) onSelect(tab.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = null,
                        tint = if (selected) CleanColors.Accent else CleanColors.Muted,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        tab.label,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) CleanColors.Ink else CleanColors.Muted
                    )
                }
            }
        }
        VerticalDivider(color = CleanColors.Line)
    }
}

/** The round Train button in the middle of the tab bar, raised a little above it. */
@Composable
private fun TrainButton(tab: Tab, selected: Boolean, modifier: Modifier, raised: Boolean = true, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 52.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .offset(y = if (raised) (-12).dp else 0.dp)
                .size(58.dp)
                .shadow(
                    if (selected) 10.dp else 4.dp,
                    CircleShape,
                    ambientColor = CleanColors.Glow,
                    spotColor = CleanColors.Glow
                )
                .clip(CircleShape)
                .background(if (selected) CleanColors.Accent else CleanColors.AccentSoft)
                .border(4.dp, CleanColors.Background, CircleShape)
                .clickable(role = Role.Tab, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                tab.icon,
                contentDescription = tab.label,
                tint = if (selected) Color.White else CleanColors.AccentInk,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/** A faint glow in the accent colour around the edge of the screen. */
private fun Modifier.edgeGlow(color: Color, width: Dp = 14.dp, alpha: Float = 0.18f) = drawWithContent {
    drawContent()
    val w = width.toPx()
    val c = color.copy(alpha = alpha)
    val clear = color.copy(alpha = 0f)
    drawRect(Brush.verticalGradient(listOf(c, clear), startY = 0f, endY = w), size = Size(size.width, w))
    drawRect(
        Brush.verticalGradient(listOf(clear, c), startY = size.height - w, endY = size.height),
        topLeft = Offset(0f, size.height - w),
        size = Size(size.width, w)
    )
    drawRect(Brush.horizontalGradient(listOf(c, clear), startX = 0f, endX = w), size = Size(w, size.height))
    drawRect(
        Brush.horizontalGradient(listOf(clear, c), startX = size.width - w, endX = size.width),
        topLeft = Offset(size.width - w, 0f),
        size = Size(w, size.height)
    )
}
