package nl.nielsnotsmurf.cleanui.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** The app's little bops and boops. */
enum class Sfx {
    TAP, NAV, TICK, GO, REP, WARMUP_DONE, SET_DONE, REST_OVER, SUCCESS, MISS
}

/**
 * Synthesized UI sounds, so nothing has to be bundled. Each sound is rendered once into a static
 * [AudioTrack] and replayed from there. No audio focus is requested, so music keeps playing.
 */
object CleanSounds {
    /** Menu and button sounds (Settings › Menu sounds). */
    @Volatile var uiEnabled = true
    /** Countdown, rep and rest sounds (Settings › Workout beeps). */
    @Volatile var workoutEnabled = true

    private const val RATE = 44_100
    private val executor = Executors.newSingleThreadExecutor()
    private val tracks = HashMap<Sfx, AudioTrack>()

    /** A note: frequency in Hz (0 = silence), length in ms. */
    private class Note(val hz: Double, val ms: Int)

    private fun notes(sfx: Sfx): Pair<List<Note>, Float> = when (sfx) {
        Sfx.TAP -> listOf(Note(1760.0, 28)) to 0.22f
        Sfx.NAV -> listOf(Note(988.0, 45), Note(1319.0, 60)) to 0.25f
        Sfx.TICK -> listOf(Note(880.0, 110)) to 0.5f
        Sfx.GO -> listOf(Note(1319.0, 90), Note(1760.0, 220)) to 0.55f
        Sfx.REP -> listOf(Note(1175.0, 70)) to 0.45f
        Sfx.WARMUP_DONE -> listOf(Note(784.0, 90), Note(1047.0, 90), Note(1319.0, 160)) to 0.5f
        Sfx.SET_DONE -> listOf(Note(1047.0, 90), Note(1319.0, 90), Note(1568.0, 90), Note(2093.0, 220)) to 0.5f
        Sfx.REST_OVER -> listOf(Note(1568.0, 90), Note(0.0, 60), Note(1568.0, 90), Note(0.0, 60), Note(2093.0, 200)) to 0.55f
        Sfx.SUCCESS -> listOf(Note(1047.0, 100), Note(1319.0, 100), Note(1568.0, 100), Note(2093.0, 120), Note(1568.0, 80), Note(2093.0, 300)) to 0.5f
        Sfx.MISS -> listOf(Note(523.0, 140), Note(392.0, 260)) to 0.45f
    }

    /** Plays [sfx] if its kind of sound is switched on. Safe to call from any thread. */
    fun play(sfx: Sfx) {
        val ui = sfx == Sfx.TAP || sfx == Sfx.NAV
        if (if (ui) !uiEnabled else !workoutEnabled) return
        executor.execute {
            runCatching {
                val track = tracks.getOrPut(sfx) { build(sfx) }
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
                track.reloadStaticData()
                track.play()
            }
        }
    }

    private fun build(sfx: Sfx): AudioTrack {
        val (seq, volume) = notes(sfx)
        val pcm = render(seq, volume)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        return track
    }

    /** Soft square-ish blips: a sine with a touch of third harmonic, quick attack, exponential decay. */
    private fun render(seq: List<Note>, volume: Float): ShortArray {
        val total = seq.sumOf { it.ms } * RATE / 1000
        val out = ShortArray(total)
        var i = 0
        for (note in seq) {
            val n = note.ms * RATE / 1000
            for (k in 0 until n) {
                if (note.hz > 0) {
                    val t = k.toDouble() / RATE
                    val attack = min(1.0, k / (0.003 * RATE))
                    val release = min(1.0, (n - k) / (0.004 * RATE))
                    val env = attack * release * exp(-t * 18.0)
                    val w = 2 * PI * note.hz * t
                    val s = (sin(w) + 0.18 * sin(3 * w)) / 1.18
                    out[i] = (s * env * volume * Short.MAX_VALUE).toInt().toShort()
                }
                i++
            }
        }
        return out
    }
}

/**
 * Plays [Sfx.TAP] whenever something inside is tapped and handles it (a button, row, chip,
 * switch...). Taps on empty space and scrolls stay silent. Dialogs are their own windows,
 * so they need this on their own content too.
 */
fun Modifier.clickSounds(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var moved = false
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Final).changes
                .firstOrNull { it.id == down.id } ?: break
            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
            if (!change.pressed) {
                if (!moved && change.isConsumed) CleanSounds.play(Sfx.TAP)
                break
            }
        }
    }
}
