package nl.nielsnotsmurf.cleanui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
    ANALYTICS("analytics", "Analytics"),
    SETTINGS("settings", "More settings", showBack = true),
    ASSESSMENT("baseline_assessment", null),
    CONNECTION_LOGS("connection_logs", "Connection logs", showBack = true),
    PROTOCOL_TESTER("protocol_tester", null)
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(CleanRoutes.TRAIN, "Train", Icons.Outlined.FitnessCenter),
    Tab(CleanRoutes.HISTORY, "History", Icons.Outlined.History),
    Tab(ClassicScreen.ANALYTICS.route, "Analytics", Icons.AutoMirrored.Outlined.ShowChart),
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
    classicContent: @Composable (ClassicScreen, NavController) -> Unit
) {
    val context = LocalContext.current
    val vm: CleanViewModel = viewModel(factory = CleanViewModel.factory(gateway, CleanUiStore.get(context)))
    val state by vm.state.collectAsStateWithLifecycle()
    val setup by vm.setup.collectAsStateWithLifecycle()
    val test by vm.test.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    fun openTab(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    CleanTheme {
        Surface(color = CleanColors.Background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                NavHost(
                    navController = nav,
                    startDestination = CleanRoutes.TRAIN,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(CleanRoutes.TRAIN) {
                        TodayScreen(
                            state = state,
                            onToggleLifter = vm::toggleLifter,
                            onPickExercise = { nav.navigate(CleanRoutes.EXERCISES) },
                            onJustLift = { nav.navigate(CleanRoutes.JUST_LIFT) },
                            onOpenExercise = { vm.openSetup(it); nav.navigate(CleanRoutes.SETUP) },
                            onMuscleGroups = { nav.navigate(CleanRoutes.MUSCLE_GROUPS) },
                            onAssessment = { vm.openTest(); nav.navigate(CleanRoutes.ONE_REP_MAX_TEST) },
                            onManageProfiles = { nav.navigate(CleanRoutes.PROFILES) },
                            onRowShown = vm::requestEstimates
                        )
                    }
                    composable(
                        "${CleanRoutes.EXERCISES}?region={region}",
                        arguments = listOf(navArgument("region") { type = NavType.StringType; nullable = true })
                    ) { entry ->
                        ExercisesScreen(
                            state = state,
                            onBack = { nav.popBackStack() },
                            onOpenExercise = { vm.openSetup(it); nav.navigate(CleanRoutes.SETUP) },
                            onRowShown = vm::requestEstimates,
                            onToggleFavorite = vm::toggleFavorite,
                            region = entry.arguments?.getString("region")
                        )
                    }
                    composable(CleanRoutes.HISTORY) {
                        HistoryScreen(state = state, history = history, onDelete = vm::deleteWorkouts)
                    }
                    composable(CleanRoutes.SETTINGS) {
                        SettingsScreen(
                            state = state,
                            settings = settings,
                            ledSchemes = gateway.ledSchemes,
                            onUpdate = vm::updateSettings,
                            onLedScheme = gateway::setLedScheme,
                            onProfiles = { nav.navigate(CleanRoutes.PROFILES) },
                            onMoreSettings = { nav.navigate(ClassicScreen.SETTINGS.route) },
                            onConnectionLogs = { nav.navigate(ClassicScreen.CONNECTION_LOGS.route) },
                            onProtocolTester = { nav.navigate(ClassicScreen.PROTOCOL_TESTER.route) },
                            onSwitchToClassic = vm::switchToClassic
                        )
                    }
                    composable(CleanRoutes.PROFILES) {
                        ProfilesScreen(
                            state = state,
                            onBack = { nav.popBackStack() },
                            onToggleLifter = vm::toggleLifter,
                            onAdd = vm::addLifter,
                            onDelete = vm::deleteLifter
                        )
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
                    composable(CleanRoutes.ACTIVE_WORKOUT) { activeWorkoutContent(nav) }
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
                if (TABS.any { it.route == currentRoute }) {
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
        Row(Modifier.fillMaxWidth().padding(6.dp)) {
            TABS.forEach { tab ->
                val selected = tab.route == currentRoute
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
