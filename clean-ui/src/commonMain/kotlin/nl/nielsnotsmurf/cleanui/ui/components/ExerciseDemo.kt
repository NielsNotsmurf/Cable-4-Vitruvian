package nl.nielsnotsmurf.cleanui.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import nl.nielsnotsmurf.cleanui.logic.ExerciseMove
import nl.nielsnotsmurf.cleanui.logic.ExerciseMoves
import nl.nielsnotsmurf.cleanui.model.ExerciseGuide
import nl.nielsnotsmurf.cleanui.theme.CleanColors

/** True when there's something to show: our own clip, an animated movement, or a body region to light up. */
val ExerciseGuide.hasDemo: Boolean get() = videoUrl != null || region.isNotEmpty() || ExerciseMoves.of(name) != null

/**
 * The exercise demo on the app and the web. Our own clip when there is one. Otherwise our own
 * animated figure doing the movement, next to free start/end photos (public domain,
 * free-exercise-db) when that library has the exercise. Exercises we can't place keep the body
 * map with the trained region pulsing. The official Vitruvian videos are not used: they are
 * not ours to redistribute (Play policy).
 */
@Composable
fun ExerciseDemo(guide: ExerciseGuide, modifier: Modifier = Modifier) {
    val clip = guide.videoUrl
    if (clip != null) {
        DemoVideo(clip, modifier, guide.thumbnailUrl)
        return
    }
    val move = remember(guide.name) { ExerciseMoves.of(guide.name) }
    val transition = rememberInfiniteTransition(label = "demo")
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CleanColors.Surface)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (move != null) {
            Row(
                Modifier.fillMaxWidth().height(200.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MoveFigure(move.move, Modifier.weight(1f).fillMaxHeight())
                val photos = move.photos
                if (photos.isNotEmpty()) {
                    // Start and end photo, swapped back and forth like a two-frame gif.
                    val frame by transition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
                        label = "frame"
                    )
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        RemoteImage(photos[if (frame < 0.5f) 0 else 1], "Photo of ${guide.name}", Modifier.fillMaxSize(), ContentScale.Fit)
                    }
                }
            }
        } else {
            val pulse by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
                label = "pulse"
            )
            val side = if (guide.region == "Back") BodySide.BACK else BodySide.FRONT
            BodyMap(
                side = side,
                fills = mapOf(guide.region to CleanColors.Accent.copy(alpha = pulse)),
                labels = emptyMap(),
                selected = null,
                onRegion = {},
                modifier = Modifier.heightIn(max = 220.dp),
                description = "Works the ${guide.region.lowercase()}"
            )
        }
        val muscles = guide.muscles.ifEmpty { listOfNotNull(guide.region.ifEmpty { null }) }
        if (muscles.isNotEmpty()) {
            Text("Works: " + muscles.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = CleanColors.Muted)
        }
        if (move?.photoId != null) {
            Text(ExerciseMove.PHOTO_CREDIT, style = MaterialTheme.typography.labelSmall, color = CleanColors.Muted)
        }
    }
}
