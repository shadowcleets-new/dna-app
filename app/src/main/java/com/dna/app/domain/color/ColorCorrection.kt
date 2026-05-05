package com.dna.app.domain.color

/**
 * Per-item colour-correction settings. All sliders are normalised; the math
 * lives in [ColorCorrectionMath] so both the Compose `ColorMatrix` (still
 * images) and Media3's `RgbMatrix` (video) share a single source of truth.
 *
 *  - [brightness] / [contrast] / [saturation] / [tint] : -1..1, 0 = neutral.
 *  - [exposureEv] : -2..2 EV, 0 = neutral. Multiplies linear RGB by 2^EV.
 *  - [temperatureK] : 2000..10000 K. 6500 K is neutral D65.
 *  - [whiteBalanceRgb] : optional per-channel gain captured from a neutral tap.
 */
data class ColorCorrection(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val exposureEv: Float = 0f,
    val temperatureK: Float = 6500f,
    val tint: Float = 0f,
    val whiteBalanceRgb: FloatArray? = null,
) {
    val isIdentity: Boolean
        get() = brightness == 0f &&
            contrast == 0f &&
            saturation == 0f &&
            exposureEv == 0f &&
            temperatureK == 6500f &&
            tint == 0f &&
            whiteBalanceRgb == null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ColorCorrection) return false
        if (brightness != other.brightness) return false
        if (contrast != other.contrast) return false
        if (saturation != other.saturation) return false
        if (exposureEv != other.exposureEv) return false
        if (temperatureK != other.temperatureK) return false
        if (tint != other.tint) return false
        val a = whiteBalanceRgb
        val b = other.whiteBalanceRgb
        return when {
            a == null && b == null -> true
            a == null || b == null -> false
            else -> a.contentEquals(b)
        }
    }

    override fun hashCode(): Int {
        var result = brightness.hashCode()
        result = 31 * result + contrast.hashCode()
        result = 31 * result + saturation.hashCode()
        result = 31 * result + exposureEv.hashCode()
        result = 31 * result + temperatureK.hashCode()
        result = 31 * result + tint.hashCode()
        result = 31 * result + (whiteBalanceRgb?.contentHashCode() ?: 0)
        return result
    }

    companion object {
        val NEUTRAL = ColorCorrection()
    }
}

/** Curated presets surfaced as chips in the correction panel. */
enum class ColorPreset(val label: String, val correction: ColorCorrection) {
    NEUTRAL("Neutral", ColorCorrection.NEUTRAL),
    WARM("Warm", ColorCorrection(temperatureK = 5200f, saturation = 0.05f)),
    COOL("Cool", ColorCorrection(temperatureK = 7800f, saturation = 0.05f)),
    PUNCHY("Punchy", ColorCorrection(contrast = 0.25f, saturation = 0.3f)),
    SOFT("Soft", ColorCorrection(contrast = -0.2f, saturation = -0.1f, brightness = 0.05f)),
    PRINT_ACCURATE("Print-accurate", ColorCorrection(saturation = -0.15f, contrast = 0.05f)),
}
