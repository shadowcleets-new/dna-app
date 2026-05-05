package com.dna.app.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dna.app.domain.color.ColorCorrection

/**
 * Per-dress colour-correction settings. Keyed 1:1 by dress id; deletes cascade
 * via the repository (Room foreign keys are intentionally avoided so that
 * Firestore-driven inserts can land before the dress row exists).
 */
@Entity(tableName = "color_corrections")
data class ColorCorrectionEntity(
    @PrimaryKey val dressId: String,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val exposureEv: Float,
    val temperatureK: Float,
    val tint: Float,
    val whiteBalanceR: Float?,
    val whiteBalanceG: Float?,
    val whiteBalanceB: Float?,
    val updatedAt: Long,
)

fun ColorCorrectionEntity.toDomain(): ColorCorrection {
    val wb = if (whiteBalanceR != null && whiteBalanceG != null && whiteBalanceB != null) {
        floatArrayOf(whiteBalanceR, whiteBalanceG, whiteBalanceB)
    } else {
        null
    }
    return ColorCorrection(
        brightness = brightness,
        contrast = contrast,
        saturation = saturation,
        exposureEv = exposureEv,
        temperatureK = temperatureK,
        tint = tint,
        whiteBalanceRgb = wb,
    )
}

fun ColorCorrection.toEntity(dressId: String, updatedAt: Long): ColorCorrectionEntity =
    ColorCorrectionEntity(
        dressId = dressId,
        brightness = brightness,
        contrast = contrast,
        saturation = saturation,
        exposureEv = exposureEv,
        temperatureK = temperatureK,
        tint = tint,
        whiteBalanceR = whiteBalanceRgb?.getOrNull(0),
        whiteBalanceG = whiteBalanceRgb?.getOrNull(1),
        whiteBalanceB = whiteBalanceRgb?.getOrNull(2),
        updatedAt = updatedAt,
    )
