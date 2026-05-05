package com.dna.app.data.imageloader

import android.content.Context
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.request.crossfade
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Coil ImageLoader configured for fidelity:
 *  - ARGB_8888 (vs RGB_565) so derivatives keep 8 bits per channel.
 *  - Hardware bitmaps enabled by default. Surfaces that need to sample pixels
 *    (e.g. the neutral-tap colour-correction tool) opt out per request via
 *    [disableHardwareForPixelSampling].
 *  - Crossfade kept short so swapping the display-tier placeholder for the
 *    full-resolution original feels seamless.
 *
 * The activity sets `ACTIVITY_COLOR_MODE_WIDE_COLOR_GAMUT` on capable displays,
 * so the rendered surface preserves DISPLAY_P3 colour information.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoilModule {

    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader {
        val platform: PlatformContext = context
        return ImageLoader.Builder(platform)
            .allowHardware(true)
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            .crossfade(true)
            .build()
    }
}

/** Disable hardware bitmaps for the request — needed when a surface samples pixels via getPixel. */
fun ImageRequest.Builder.disableHardwareForPixelSampling(): ImageRequest.Builder =
    allowHardware(false)
