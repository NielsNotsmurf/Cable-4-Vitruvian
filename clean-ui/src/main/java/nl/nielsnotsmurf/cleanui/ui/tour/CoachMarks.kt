package nl.nielsnotsmurf.cleanui.ui.tour

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import nl.nielsnotsmurf.cleanui.theme.CleanColors

/** The running app tour: which step shows, and where its targets are on screen. */
@Stable
class TourController(private val steps: List<TourStep> = AppTour.steps) {
    var index by mutableStateOf<Int?>(null)
        private set

    /** Bounds in the root of everything marked with [tourTarget] that is on screen now. */
    internal val targets = mutableStateMapOf<String, Rect>()

    val isRunning: Boolean get() = index != null
    val step: TourStep? get() = index?.let { steps.getOrNull(it) }
    val count: Int get() = steps.size
    val isLast: Boolean get() = index == steps.lastIndex

    fun start() { index = 0 }
    fun next() { index = index?.let { if (it < steps.lastIndex) it + 1 else null } }
    fun back() { index = index?.let { (it - 1).coerceAtLeast(0) } }
    fun stop() { index = null }
}

val LocalTour = compositionLocalOf<TourController?> { null }

/** Marks this element so the tour can point at it under [key] (see [TourTargets]). */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tourTarget(key: String): Modifier = composed {
    val tour = LocalTour.current ?: return@composed this
    val requester = remember { BringIntoViewRequester() }
    val pointedAt = tour.step?.target == key
    LaunchedEffect(pointedAt) {
        if (pointedAt) {
            delay(80)
            requester.bringIntoView()
        }
    }
    DisposableEffect(tour, key) { onDispose { tour.targets.remove(key) } }
    this
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { tour.targets[key] = it.boundsInRoot() }
}

/**
 * Dims the screen except the current step's target, with an arrow from a card to it.
 * Taps outside the card do nothing, so the tour can't be left half way by accident.
 */
@Composable
fun TourOverlay(tour: TourController, onFinish: (completed: Boolean) -> Unit) {
    val step = tour.step ?: return
    val index = tour.index ?: return
    var origin by remember { mutableStateOf(Offset.Zero) }
    // Wait for the screen to settle (navigation, scrolling) before cutting out the target.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(index) {
        settled = false
        delay(450)
        settled = true
    }
    val target = step.target?.let { tour.targets[it] }?.takeIf { settled }?.translate(-origin.x, -origin.y)
    val accent = CleanColors.Accent

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            drawRect(Color.Black.copy(alpha = 0.66f))
            if (target != null) {
                val pad = 6.dp.toPx()
                val hole = target.inflate(pad)
                val radius = CornerRadius(16.dp.toPx())
                drawRoundRect(Color.Black, hole.topLeft, hole.size, radius, blendMode = BlendMode.Clear)
                drawRoundRect(accent, hole.topLeft, hole.size, radius, style = Stroke(3.dp.toPx()))
            }
        }
        val visible = target != null || step.target == null || settled
        if (visible) {
            CardWithArrow(target = target) {
                TourCard(
                    step = step,
                    number = index + 1,
                    count = tour.count,
                    isLast = tour.isLast,
                    onSkip = { tour.stop(); onFinish(false) },
                    onBack = if (index > 0) tour::back else null,
                    onNext = {
                        if (tour.isLast) {
                            tour.stop()
                            onFinish(true)
                        } else {
                            tour.next()
                        }
                    }
                )
            }
        }
    }
}

/** Places the card below the target when it sits in the top half, above it otherwise, with an arrow between. */
@Composable
private fun CardWithArrow(target: Rect?, card: @Composable () -> Unit) {
    val accent = CleanColors.Accent
    Layout(
        content = {
            Box(Modifier.padding(horizontal = 16.dp)) { card() }
            Canvas(Modifier.size(28.dp, 40.dp)) {
                // An arrow pointing up at the target; the layout flips it when the card sits above.
                val path = Path()
                val stroke = 4.dp.toPx()
                drawLine(accent, Offset(size.width / 2, stroke), Offset(size.width / 2, size.height - stroke), stroke, StrokeCap.Round)
                path.moveTo(size.width / 2, 0f)
                path.lineTo(0f, size.height * 0.42f)
                path.lineTo(size.width, size.height * 0.42f)
                path.close()
                drawPath(path, accent)
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { measurables, constraints ->
        val cardPlaceable = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val arrowPlaceable = measurables[1].measure(Constraints())
        val margin = 12.dp.roundToPx()
        layout(constraints.maxWidth, constraints.maxHeight) {
            val cardX = (constraints.maxWidth - cardPlaceable.width) / 2
            if (target == null) {
                cardPlaceable.place(cardX, (constraints.maxHeight - cardPlaceable.height) / 2)
                return@layout
            }
            val below = target.center.y < constraints.maxHeight / 2f
            val arrowX = (target.center.x.toInt() - arrowPlaceable.width / 2)
                .coerceIn(margin, constraints.maxWidth - arrowPlaceable.width - margin)
            if (below) {
                val arrowY = target.bottom.toInt() + margin
                arrowPlaceable.place(arrowX, arrowY)
                val cardY = (arrowY + arrowPlaceable.height).coerceAtMost(constraints.maxHeight - cardPlaceable.height)
                cardPlaceable.place(cardX, cardY)
            } else {
                val arrowY = target.top.toInt() - margin - arrowPlaceable.height
                // Flip the arrow so its head points down at the target.
                arrowPlaceable.placeWithLayer(arrowX, arrowY) { rotationZ = 180f }
                val cardY = (arrowY - cardPlaceable.height).coerceAtLeast(0)
                cardPlaceable.place(cardX, cardY)
            }
        }
    }
}

@Composable
private fun TourCard(
    step: TourStep,
    number: Int,
    count: Int,
    isLast: Boolean,
    onSkip: () -> Unit,
    onBack: (() -> Unit)?,
    onNext: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CleanColors.Background)
            .padding(start = 18.dp, end = 10.dp, top = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "$number of $count",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = CleanColors.AccentInk
        )
        Text(step.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 8.dp))
        Text(step.body, style = MaterialTheme.typography.bodyMedium, color = CleanColors.Ink, modifier = Modifier.padding(end = 8.dp))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!isLast) TextButton(onClick = onSkip) { Text("Skip tour", color = CleanColors.Muted) }
            Spacer(Modifier.weight(1f))
            if (onBack != null) TextButton(onClick = onBack) { Text("Back", color = CleanColors.Ink) }
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(CleanColors.Accent)
                    .clickable(role = Role.Button, onClick = onNext)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (isLast) "Done" else "Next", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
