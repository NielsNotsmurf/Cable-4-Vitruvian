package nl.nielsnotsmurf.cleanui.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import nl.nielsnotsmurf.cleanui.data.CleanUiStore
import nl.nielsnotsmurf.cleanui.gateway.TrainerGateway
import nl.nielsnotsmurf.cleanui.theme.CleanColors
import nl.nielsnotsmurf.cleanui.theme.CleanTheme
import nl.nielsnotsmurf.cleanui.ui.components.GhostButton
import nl.nielsnotsmurf.cleanui.ui.components.SectionLabel

/**
 * Route names the host can rely on. [ACTIVE_WORKOUT] and [JUST_LIFT] match the classic
 * routes on purpose, so classic screens hosted here can keep navigating by their own names.
 */
object CleanRoutes {
    const val TODAY = "clean_today"
    const val EXERCISES = "clean_exercises"
    const val SETUP = "clean_setup"
    const val SETTINGS = "clean_settings"
    const val ACTIVE_WORKOUT = "active_workout"
    const val JUST_LIFT = "just_lift"
}

/**
 * Entry point of the Clean UI.
 *
 * Lifting and Just Lift are slots for now: the host passes the existing classic screens
 * until the clean versions land, so the BLE workout flow stays exactly as tested.
 */
@Composable
fun CleanApp(
    gateway: TrainerGateway,
    activeWorkoutContent: @Composable (NavController) -> Unit,
    justLiftContent: @Composable (NavController) -> Unit
) {
    val context = LocalContext.current
    val vm: CleanViewModel = viewModel(factory = CleanViewModel.factory(gateway, CleanUiStore.get(context)))
    val state by vm.state.collectAsStateWithLifecycle()
    val setup by vm.setup.collectAsStateWithLifecycle()
    val nav = rememberNavController()

    CleanTheme {
        Surface(color = CleanColors.Background, modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = nav,
                startDestination = CleanRoutes.TODAY,
                modifier = Modifier.safeDrawingPadding()
            ) {
                composable(CleanRoutes.TODAY) {
                    TodayScreen(
                        state = state,
                        onToggleLifter = vm::toggleLifter,
                        onPickExercise = { nav.navigate(CleanRoutes.EXERCISES) },
                        onJustLift = { nav.navigate(CleanRoutes.JUST_LIFT) },
                        onOpenExercise = { vm.openSetup(it); nav.navigate(CleanRoutes.SETUP) },
                        onOpenSettings = { nav.navigate(CleanRoutes.SETTINGS) },
                        onRowShown = vm::requestEstimates
                    )
                }
                composable(CleanRoutes.EXERCISES) {
                    ExercisesScreen(
                        state = state,
                        onBack = { nav.popBackStack() },
                        onOpenExercise = { vm.openSetup(it); nav.navigate(CleanRoutes.SETUP) },
                        onRowShown = vm::requestEstimates
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
                            onStart = {
                                vm.start {
                                    nav.navigate(CleanRoutes.ACTIVE_WORKOUT) {
                                        popUpTo(CleanRoutes.TODAY)
                                    }
                                }
                            }
                        )
                    }
                }
                composable(CleanRoutes.SETTINGS) {
                    CleanSettingsScreen(
                        onBack = { nav.popBackStack() },
                        onSwitchToClassic = vm::switchToClassic
                    )
                }
                composable(CleanRoutes.ACTIVE_WORKOUT) { activeWorkoutContent(nav) }
                composable(CleanRoutes.JUST_LIFT) { justLiftContent(nav) }
            }
        }
    }
}

@Composable
private fun CleanSettingsScreen(onBack: () -> Unit, onSwitchToClassic: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Settings", style = MaterialTheme.typography.headlineLarge)
        }
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionLabel("Layout")
            Text(
                "History, analytics, profiles and backups are still in the classic layout. " +
                    "You can switch back here and return from classic Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = CleanColors.Muted
            )
            GhostButton("Switch to classic layout", onClick = onSwitchToClassic, modifier = Modifier.fillMaxWidth())
        }
    }
}
