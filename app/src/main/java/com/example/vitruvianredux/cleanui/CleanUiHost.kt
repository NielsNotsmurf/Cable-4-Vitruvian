package com.example.vitruvianredux.cleanui

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.example.vitruvianredux.data.preferences.PreferencesManager
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import com.example.vitruvianredux.presentation.navigation.ClassicSettingsRoute
import com.example.vitruvianredux.presentation.screen.ActiveWorkoutScreen
import com.example.vitruvianredux.presentation.screen.AnalyticsScreen
import com.example.vitruvianredux.presentation.screen.BaselineAssessmentScreen
import com.example.vitruvianredux.presentation.screen.ConnectionLogsScreen
import com.example.vitruvianredux.presentation.screen.HistoryTab
import com.example.vitruvianredux.presentation.screen.JustLiftScreen
import com.example.vitruvianredux.presentation.screen.MuscleGroupTrainingScreen
import com.example.vitruvianredux.presentation.screen.ProfileSelectionScreen
import com.example.vitruvianredux.presentation.screen.ProtocolTesterScreen
import com.example.vitruvianredux.presentation.viewmodel.MainViewModel
import com.example.vitruvianredux.ui.theme.ThemeMode
import com.example.vitruvianredux.ui.theme.VitruvianProjectPhoenixTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import nl.nielsnotsmurf.cleanui.data.CleanUiStore
import nl.nielsnotsmurf.cleanui.ui.CleanApp
import nl.nielsnotsmurf.cleanui.ui.ClassicScreen

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CleanUiEntryPoint {
    fun preferencesManager(): PreferencesManager
}

/**
 * Hosts the Clean UI inside the app. MainActivity shows this instead of the classic
 * EnhancedMainScreen when the Clean UI is switched on.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CleanUiHost(viewModel: MainViewModel) {
    val context = LocalContext.current
    val bridge = remember(viewModel) { createBridge(context, viewModel) }

    // Same permissions the classic screen asks for (Android 12+ doesn't need location).
    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }
    val permissionState = rememberMultiplePermissionsState(permissions)

    if (!permissionState.allPermissionsGranted) {
        BluetoothPermissionPrompt(onAllow = { permissionState.launchMultiplePermissionRequest() })
        return
    }

    CleanApp(
        gateway = bridge,
        // Lifting and Just Lift reuse the classic, hardware-tested screens for now.
        activeWorkoutContent = { nav ->
            VitruvianProjectPhoenixTheme(themeMode = ThemeMode.LIGHT) {
                ActiveWorkoutScreen(
                    navController = nav,
                    viewModel = viewModel,
                    exerciseRepository = viewModel.exerciseRepository
                )
            }
        },
        justLiftContent = { nav ->
            VitruvianProjectPhoenixTheme(themeMode = ThemeMode.LIGHT) {
                JustLiftScreen(navController = nav, viewModel = viewModel, themeMode = ThemeMode.LIGHT)
            }
        },
        // Every other classic feature stays reachable inside the Clean layout until its clean version lands.
        classicContent = { screen, nav ->
            VitruvianProjectPhoenixTheme(themeMode = ThemeMode.LIGHT) {
                ClassicScreenContent(screen, nav, viewModel)
            }
        }
    )
}

@Composable
private fun ClassicScreenContent(screen: ClassicScreen, nav: NavController, viewModel: MainViewModel) {
    when (screen) {
        ClassicScreen.HISTORY -> ClassicHistory(viewModel)
        ClassicScreen.ANALYTICS -> AnalyticsScreen(viewModel = viewModel, themeMode = ThemeMode.LIGHT)
        ClassicScreen.SETTINGS -> ClassicSettingsRoute(navController = nav, viewModel = viewModel)
        ClassicScreen.PROFILES -> ProfileSelectionScreen(viewModel = viewModel, onNavigateBack = { nav.popBackStack() })
        ClassicScreen.MUSCLE_GROUPS -> MuscleGroupTrainingScreen(
            navController = nav,
            viewModel = viewModel,
            exerciseRepository = viewModel.exerciseRepository
        )
        ClassicScreen.ASSESSMENT -> BaselineAssessmentScreen(navController = nav, viewModel = viewModel)
        ClassicScreen.CONNECTION_LOGS -> ConnectionLogsScreen(onNavigateBack = { nav.popBackStack() }, mainViewModel = viewModel)
        ClassicScreen.PROTOCOL_TESTER -> ProtocolTesterScreen(onNavigateBack = { nav.popBackStack() })
    }
}

/** Workout history with the same per-profile filter the classic Analytics screen has. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassicHistory(viewModel: MainViewModel) {
    val history by viewModel.analyticsGroupedHistory.collectAsState()
    val weightUnit by viewModel.weightUnit.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val selected by viewModel.analyticsProfileId.collectAsState()
    Column(Modifier.fillMaxSize()) {
        if (profiles.size > 1) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = selected == null, onClick = { viewModel.setAnalyticsProfile(null) }, label = { Text("Everyone") })
                profiles.forEach { p ->
                    FilterChip(selected = selected == p.id, onClick = { viewModel.setAnalyticsProfile(p.id) }, label = { Text(p.name) })
                }
            }
        }
        HistoryTab(
            groupedWorkoutHistory = history,
            weightUnit = weightUnit,
            formatWeight = viewModel::formatWeight,
            onDeleteWorkout = { viewModel.deleteWorkout(it) },
            exerciseRepository = viewModel.exerciseRepository,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun createBridge(context: Context, viewModel: MainViewModel): CleanUiBridge {
    val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, CleanUiEntryPoint::class.java)
    return CleanUiBridge(
        vm = viewModel,
        preferencesManager = entryPoint.preferencesManager(),
        store = CleanUiStore.get(context),
        scope = viewModel.viewModelScope
    )
}

@Composable
private fun BluetoothPermissionPrompt(onAllow: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.White).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text("Connect your trainer", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F1217))
        Text(
            "Phoenix needs Bluetooth access to find and control your trainer. Nothing leaves your phone.",
            fontSize = 16.sp,
            color = Color(0xFF6B7280)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF0F1217))
                .clickable(onClick = onAllow),
            contentAlignment = Alignment.Center
        ) {
            Text("Allow Bluetooth", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}
