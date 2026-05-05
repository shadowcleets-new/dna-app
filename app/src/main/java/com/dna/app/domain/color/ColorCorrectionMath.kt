package com.dna.app.domain.color

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Pure-math helpers shared by the still-image (Compose ColorMatrix) and video
 * (Media3 RgbMatrix) rendering paths. The chain (right-to-left as applied to
 * the colour vector) is:
 *
 *   final = brightness ∘ contrast ∘ saturation ∘ tempTint ∘ whiteBalance ∘ exposure
 *
 * The result is a 4×5 row-major matrix (Compose `ColorMatrix` uses this layout)
 * and a separate 4×4 helper for Media3.
 */
object ColorCorrectionMath {

    /** 4×5 row-major matrix consumable by [androidx.compose.ui.graphics.ColorMatrix]. */
    fun toMatrix4x5(c: ColorCorrection): FloatArray {
        var m = identity4x5()

        // Apply right-to-left: each new step is composed on top of the running matrix.
        m = compose(exposureMatrix(c.exposureEv), m)
        c.whiteBalanceRgb?.let { wb -> m = compose(channelGainMatrix(wb[0], wb[1], wb[2]), m) }
        val (rGain, gGain, bGain) = kelvinToRgbGain(c.temperatureK, c.tint)
        m = compose(channelGainMatrix(rGain, gGain, bGain), m)
        m = compose(saturationMatrix(1f + c.saturation), m)
        m = compose(contrastMatrix(1f + c.contrast), m)
        m = compose(brightnessMatrix(c.brightness), m)
        return m
    }

    /** Drop-in 4×4 matrix for Media3 `RgbMatrix` (alpha row + bias row dropped). */
    fun toRgbMatrix4x4(c: ColorCorrection): FloatArray {
        val m = toMatrix4x5(c)
        return floatArrayOf(
            m[0], m[1], m[2], m[3],
            m[5], m[6], m[7], m[8],
            m[10], m[11], m[12], m[13],
            m[15], m[16], m[17], m[18],
        )
    }

    /**
     * Tanner Helland approximation: maps colour temperature in Kelvin to per-channel
     * gain. The [tint] slider biases the green channel ±15 %.
     */
    internal fun kelvinToRgbGain(kelvin: Float, tint: Float): Triple<Float, Float, Float> {
        val k = kelvin.coerceIn(1000f, 40000f) / 100f
        val r: Float = if (k <= 66f) 255f else (329.698727446 * (k - 60).toDouble().pow(-0.1332047592)).toFloat()
        val g: Float = if (k <= 66f) {
            (99.4708025861 * ln(k.toDouble()) - 161.1195681661).toFloat()
        } else {
            (288.1221695283 * (k - 60).toDouble().pow(-0.0755148492)).toFloat()
        }
        val b: Float = when {
            k >= 66f -> 255f
            k <= 19f -> 0f
            else -> (138.5177312231 * ln((k - 10).toDouble()) - 305.0447927307).toFloat()
        }
        // Reference white at 6500 K — evaluate the same approximation here so
        // a 6500 K input yields gain = (1, 1, 1) exactly (identity).
        val refK = 6500f / 100f
        val refR = 255f
        val refG = (99.4708025861 * ln(refK.toDouble()) - 161.1195681661).toFloat()
        val refB = (138.5177312231 * ln((refK - 10).toDouble()) - 305.0447927307).toFloat()
        val rGain = (r.coerceIn(0f, 255f) / refR).coerceIn(0.5f, 2f)
        val gGain = (g.coerceIn(0f, 255f) / refG).coerceIn(0.5f, 2f) * (1f - 0.15f * tint.coerceIn(-1f, 1f))
        val bGain = (b.coerceIn(0f, 255f) / refB).coerceIn(0.5f, 2f)
        return Triple(rGain, gGain, bGain)
    }

    /**
     * Given a tapped pixel [r],[g],[b] (0..255) that the user knows should be neutral
     * grey, return the per-channel gain that drags it to its mean. Result is clamped
     * to a sane range.
     */
    fun whiteBalanceGainFromTap(r: Int, g: Int, b: Int): FloatArray {
        val rf = r.coerceAtLeast(1).toFloat()
        val gf = g.coerceAtLeast(1).toFloat()
        val bf = b.coerceAtLeast(1).toFloat()
        val gray = (rf + gf + bf) / 3f
        return floatArrayOf(
            (gray / rf).coerceIn(0.5f, 2f),
            (gray / gf).coerceIn(0.5f, 2f),
            (gray / bf).coerceIn(0.5f, 2f),
        )
    }

    private fun identity4x5(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    private fun exposureMatrix(ev: Float): FloatArray {
        val gain = 2.0.pow(ev.toDouble().coerceIn(-2.0, 2.0)).toFloat()
        return channelGainMatrix(gain, gain, gain)
    }

    private fun channelGainMatrix(r: Float, g: Float, b: Float): FloatArray = floatArrayOf(
        r, 0f, 0f, 0f, 0f,
        0f, g, 0f, 0f, 0f,
        0f, 0f, b, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    private fun brightnessMatrix(amount: Float): FloatArray {
        val offset = (amount.coerceIn(-1f, 1f)) * 255f
        return floatArrayOf(
            1f, 0f, 0f, 0f, offset,
            0f, 1f, 0f, 0f, offset,
            0f, 0f, 1f, 0f, offset,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    private fun contrastMatrix(scale: Float): FloatArray {
        val s = scale.coerceIn(0f, 2f)
        val bias = 0.5f * (1f - s) * 255f
        return floatArrayOf(
            s, 0f, 0f, 0f, bias,
            0f, s, 0f, 0f, bias,
            0f, 0f, s, 0f, bias,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    private fun saturationMatrix(amount: Float): FloatArray {
        val s = amount.coerceIn(0f, 2f)
        val inv = 1f - s
        // Rec. 709 luma weights.
        val rW = 0.2126f * inv
        val gW = 0.7152f * inv
        val bW = 0.0722f * inv
        return floatArrayOf(
            rW + s, gW, bW, 0f, 0f,
            rW, gW + s, bW, 0f, 0f,
            rW, gW, bW + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** Compose two 4×5 matrices: result = a ∘ b (b applied first to the colour vector). */
    private fun compose(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(20)
        for (row in 0 until 4) {
            for (col in 0 until 5) {
                var sum = 0f
                for (k in 0 until 4) {
                    sum += a[row * 5 + k] * b[k * 5 + col]
                }
                if (col == 4) sum += a[row * 5 + 4]
                out[row * 5 + col] = sum
            }
        }
        return out
    }

    @Suppress("unused")
    private fun clamp01(v: Float): Float = max(0f, min(1f, v))
}
