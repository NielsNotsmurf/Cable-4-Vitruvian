package nl.nielsnotsmurf.cleanui.ui.components

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Plays an exercise demo inside the app, looping like the classic screens do.
 *
 * The demo links are Mux streams: most are .mp4, but tutorials are HLS (.m3u8), which a phone
 * browser won't play when handed the link. MediaPlayer plays both.
 */
@Composable
fun DemoVideo(url: String, modifier: Modifier = Modifier) {
    var loading by remember(url) { mutableStateOf(true) }
    var failed by remember(url) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1F2937)),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                DemoVideoView(
                    ctx,
                    url,
                    onReady = { loading = false },
                    onError = { loading = false; failed = true }
                )
            },
            onRelease = { it.release() },
            modifier = Modifier.fillMaxSize()
        )
        if (loading && !failed) CircularProgressIndicator(Modifier.size(32.dp), color = Color.White)
        if (failed) {
            Text(
                "Couldn't load this video. Check your internet connection.",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Draws through a TextureView rather than VideoView's SurfaceView. A SurfaceView is a separate
 * window layer, and when navigation removed the screen mid-transition it stayed behind and hid
 * the screen underneath, leaving only the bottom bar visible.
 */
private class DemoVideoView(
    context: Context,
    private val url: String,
    private val onReady: () -> Unit,
    private val onError: () -> Unit
) : TextureView(context), TextureView.SurfaceTextureListener {

    private var player: MediaPlayer? = null
    private var surface: Surface? = null

    init {
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        val s = Surface(texture).also { surface = it }
        player = MediaPlayer().apply {
            setSurface(s)
            isLooping = true
            setVolume(0f, 0f)
            setOnPreparedListener { mp ->
                fitVideo(mp.videoWidth, mp.videoHeight)
                mp.start()
                onReady()
            }
            setOnVideoSizeChangedListener { _, w, h -> fitVideo(w, h) }
            setOnErrorListener { _, _, _ ->
                onError()
                true
            }
            try {
                setDataSource(url)
                prepareAsync()
            } catch (e: Exception) {
                onError()
            }
        }
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        player?.let { fitVideo(it.videoWidth, it.videoHeight) }
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        release()
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    fun release() {
        player?.release()
        player = null
        surface?.release()
        surface = null
    }

    /** Letterboxes the video inside the view instead of stretching it. */
    private fun fitVideo(videoW: Int, videoH: Int) {
        if (videoW <= 0 || videoH <= 0 || width <= 0 || height <= 0) return
        val scale = minOf(width.toFloat() / videoW, height.toFloat() / videoH)
        val sx = videoW * scale / width
        val sy = videoH * scale / height
        setTransform(Matrix().apply { setScale(sx, sy, width / 2f, height / 2f) })
    }
}
