package nl.nielsnotsmurf.cleanui.logic

import kotlin.math.abs
import kotlin.math.ceil

/**
 * Ends an AMRAP (to failure) set, with the same rules and thresholds as the Android app's
 * auto-stop. It only arms once a real range of motion (50 mm) has been seen, so it never
 * stops a set before the lifter has actually reached the bottom and top of a rep.
 *
 * - Failure: the handles stand still (< 2.5 mm/s) for 5 s while out of the machine, e.g. a
 *   bench press stuck halfway or resting on the chest. Clear movement (> 10 mm/s) resets it.
 * - Done: the handles are back at the bottom of the learned range for 2.5 s.
 *
 * Feed it every sample; [update] returns the seconds left before the stop (null when no
 * countdown runs) and [shouldStop] turns true once the set must end. The caller then sends the
 * trainer's stop/reset so the cables let go of the load.
 */
class FailureDetector {
    private var minPos = Float.MAX_VALUE
    private var maxPos = -Float.MAX_VALUE
    private var lastPos: Float? = null
    private var lastAt = 0L
    private var stallSince: Long? = null
    private var restSince: Long? = null

    var shouldStop = false
        private set

    fun reset() {
        minPos = Float.MAX_VALUE; maxPos = -Float.MAX_VALUE
        lastPos = null; lastAt = 0L; stallSince = null; restSince = null; shouldStop = false
    }

    /** [position] is the furthest-out cable in mm, [nowMs] a monotonic clock. */
    fun update(position: Float, nowMs: Long): Int? {
        if (shouldStop) return 0
        minPos = minOf(minPos, position)
        maxPos = maxOf(maxPos, position)
        val prev = lastPos
        val dt = nowMs - lastAt
        lastPos = position
        lastAt = nowMs
        if (prev == null || dt <= 0) return null
        val velocity = abs(position - prev) / (dt / 1000f)
        val range = maxPos - minPos
        if (range < MIN_RANGE_MM) { stallSince = null; restSince = null; return null }

        if (velocity < STALL_LOW) { if (stallSince == null) stallSince = nowMs }
        else if (velocity > STALL_HIGH) stallSince = null

        val atBottom = position <= minPos + range * 0.05f && position - minPos < 10f
        restSince = if (atBottom) restSince ?: nowMs else null

        val stallLeft = stallSince?.let { STALL_SECONDS - (nowMs - it) / 1000f }
        val restLeft = restSince?.let { REST_SECONDS - (nowMs - it) / 1000f }
        val left = listOfNotNull(stallLeft, restLeft).minOrNull() ?: return null
        if (left <= 0f) { shouldStop = true; return 0 }
        return ceil(left).toInt()
    }

    companion object {
        const val MIN_RANGE_MM = 50f
        const val STALL_LOW = 2.5f
        const val STALL_HIGH = 10f
        const val STALL_SECONDS = 5f
        const val REST_SECONDS = 2.5f
    }
}
