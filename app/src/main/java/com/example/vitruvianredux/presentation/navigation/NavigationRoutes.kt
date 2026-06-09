package com.example.vitruvianredux.presentation.navigation

sealed class NavigationRoutes(val route: String) {
    object Home : NavigationRoutes("home")
    object JustLift : NavigationRoutes("just_lift")
    object SingleExercise : NavigationRoutes("single_exercise")
    object ActiveWorkout : NavigationRoutes("active_workout")
    object Analytics : NavigationRoutes("analytics")
    object Settings : NavigationRoutes("settings")
    object ConnectionLogs : NavigationRoutes("connection_logs")
    object ProtocolTester : NavigationRoutes("protocol_tester")
    object Profiles : NavigationRoutes("profiles")
    object MuscleGroupTraining : NavigationRoutes("muscle_group_training")
    object BaselineAssessment : NavigationRoutes("baseline_assessment")
}

enum class BottomNavItem(val route: String, val label: String) {
    WORKOUT("home", "Workout"),
    ANALYTICS("analytics", "Analytics"),
    SETTINGS("settings", "Settings")
}
