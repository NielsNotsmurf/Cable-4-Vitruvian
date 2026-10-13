package nl.nielsnotsmurf.cleanui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import android.net.Uri
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import nl.nielsnotsmurf.cleanui.data.CleanUiStore
import nl.nielsnotsmurf.cleanui.gateway.TrainerGateway
import nl.nielsnotsmurf.cleanui.theme.CleanColors
import nl.nielsnotsmurf.cleanui.theme.CleanTheme

/**
 * Route names the host can rely on. [TRAIN], [ACTIVE_WORKOUT] and [JUST_LIFT] match the
 * classic routes on purpose, so classic screens hosted here keep navigating by their own names
 * (for example muscle-group training pops up to "home" when a set starts).
 */
object CleanRoutes {
    const val TRAIN = "home"
    const val EXERCISES = "clean_exercises"
    const val MUSCLE_GROUPS = "clean_muscle_groups"
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
    val context = LocalContext.current
    val vm: CleanViewModel = viewModel(factory = CleanViewModel.factory(gateway, CleanUiStore.get(context)))
    val state by vm.state.collectAsStateWithLifecycle()
    val setup by vm.setup.collectAsStateWithLifecycle()
    val test by vm.test.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val heartRate by vm.heartRate.collectAsStateWithLifecycle()
    val ledScheme by vm.ledScheme.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    // Exercises takes an optional region; without one it is the Exercises tab.
    val currentRoute = backStack?.destination?.route?.substringBefore('?')
    val isTab = TABS.any { it.route == currentRoute } &&
        !(currentRoute == CleanRoutes.EXERCISES && backStack?.arguments?.getString("region") != null)

    fun openTab(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    CleanTheme {
        Surface(color = CleanColors.Background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().edgeGlow(CleanColors.Glow).safeDrawingPadding()) {
                NavHost(
                    navController = nav,
                    startDestination = CleanRoutes.TRAIN,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(CleanRoutes.TRAIN) {
                        TodayScreen(
                            state = state,
                            history = history,
                            heartRate = heartRate,
                            onToggleLifter = vm::toggleLifter,
                            onPickExercise = { openTab(CleanRoutes.EXERCISES) },
                            onJustLift = { nav.navigate(CleanRoutes.JUST_LIFT) },
                            onOpenExercise = { vm.openSetup(it); nav.navigate(CleanRoutes.SETUP) },
                            onMuscleGroups = { nav.navigate(CleanRoutes.MUSCLE_GROUPS) },
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
                        val region = entry.arguments?.getString("region")
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
                            onMoreAnalytics = { nav.navigate(ClassicScreen.ANALYTICS.route) }
                        )
                    }
                    composable(CleanRoutes.SETTINGS) {
                        SettingsScreen(
                            state = state,
                            settings = settings,
                            ledSchemes = gateway.ledSchemes,
                            ledIndex = ledScheme,
                            onUpdate = vm::updateSettings,
                            onLedScheme = vm::setLedScheme,
                            onProfiles = { nav.navigate(CleanRoutes.PROFILES) },
                            onMoreSettings = { nav.navigate(ClassicScreen.SETTINGS.route) },
                            onConnectionLogs = { nav.navigate(ClassicScreen.CONNECTION_LOGS.route) },
                            onProtocolTester = { nav.navigate(ClassicScreen.PROTOCOL_TESTER.route) },
                            onSwitchToClassic = vm::switchToClassic
                        )
                    }
                    composable(CleanRoutes.PROFILES) {
                        var heartRateFor by rememberSaveable { mutableStateOf<String?>(null) }
                        ProfilesScreen(
                            state = state,
                            onBack = { nav.popBackStack() },
                            onToggleLifter = vm::toggleLifter,
                            onAdd = vm::addLifter,
                            onDelete = vm::deleteLifter,
                            onHeartRate = if (heartRateContent != null) ({ heartRateFor = it }) else null
                        )
                        heartRateFor?.let { id -> heartRateContent?.invoke(id) { heartRateFor = null } }
                    }
                    composable(CleanRoutes.MUSCLE_GROUPS) {
                        MuscleGroupsScreen(
                            state = state,
                            onBack = { nav.popBackStack() },
                            onRegion = { nav.navigate("${CleanRoutes.EXERCISES}?region=${Uri.encode(it)}") },
                            onTestOneRepMax = { vm.openTest(); nav.navigate(CleanRoutes.ONE_REP_MAX_TEST) }
                        )
                    }
                    composable(CleanRoutes.SETUP) {
                        val s = setup
                        if (s == null) {
                            LaunchedEffect(Unit) { nav.popBackStack() }
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
                        val close = { vm.closeTest(); nav.popBackStack(); Unit }
                        if (t == null) {
                            LaunchedEffect(Unit) { nav.popBackStack() }
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
                        val live by gateway.liveWorkout.collectAsStateWithLifecycle(null)
                        var classicView by rememberSaveable { mutableStateOf(false) }
                        var record by remember { mutableStateOf<String?>(null) }
                        LaunchedEffect(Unit) {
                            gateway.personalRecords.collectLatest { record = it; delay(4000); record = null }
                        }
                        val l = live
                        // A 1RM attempt is running while the test is open underneath.
                        val attemptMode = test != null
                        if (l == null || classicView || !l.isCleanReady()) {
                            activeWorkoutContent(nav)
                        } else {
                            LiveWorkoutScreen(
                                live = l,
                                unit = state.unit,
                                heartRate = heartRate,
                                history = history,
                                personalRecord = record,
                                attemptMode = attemptMode,
                                onSkipRest = gateway::skipRest,
                                onContinue = gateway::proceedFromSummary,
                                onNextExercise = gateway::advanceToNextExercise,
                                onStop = { gateway.stopWorkout(); nav.popBackStack() },
                                onDone = { nav.popBackStack(); gateway.resetForNewWorkout() },
                                onClassicView = { classicView = true }
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
                if (isTab) {
                    TabBar(currentRoute, onSelect = ::openTab)
                }
            }
        }
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

/** The round Train button in the middle of the tab bar, raised a little above it. */
@Composable
private fun TrainButton(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.heightIn(min = 52.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .offset(y = (-12).dp)
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
