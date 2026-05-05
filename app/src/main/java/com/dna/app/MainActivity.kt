package com.dna.app

import android.app.ActivityManager
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dna.app.ui.nav.DnaNavHost
import com.dna.app.ui.theme.DnaAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableWideColorGamutIfSupported()
        enableEdgeToEdge()
        setContent {
            DnaAppTheme {
                DnaNavHost()
            }
        }
    }

    /**
     * Opt the activity surface into wide-gamut rendering when the display
     * supports it. This lets Coil-decoded DISPLAY_P3 bitmaps and Media3 video
     * frames keep colour information that would otherwise be clipped to sRGB.
     *
     * Skipped on low-RAM devices where the ~2× framebuffer cost is steep.
     */
    private fun enableWideColorGamutIfSupported() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val d = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION") windowManager.defaultDisplay
        } ?: return
        if (!d.isWideColorGamut) return
        val am = getSystemService(ACTIVITY_SERVICE) as? ActivityManager
        if (am?.isLowRamDevice == true) return
        window.colorMode = ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT
    }
}
