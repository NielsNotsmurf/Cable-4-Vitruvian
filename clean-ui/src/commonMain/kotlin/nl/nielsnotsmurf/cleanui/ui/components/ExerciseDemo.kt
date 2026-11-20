package nl.nielsnotsmurf.cleanui.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import nl.nielsnotsmurf.cleanui.model.ExerciseGuide
import nl.nielsnotsmurf.cleanui.theme.CleanColors

/** True when there's something to show: our own clip, or a body region to light up. */
val ExerciseGuide.hasDemo: Boolean get() = videoUrl != null || region.isNotEmpty()

/**
 * The exercise demo on the app and the web. Our own clip when there is one; otherwise the
 * figure with the trained region pulsing, like a muscle working, and the muscles named under it.
 * The official Vitruvian videos are not used: they are not ours to redistribute (Play policy).
 */
@Composable
fun ExerciseDemo(guide: ExerciseGuide, modifier: Modifier = Modifier) {
    val clip = guide.videoUrl
    if (clip != null) {
        DemoVideo(clip, modifier, guide.thumbnailUrl)
        return
    }
    val pulse by rememberInfiniteTransition(label = "demo").animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val side = if (guide.region == "Back") BodySide.BACK else BodySide.FRONT
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CleanColors.Surface)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BodyMap(
            side = side,
            fills = mapOf(guide.region to CleanColors.Accent.copy(alpha = pulse)),
            labels = emptyMap(),
            selected = null,
            onRegion = {},
            modifier = Modifier.heightIn(max = 220.dp),
            description = "Works the ${guide.region.lowercase()}"
        )
        val muscles = guide.muscles.ifEmpty { listOf(guide.region) }
        Text("Works: " + muscles.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = CleanColors.Muted)
    }
}
