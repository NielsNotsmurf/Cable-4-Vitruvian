package com.example.vitruvianredux.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.vitruvianredux.data.repository.ExerciseRepository
import com.example.vitruvianredux.presentation.screen.*
import com.example.vitruvianredux.presentation.viewmodel.MainViewModel
import com.example.vitruvianredux.ui.theme.ThemeMode

/**
 * Main navigation graph for the app.
 * Defines all routes and their composable destinations.
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    viewModel: MainViewModel,
    exerciseRepository: ExerciseRepository,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = NavigationRoutes.Home.route,
        modifier = modifier
    ) {
        // Home screen - workout type selection
        composable(NavigationRoutes.Home.route) {
            HomeScreen(
                navController = navController,
                viewModel = viewModel,
                themeMode = themeMode
            )
        }

        // Just Lift screen - quick workout configuration
        composable(
            route = NavigationRoutes.JustLift.route,
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                )
            }
        ) {
            JustLiftScreen(
                navController = navController,
                viewModel = viewModel,
                themeMode = themeMode
            )
        }

        // Single Exercise screen - choose one exercise
        composable(NavigationRoutes.SingleExercise.route) {
            SingleExerciseScreen(
                navController = navController,
                viewModel = viewModel,
                exerciseRepository = exerciseRepository
            )
        }


        // Active Workout screen - shows workout controls during active workout
        composable(
            route = NavigationRoutes.ActiveWorkout.route,
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            }
        ) {
            ActiveWorkoutScreen(
                navController = navController,
                viewModel = viewModel,
                exerciseRepository = exerciseRepository
            )
        }

        // Analytics screen - history, PRs, trends
        composable(
            route = NavigationRoutes.Analytics.route,
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) }
        ) {
            AnalyticsScreen(
                viewModel = viewModel,
                themeMode = themeMode
            )
        }

        // Settings screen
        composable(
            route = NavigationRoutes.Settings.route,
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) }
        ) {
            ClassicSettingsRoute(navController = navController, viewModel = viewModel)
        }

        // Connection Logs screen - debug BLE connections
        composable(NavigationRoutes.ConnectionLogs.route) {
            ConnectionLogsScreen(
                onNavigateBack = { navController.popBackStack() },
                mainViewModel = viewModel
            )
        }

        // Protocol Tester screen - diagnostic tool for BLE protocol testing
        composable(
            route = NavigationRoutes.ProtocolTester.route,
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                )
            }
        ) {
            ProtocolTesterScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Muscle Group Training screen — Phase 2 workout entry
        composable(
            route = NavigationRoutes.MuscleGroupTraining.route,
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                )
            }
        ) {
            MuscleGroupTrainingScreen(
                navController = navController,
                viewModel = viewModel,
                exerciseRepository = exerciseRepository
            )
        }

        // Baseline Assessment screen — Phase 2 strength setup
        composable(
            route = NavigationRoutes.BaselineAssessment.route,
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                )
            }
        ) {
            BaselineAssessmentScreen(
                navController = navController,
                viewModel = viewModel
            )
        }

        // Profiles screen — manage local user profiles
        composable(
            route = NavigationRoutes.Profiles.route,
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300)
                )
            }
        ) {
            ProfileSelectionScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

/**
 * Classic Settings with all of its wiring. Shared by the classic graph and the Clean layout,
 * which hosts it as its Settings tab.
 */
@Composable
fun ClassicSettingsRoute(navController: NavController, viewModel: MainViewModel) {
    val weightUnit by viewModel.weightUnit.collectAsState()
    val userPreferences by viewModel.userPreferences.collectAsState()
    val isAutoConnecting by viewModel.isAutoConnecting.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val importResult by viewModel.importResult.collectAsState()
    val showImportResultDialog by viewModel.showImportResultDialog.collectAsState()
    val backupPreview by viewModel.backupPreview.collectAsState()
    val showImportPreviewDialog by viewModel.showImportPreviewDialog.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val activeProfileId by viewModel.activeProfileId.collectAsState()
    val activeProfileName = profiles.find { it.id == activeProfileId }?.name ?: "Default"

    SettingsTab(
        weightUnit = weightUnit,
        autoplayEnabled = userPreferences.autoplayEnabled,
        stopAtTop = userPreferences.stopAtTop,
        enableVideoPlayback = userPreferences.enableVideoPlayback,
        beepsEnabled = userPreferences.beepsEnabled,
        defaultRepCount = userPreferences.defaultRepCount,
        onWeightUnitChange = { viewModel.setWeightUnit(it) },
        onAutoplayChange = { viewModel.setAutoplayEnabled(it) },
        onStopAtTopChange = { viewModel.setStopAtTop(it) },
        onEnableVideoPlaybackChange = { viewModel.setEnableVideoPlayback(it) },
        onBeepsEnabledChange = { viewModel.setBeepsEnabled(it) },
        onDefaultRepCountChange = { viewModel.setDefaultRepCount(it) },
        onColorSchemeChange = { viewModel.setColorScheme(it) },
        onDeleteAllWorkouts = { viewModel.deleteAllWorkouts() },
        onNavigateToConnectionLogs = { navController.navigate(NavigationRoutes.ConnectionLogs.route) },
        onNavigateToProtocolTester = { navController.navigate(NavigationRoutes.ProtocolTester.route) },
        onNavigateToProfiles = { navController.navigate(NavigationRoutes.Profiles.route) },
        activeProfileName = activeProfileName,
        isExporting = isExporting,
        isImporting = isImporting,
        importResult = importResult,
        showImportResultDialog = showImportResultDialog,
        backupPreview = backupPreview,
        showImportPreviewDialog = showImportPreviewDialog,
        onExportData = { viewModel.exportAllData() },
        onImportData = { uri -> viewModel.importFromUri(uri) },
        onDismissImportResult = { viewModel.dismissImportResult() },
        onConfirmImport = { viewModel.confirmImport() },
        onCancelImport = { viewModel.cancelImport() },
        isAutoConnecting = isAutoConnecting,
        connectionError = connectionError,
        onClearConnectionError = { viewModel.clearConnectionError() },
        onCancelAutoConnecting = { viewModel.cancelAutoConnecting() },
        onSetTitle = { viewModel.updateTopBarTitle(it) }
    )
}
