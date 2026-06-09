package com.example.vitruvianredux.data.baseline

enum class SuggestionSource(val confidence: Float, val displayLabel: String) {
    ACTUAL_HISTORY(1.00f, "Your history"),
    ADAPTIVE_CORRECTED(0.90f, "Personalized"),
    EXERCISE_PROPAGATION(0.75f, "Similar exercises"),
    BUCKET_PROPAGATION(0.60f, "Strength profile"),
    ANCHOR_CONSERVATIVE(0.40f, "Starting estimate"),
    SAFE_DEFAULT(0.20f, "Safe default")
}

data class WeightSuggestion(
    val weightKg: Float,
    val source: SuggestionSource
) {
    val confidence: Float get() = source.confidence
    val isHighConfidence: Boolean get() = confidence >= 0.75f
}
