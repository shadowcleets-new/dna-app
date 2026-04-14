package com.dna.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// TODO(M8): swap to expressiveLightColorScheme() / expressiveDarkColorScheme() once
// Material 3 1.4.x Expressive APIs stabilise. For now, fall back to seed-based schemes.
private val LightScheme = lightColorScheme(
    primary = SeedPrimary,
    secondary = SeedSecondary,
    tertiary = SeedTertiary,
)

private val DarkScheme = darkColorScheme(
    primary = SeedPrimary,
    secondary = SeedSecondary,
    tertiary = SeedTertiary,
)

@Composable
fun DnaAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DnaTypography,
        content = content,
    )
}
