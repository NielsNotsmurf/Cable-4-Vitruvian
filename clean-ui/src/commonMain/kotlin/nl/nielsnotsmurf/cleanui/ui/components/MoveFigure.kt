package nl.nielsnotsmurf.cleanui.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import nl.nielsnotsmurf.cleanui.logic.Move
import nl.nielsnotsmurf.cleanui.theme.CleanColors

/** A point in the figure's 100 x 110 box (x right, y down, floor at 97). */
private data class P(val x: Float, val y: Float)

private fun p(x: Int, y: Int) = P(x.toFloat(), y.toFloat())

/** Where the cable from the trainer is hooked on. */
private enum class Hook { HANDS, FOOT, HIP }

/**
 * One frame of the figure. Side view faces right; front view spreads the shoulders and hips.
 * L and R are the two arms and legs. In side view both arms follow L; the legs can differ (lunge, kickback).
 */
private data class Pose(
    val head: P, val sh: P, val hip: P,
    val eL: P, val hL: P, val eR: P = eL, val hR: P = hL,
    val kL: P, val fL: P, val kR: P = kL, val fR: P = fL
)

private class Anim(val start: Pose, val end: Pose, val front: Boolean = false, val bench: Boolean = false, val hook: Hook = Hook.HANDS)

private val stand = Pose(
    head = p(50, 18), sh = p(50, 28), hip = p(50, 55),
    eL = p(50, 42), hL = p(51, 56), kL = p(50, 75), fL = p(50, 95)
)
private val hinged = Pose(p(74, 36), p(66, 40), p(42, 55), stand.eL, stand.hL, kL = p(48, 74), fL = p(50, 95))
private val lying = Pose(p(20, 67), p(30, 70), p(60, 70), stand.eL, stand.hL, kL = p(74, 62), fL = p(80, 95))
private val split = stand.copy(kL = p(44, 75), fL = p(36, 95), kR = p(56, 75), fR = p(62, 95))
private val frontLegs = stand.copy(kL = p(46, 75), fL = p(45, 95), kR = p(54, 75), fR = p(55, 95))
private val kneel = Pose(p(50, 36), p(50, 46), p(50, 72), stand.eL, stand.hL, kL = p(58, 95), fL = p(36, 95))

