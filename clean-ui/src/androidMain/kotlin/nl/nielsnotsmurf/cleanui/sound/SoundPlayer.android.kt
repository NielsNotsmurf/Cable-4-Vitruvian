package nl.nielsnotsmurf.cleanui.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors

private val executor = Executors.newSingleThreadExecutor()
private val tracks = HashMap<Sfx, AudioTrack>()

/** Each sound lives in a static [AudioTrack], built on first use. */
internal actual fun playPcm(sfx: Sfx, render: () -> ShortArray) {
    executor.execute {
        runCatching {
            val track = tracks.getOrPut(sfx) { build(render()) }
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            track.reloadStaticData()
            track.play()
        }
    }
}

private fun build(pcm: ShortArray): AudioTrack {
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
                .setSampleRate(CleanSounds.RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(pcm.size * 2)
        .build()
    track.write(pcm, 0, pcm.size)
    return track
}
