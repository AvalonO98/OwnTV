package tv.own.owntv.player

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private class MpvSurfaceView(context: Context, private val player: OwnTVPlayer) :
    SurfaceView(context), SurfaceHolder.Callback {

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        player.attachSurface(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        player.setSurfaceSize(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        player.detachSurface()
    }
}

/**
 * Hosts the mpv video output (a [SurfaceView]) in Compose.
 *
 * In direct render mode the decoder fills the surface edge-to-edge (no GL letterboxing), so the
 * view itself sizes to the video's aspect ratio inside a black box — 4:3 channels stay 4:3.
 * In GL mode mpv letterboxes internally and the view simply fills the slot.
 */
@Composable
fun MpvVideoSurface(player: OwnTVPlayer, modifier: Modifier = Modifier) {
    val direct by player.directRender.collectAsStateWithLifecycle()
    val aspect by player.videoAspect.collectAsStateWithLifecycle()

    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        val a = aspect
        AndroidView(
            modifier = if (direct && a != null && a > 0f) Modifier.aspectRatio(a) else Modifier.fillMaxSize(),
            factory = { ctx -> MpvSurfaceView(ctx, player) },
        )
    }
}