private fun anim(move: Move): Anim = when (move) {
    Move.SQUAT -> Anim(
        stand.copy(eL = p(58, 36), hL = p(54, 28)),
        Pose(p(58, 38), p(54, 47), p(36, 67), p(62, 55), p(58, 47), kL = p(58, 72), fL = p(50, 95))
    )
    Move.HINGE -> Anim(stand, hinged.copy(eL = p(67, 54), hL = p(67, 68)))
    Move.LUNGE -> Anim(
        split,
        split.copy(head = p(50, 29), sh = p(50, 39), hip = p(50, 66), eL = p(50, 53), hL = p(51, 67), kL = p(40, 90), fL = p(28, 95), kR = p(64, 70), fR = p(64, 95))
    )
    Move.OVERHEAD_PRESS -> Anim(stand.copy(eL = p(57, 37), hL = p(55, 25)), stand.copy(eL = p(53, 14), hL = p(52, 1)))
    Move.BENCH_PRESS -> Anim(lying.copy(eL = p(32, 79), hL = p(33, 64)), lying.copy(eL = p(31, 56), hL = p(31, 42)), bench = true)
    Move.PULLOVER -> Anim(lying.copy(eL = p(16, 62), hL = p(5, 66)), lying.copy(eL = p(31, 56), hL = p(32, 42)), bench = true)
    Move.ROW -> Anim(hinged.copy(eL = p(66, 54), hL = p(66, 68)), hinged.copy(eL = p(54, 44), hL = p(62, 56)))
    Move.ARM_KICKBACK -> Anim(hinged.copy(eL = p(54, 46), hL = p(57, 60)), hinged.copy(eL = p(54, 46), hL = p(38, 50)))
    Move.HIGH_PULL -> Anim(stand.copy(eL = p(62, 36), hL = p(75, 40)), stand.copy(eL = p(42, 27), hL = p(58, 20)))
    Move.CURL -> Anim(stand, stand.copy(hL = p(59, 30)))
    Move.FRONT_RAISE -> Anim(stand, stand.copy(eL = p(63, 28), hL = p(77, 26)))
    Move.OVERHEAD_EXTENSION -> Anim(stand.copy(eL = p(54, 8), hL = p(42, 18)), stand.copy(eL = p(54, 8), hL = p(56, -6)))
    Move.UPRIGHT_ROW -> Anim(
        frontLegs.copy(eL = p(42, 42), hL = p(47, 56), eR = p(58, 42), hR = p(53, 56)),
        frontLegs.copy(eL = p(32, 28), hL = p(47, 32), eR = p(68, 28), hR = p(53, 32)),
        front = true
    )
    Move.LATERAL_RAISE -> Anim(
        frontLegs.copy(eL = p(41, 42), hL = p(40, 55), eR = p(59, 42), hR = p(60, 55)),
        frontLegs.copy(eL = p(29, 30), hL = p(16, 29), eR = p(71, 30), hR = p(84, 29)),
        front = true
    )
    Move.FLY -> Anim(
        frontLegs.copy(eL = p(30, 31), hL = p(17, 38), eR = p(70, 31), hR = p(83, 38)),
        frontLegs.copy(eL = p(38, 39), hL = p(48, 47), eR = p(62, 39), hR = p(52, 47)),
        front = true
    )
    Move.REAR_FLY -> Anim(
        frontLegs.copy(eL = p(40, 42), hL = p(48, 50), eR = p(60, 42), hR = p(52, 50)),
        frontLegs.copy(eL = p(30, 32), hL = p(16, 36), eR = p(70, 32), hR = p(84, 36)),
        front = true
    )
    Move.SHRUG -> Anim(
        frontLegs.copy(eL = p(41, 42), hL = p(42, 56), eR = p(59, 42), hR = p(58, 56)),
        frontLegs.copy(sh = p(50, 24), eL = p(41, 38), hL = p(42, 52), eR = p(59, 38), hR = p(58, 52)),
        front = true
    )
    Move.ROTATION -> Anim(
        frontLegs.copy(eL = p(36, 48), hL = p(30, 62), eR = p(42, 50), hR = p(32, 62)),
        frontLegs.copy(eL = p(62, 30), hL = p(72, 22), eR = p(66, 32), hR = p(74, 22)),
        front = true
    )
    Move.ABDUCTION -> Anim(
        frontLegs.copy(eL = p(40, 42), hL = p(42, 55), eR = p(60, 42), hR = p(58, 55)),
        frontLegs.copy(eL = p(40, 42), hL = p(42, 55), eR = p(60, 42), hR = p(58, 55), kR = p(62, 74), fR = p(72, 92)),
        front = true, hook = Hook.FOOT
    )
    Move.CALF_RAISE -> Anim(
        stand,
        stand.copy(head = p(50, 13), sh = p(50, 23), hip = p(50, 50), eL = p(50, 37), hL = p(51, 51), kL = p(50, 70), fL = p(51, 91), kR = p(50, 70), fR = p(51, 91))
    )
    Move.CRUNCH -> Anim(
        kneel.copy(eL = p(56, 42), hL = p(54, 34)),
        kneel.copy(head = p(76, 70), sh = p(68, 60), eL = p(70, 58), hL = p(74, 66))
    )
    Move.HIP_THRUST -> Anim(
        Pose(p(14, 86), p(22, 89), p(46, 93), p(36, 92), p(46, 93), kL = p(66, 76), fL = p(76, 95)),
        Pose(p(14, 86), p(22, 89), p(48, 76), p(36, 84), p(48, 76), kL = p(68, 70), fL = p(76, 95)),
        hook = Hook.HIP
    )
    Move.GLUTE_KICKBACK -> Anim(
        stand.copy(kR = p(52, 75), fR = p(50, 93)),
        stand.copy(kR = p(42, 72), fR = p(27, 80)),
        hook = Hook.FOOT
    )
    Move.LEG_CURL -> Anim(stand, stand.copy(kR = p(50, 75), fR = p(33, 64)), hook = Hook.FOOT)
}

