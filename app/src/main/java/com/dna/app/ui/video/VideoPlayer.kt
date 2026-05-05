package com.dna.app.ui.video

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.RgbMatrix
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.dna.app.domain.color.ColorCorrection
import com.dna.app.domain.color.ColorCorrectionMath

/**
 * Compose wrapper around [PlayerView] that:
 *  - Streams [videoUrl] via ExoPlayer (no transcoding; original mp4/mov bytes).
 *  - Applies [colorCorrection] as a Media3 [RgbMatrix] effect every time it changes.
 *  - Releases the player on dispose so navigating away never leaks the codec.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    colorCorrection: ColorCorrection = ColorCorrection.NEUTRAL,
) {
    val context = LocalContext.current
    val player = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            playWhenReady = autoPlay
            prepare()
        }
    }

    LaunchedEffect(player, colorCorrection) {
        val matrix = ColorCorrectionMath.toRgbMatrix4x4(colorCorrection)
        runCatching {
            player.setVideoEffects(
                listOf(
                    object : RgbMatrix {
                        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = matrix
                    },
                ),
            )
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
            }
        },
        update = { view -> view.player = player },
    )
}
