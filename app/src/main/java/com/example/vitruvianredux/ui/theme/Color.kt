package com.example.vitruvianredux.ui.theme

import androidx.compose.ui.graphics.Color

// Cable monochrome palette: the legacy names below are kept so callers stay unchanged.
// Background Colors
val BackgroundBlack = Color(0xFF000000)          // Pure black background
val BackgroundDarkGrey = Color(0xFF121212)       // Dark grey surface
val SurfaceDarkGrey = Color(0xFF1E1E1E)         // Elevated surfaces
val CardBackground = Color(0xFF252525)           // Card backgrounds

// Light Theme Colors
val ColorLightBackground = Color(0xFFF8F9FB)     // Soft light background
val ColorOnLightBackground = Color(0xFF0F172A)   // Slate-900 like text
val ColorLightSurface = Color(0xFFFFFFFF)        // White surface
val ColorOnLightSurface = Color(0xFF111827)      // Dark text on surface
val ColorLightSurfaceVariant = Color(0xFFF3F4F6) // Light gray surface variant
val ColorOnLightSurfaceVariant = Color(0xFF6B7280) // Gray-500 text
// Accent colours: white on dark, black on light
val PrimaryPurpleDark = Color(0xFFFFFFFF)
val SecondaryPurpleDark = Color(0xFF3A3A3A)
val TertiaryPurpleDark = Color(0xFF2A2A2A)
val PurpleAccentDark = Color(0xFF2E2E2E)
val PrimaryBlueLight = Color(0xFF000000)
val SecondaryBlueLight = Color(0xFF333333)
val TertiaryBlueLight = Color(0xFF000000)
val BlueAccentLight = Color(0xFF000000)
val TopAppBarDark = Color(0xFF000000)
val TopAppBarLight = Color(0xFF000000)          // Darker purple for light mode header

// Text Colors
val TextPrimary = Color(0xFFFFFFFF)             // Pure white text
val TextSecondary = Color(0xFFE0E0E0)           // Light grey text
val TextTertiary = Color(0xFFB0B0B0)            // Medium grey text
val TextDisabled = Color(0xFF707070)            // Disabled text

// Status Colors
val SuccessGreen = Color(0xFF4CAF50)            // Success states
val ErrorRed = Color(0xFFF44336)                // Error states
val WarningOrange = Color(0xFFFF9800)           // Warning states
val InfoBlue = Color(0xFF2196F3)                // Info states

// Legacy colors (kept for compatibility) - default to dark mode variants
val Purple80 = PrimaryPurpleDark
val PurpleGrey80 = SecondaryPurpleDark
val Pink80 = TertiaryPurpleDark
val Purple40 = PurpleAccentDark
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)