/**
 * Our own animated figure for an exercise: a stick figure doing the movement, with the cable
 * running from the trainer on the floor. Drawn in code, so it's ours and works the same on the
 * app and the web.
 */
@Composable
fun MoveFigure(move: Move, modifier: Modifier = Modifier, ink: Color = CleanColors.Ink, accent: Color = CleanColors.Accent) {
    val a = anim(move)
    val t by rememberInfiniteTransition(label = "move").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "t"
    )
    Canvas(modifier.semantics { contentDescription = "Animation of the movement" }) {
        val s = minOf(size.width / 100f, size.height / 110f)
        val ox = (size.width - 100f * s) / 2f
        val oy = (size.height - 110f * s) / 2f + 10f * s
        fun o(p: P) = Offset(ox + p.x * s, oy + p.y * s)
        fun mix(x: P, y: P) = P(x.x + (y.x - x.x) * t, x.y + (y.y - x.y) * t)
        val q = with(a) {
            Pose(
                mix(start.head, end.head), mix(start.sh, end.sh), mix(start.hip, end.hip),
                mix(start.eL, end.eL), mix(start.hL, end.hL), mix(start.eR, end.eR), mix(start.hR, end.hR),
                mix(start.kL, end.kL), mix(start.fL, end.fL), mix(start.kR, end.kR), mix(start.fR, end.fR)
            )
        }
        val w = 4.5f * s
        fun line(x: P, y: P, c: Color = ink, width: Float = w) = drawLine(c, o(x), o(y), width, StrokeCap.Round)
        val spread = if (a.front) 8 else 0
        val hipSpread = if (a.front) 5 else 0
        val shL = P(q.sh.x - spread, q.sh.y)
        val shR = P(q.sh.x + spread, q.sh.y)
        val hipL = P(q.hip.x - hipSpread, q.hip.y)
        val hipR = P(q.hip.x + hipSpread, q.hip.y)

        // Floor with the trainer, and the bench for lying moves.
        line(p(8, 97), p(92, 97), CleanColors.Line, 2f * s)
        drawRoundRect(
            CleanColors.Line,
            topLeft = o(p(34, 96)),
            size = androidx.compose.ui.geometry.Size(32f * s, 4f * s),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * s)
        )
        if (a.bench) {
            line(p(16, 76), p(66, 76), CleanColors.Muted, 3f * s)
            line(p(22, 76), p(22, 96), CleanColors.Muted, 2f * s)
            line(p(60, 76), p(60, 96), CleanColors.Muted, 2f * s)
        }

        // Cables from the trainer to where they're hooked on.
        val cable = accent.copy(alpha = 0.8f)
        val thin = 1.6f * s
        when (a.hook) {
            Hook.HANDS -> if (a.front) {
                line(p(44, 97), q.hL, cable, thin); line(p(56, 97), q.hR, cable, thin)
            } else line(p(50, 97), q.hL, cable, thin)
            Hook.FOOT -> line(p(50, 97), q.fR, cable, thin)
            Hook.HIP -> line(p(50, 97), q.hip, cable, thin)
        }

        // Body: back leg and arm a little lighter, so side views still read as two limbs.
        val back = if (a.front) ink else ink.copy(alpha = 0.45f)
        line(hipL, q.kL, back); line(q.kL, q.fL, back)
        if (a.front) { line(shL, q.eL, back); line(q.eL, q.hL, back) }
        line(q.sh, q.hip, ink, w * 1.2f)
        if (a.front) { line(shL, shR); line(hipL, hipR) }
        line(hipR, q.kR); line(q.kR, q.fR)
        val eR = if (a.front) q.eR else q.eL
        val hR = if (a.front) q.hR else q.hL
        line(shR, eR); line(eR, hR)
        drawCircle(ink, 6f * s, o(q.head))
    }
}
