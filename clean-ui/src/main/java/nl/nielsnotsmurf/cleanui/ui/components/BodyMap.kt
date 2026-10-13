package nl.nielsnotsmurf.cleanui.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import nl.nielsnotsmurf.cleanui.theme.CleanColors

/** Which side of the figure faces the viewer. Back shows the back region; front shows chest. */
enum class BodySide { FRONT, BACK }

/**
 * The segmented Vitruvian figure. Each region is filled with [fills] (grey when missing),
 * shows its [labels] text inside it, and taps report the region (or null for the space around).
 * Changing [side] turns the figure around.
 */
@Composable
fun BodyMap(
    side: BodySide,
    fills: Map<String, Color>,
    labels: Map<String, String>,
    selected: String?,
    onRegion: (String?) -> Unit,
    modifier: Modifier = Modifier,
    description: String = ""
) {
    val shapes = remember { ParsedBody() }
    val measurer = rememberTextMeasurer()
    // Quarter turn out, swap sides, quarter turn back in, so the figure is never drawn mirrored.
    val turn = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(side) }
    LaunchedEffect(side) {
        if (side == shown) return@LaunchedEffect
        turn.animateTo(90f, tween(160))
        shown = side
        turn.snapTo(-90f)
        turn.animateTo(0f, tween(160))
    }
    val currentOnRegion by rememberUpdatedState(onRegion)
    val regions = if (shown == BodySide.FRONT) shapes.front else shapes.back
    val labelSpots = if (shown == BodySide.FRONT) BodyMapShapes.frontLabels else BodyMapShapes.backLabels
    val neutral = if (shown == BodySide.FRONT) shapes.frontNeutral else shapes.backNeutral

    Canvas(
        modifier
            .aspectRatio(VIEW.width / VIEW.height)
            .semantics { contentDescription = description }
            .graphicsLayer {
                rotationY = turn.value
                cameraDistance = 14f * density
            }
            .pointerInput(shown) {
                detectTapGestures { tap ->
                    val k = size.width / VIEW.width
                    val p = Offset(tap.x / k + VIEW_LEFT, tap.y / k + VIEW_TOP)
                    currentOnRegion(shapes.hit(regions, p))
                }
            }
    ) {
        val k = size.width / VIEW.width
        withTransform({
            scale(k, k, pivot = Offset.Zero)
            translate(-VIEW_LEFT, -VIEW_TOP)
        }) {
            val line = 1f
            drawCircle(CleanColors.Line, BodyMapShapes.CIRCLE_RADIUS, BodyMapShapes.circleCenter, style = Stroke(line))
            drawRect(
                CleanColors.Line,
                topLeft = Offset(BodyMapShapes.SQUARE_LEFT, BodyMapShapes.SQUARE_TOP),
                size = Size(BodyMapShapes.SQUARE_SIZE, BodyMapShapes.SQUARE_SIZE),
                style = Stroke(line)
            )
            drawPath(neutral, CleanColors.Line)
            regions.forEach { (name, path) ->
                drawPath(path, fills[name] ?: CleanColors.Line)
                drawPath(path, CleanColors.Background, style = Stroke(1.2f, join = StrokeJoin.Round))
            }
            selected?.let { regions[it] }?.let { drawPath(it, CleanColors.Ink, style = Stroke(1.6f, join = StrokeJoin.Round)) }
        }
        // Text is laid out at screen size so it stays crisp.
        val fontSp = (8.5f * k / density / fontScale).sp
        labelSpots.forEach { (name, spot) ->
            val text = labels[name] ?: return@forEach
            if (name !in regions) return@forEach
            val fill = fills[name] ?: CleanColors.Line
            val color = if (fill.luminance() < 0.42f) Color.White else CleanColors.Ink
            val style = TextStyle(fontSize = fontSp, fontWeight = FontWeight.Bold, color = color)
            val maxWidth = (BodyMapShapes.labelWidths[name] ?: 30f) * k
            var layout = measurer.measure(text, style)
            if (layout.size.width > maxWidth) {
                layout = measurer.measure(text, style.copy(fontSize = fontSp * (maxWidth / layout.size.width)))
            }
            val center = Offset((spot.x - VIEW_LEFT) * k, (spot.y - VIEW_TOP) * k)
            translate(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f) {
                drawText(layout)
            }
        }
    }
}

/** Area drawn: the figure plus the Vitruvian circle around it. */
private const val VIEW_LEFT = -22f
private const val VIEW_TOP = -9f
private val VIEW = Size(BodyMapShapes.WIDTH + 2 * -VIEW_LEFT, 284f)

private class ParsedBody {
    val front: Map<String, Path> = BodyMapShapes.front.mapValues { parse(it.value) }
    val back: Map<String, Path> = BodyMapShapes.back.mapValues { parse(it.value) }
    val frontNeutral = parse(BodyMapShapes.FRONT_NEUTRAL)
    val backNeutral = parse(BodyMapShapes.BACK_NEUTRAL)

    /** Region under [p] (figure units), or null. */
    fun hit(regions: Map<String, Path>, p: Offset): String? =
        regions.entries.firstOrNull { (_, path) -> contains(path, p) }?.key

    private fun contains(path: Path, p: Offset): Boolean {
        if (!path.getBounds().contains(p)) return false
        // Regions are small in figure units; scale up so the integer region keeps its shape.
        val scaled = android.graphics.Path(path.asAndroidPath()).apply {
            transform(android.graphics.Matrix().apply { setScale(HIT_SCALE, HIT_SCALE) })
        }
        val clip = android.graphics.Region(-1000, -1000, 4000, 4000)
        val region = android.graphics.Region().apply { setPath(scaled, clip) }
        return region.contains((p.x * HIT_SCALE).toInt(), (p.y * HIT_SCALE).toInt())
    }

    private fun parse(d: String): Path = PathParser().parsePathString(d).toPath()

    private companion object {
        const val HIT_SCALE = 8f
    }
}
