package nl.nielsnotsmurf.cleanui.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
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
 * browser won't play when handed the link. VideoView plays both.
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
                FrameLayout(ctx).apply {
                    addView(
                        VideoView(ctx).apply {
                            setOnPreparedListener { mp ->
                                loading = false
                                mp.isLooping = true
                                mp.setVolume(0f, 0f)
                                start()
                            }
                            setOnErrorListener { _, _, _ ->
                                loading = false
                                failed = true
                                true
                            }
                            setVideoURI(Uri.parse(url))
                        },
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            android.view.Gravity.CENTER
                        )
                    )
                }
            },
            onRelease = { frame -> (frame.getChildAt(0) as? VideoView)?.stopPlayback() },
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
