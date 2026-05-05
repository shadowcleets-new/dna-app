package com.dna.app.ui.detail

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Overlay matchParentSize-style modifier consumer. While neutral-tap mode is on,
 * a tap is mapped to a pixel of the loaded [bitmap] — the corresponding RGB
 * triple is reported via [onSampled].
 *
 * The overlay deliberately doesn't render anything visible — the cursor / chip
 * lives on the panel.
 */
@Composable
fun NeutralTapOverlay(
    enabled: Boolean,
    bitmap: MutableState<Bitmap?>,
    onSampled: (FloatArray) -> Unit,
) {
    if (!enabled) return
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(enabled, bitmap.value) {
                val bm = bitmap.value ?: return@pointerInput
                detectTapGestures { offset ->
                    val w = size.width.takeIf { it > 0 } ?: return@detectTapGestures
                    val h = size.height.takeIf { it > 0 } ?: return@detectTapGestures
                    val px = ((offset.x / w) * (bm.width - 1)).toInt().coerceIn(0, bm.width - 1)
                    val py = ((offset.y / h) * (bm.height - 1)).toInt().coerceIn(0, bm.height - 1)
                    val pixel = runCatching { bm.getPixel(px, py) }.getOrNull() ?: return@detectTapGestures
                    val gain = com.dna.app.domain.color.ColorCorrectionMath.whiteBalanceGainFromTap(
                        Color.red(pixel),
                        Color.green(pixel),
                        Color.blue(pixel),
                    )
                    onSampled(gain)
                }
            },
    ) {}
}
