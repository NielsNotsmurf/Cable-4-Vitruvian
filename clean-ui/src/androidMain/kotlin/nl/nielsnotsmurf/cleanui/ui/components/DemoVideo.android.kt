package nl.nielsnotsmurf.cleanui.ui.components

import android.content.Context
import android.graphics.Matrix
import android.view.TextureView
import androidx.annotation.OptIn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import coil.compose.AsyncImage
import java.io.File

/**
 * Plays an exercise demo inside the app, looping like the classic screens do.
 *
 * Loads fast because clips are cached on disk after the first view, every clip plays through
 * Mux's adaptive stream capped at preview size (instead of the full-size high.mp4), and playback
 * starts after a short buffer. The thumbnail shows while the first frames load.
 */
@Composable
actual fun DemoVideo(url: String, modifier: Modifier, thumbnailUrl: String?) {
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
        if (loading && !failed) {
            val poster = thumbnailUrl ?: muxThumbnail(url)
            if (poster != null) {
                AsyncImage(
                    model = poster,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            CircularProgressIndicator(Modifier.size(32.dp), color = Color.White)
        }
        if (failed) {
            Text(
                "Couldn't load this video. Check your internet connection.",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/** One disk cache for every demo clip, so a clip seen before plays straight from storage. */
@OptIn(UnstableApi::class)
private object DemoVideoCache {
    @Volatile private var cache: SimpleCache? = null

    fun get(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(context.cacheDir, "demo-videos"),
            LeastRecentlyUsedCacheEvictor(200L * 1024 * 1024),
            StandaloneDatabaseProvider(context.applicationContext)
        ).also { cache = it }
    }
}

/**
 * Draws through a TextureView rather than a SurfaceView. A SurfaceView is a separate window
 * layer, and when navigation removed the screen mid-transition it stayed behind and hid the
 * screen underneath, leaving only the bottom bar visible.
 */
@OptIn(UnstableApi::class)
private class DemoVideoView(
    context: Context,
    url: String,
    private val onReady: () -> Unit,
    private val onError: () -> Unit
) : TextureView(context), Player.Listener {

    private var videoW = 0
    private var videoH = 0
    private var reported = false

    private val player: ExoPlayer = run {
        val dataSource = CacheDataSource.Factory()
            .setCache(DemoVideoCache.get(context))
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        // Start after a short buffer instead of the default 2.5 s; demos are short loops.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2_000, 15_000, 500, 1_000)
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setLoadControl(loadControl)
            .build()
            .apply {
                // A preview is a small card: SD is plenty and downloads several times faster.
                trackSelectionParameters = trackSelectionParameters.buildUpon().setMaxVideoSizeSd().build()
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                setVideoTextureView(this@DemoVideoView)
                addListener(this@DemoVideoView)
                setMediaItem(MediaItem.fromUri(previewStreamUrl(url)))
                playWhenReady = true
                prepare()
            }
    }

    override fun onRenderedFirstFrame() {
        if (!reported) { reported = true; onReady() }
    }

    override fun onPlayerError(error: PlaybackException) {
        onError()
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        videoW = videoSize.width
        videoH = videoSize.height
        fitVideo()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitVideo()
    }

    fun release() {
        player.removeListener(this)
        player.clearVideoTextureView(this)
        player.release()
    }

    /** Letterboxes the video inside the view instead of stretching it. */
    private fun fitVideo() {
        if (videoW <= 0 || videoH <= 0 || width <= 0 || height <= 0) return
        val scale = minOf(width.toFloat() / videoW, height.toFloat() / videoH)
        val sx = videoW * scale / width
        val sy = videoH * scale / height
        setTransform(Matrix().apply { setScale(sx, sy, width / 2f, height / 2f) })
    }
}
