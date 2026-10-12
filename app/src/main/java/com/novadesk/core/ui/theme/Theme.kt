package com.novadesk.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Theme variants the user can pick in Settings → Appearance. */
enum class NovaTheme { System, Dark, Light, Amoled }

/** Accent choices. Kept small on purpose — every accent is hand-checked. */
enum class NovaAccent(val color: Color) {
    Violet(Color(0xFF8B5CF6)),
    Cyan(Color(0xFF22D3EE)),
    Mint(Color(0xFF34D399)),
    Amber(Color(0xFFFBBF24)),
    Rose(Color(0xFFF472B6)),
}

private fun darkScheme(accent: Color, background: Color, surface: Color) =
    darkColorScheme(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = Color(0xFF241B4D),
        onPrimaryContainer = SoftViolet,
        secondary = NeonCyan,
        onSecondary = VoidBlack,
        tertiary = MintGreen,
        background = background,
        onBackground = StarWhite,
        surface = surface,
        onSurface = StarWhite,
        surfaceVariant = EdgeViolet,
        onSurfaceVariant = MutedLavender,
        outline = EdgeViolet,
        outlineVariant = EdgeViolet.copy(alpha = 0.55f),
        error = DangerRed,
    )

private fun lightScheme(accent: Color) =
    lightColorScheme(
        primary = DeepViolet,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE6DCFF),
        onPrimaryContainer = Color(0xFF2A1B6B),
        secondary = Color(0xFF0E7490),
        background = Color(0xFFF6F4FF),
        onBackground = Color(0xFF17142E),
        surface = Color.White,
        onSurface = Color(0xFF17142E),
        surfaceVariant = Color(0xFFE9E4FA),
        onSurfaceVariant = Color(0xFF4C4670),
        outline = Color(0xFFCFC6EE),
        error = Color(0xFFB91C1C),
    )

/**
 * App theme. Resolves [NovaTheme.System] against the platform setting and
 * applies pure-black surfaces for [NovaTheme.Amoled].
 */
@Composable
fun NovaDeskTheme(
    theme: NovaTheme? = null,
    accent: NovaAccent? = null,
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val resolved = theme ?: NovaTheme.System
    val dark = when (resolved) {
        NovaTheme.Dark, NovaTheme.Amoled -> true
        NovaTheme.Light -> false
        NovaTheme.System -> isSystemInDarkTheme()
    }
    val primary = (accent ?: NovaAccent.Violet).color

    val scheme =
        if (dark) {
            val bg = if (resolved == NovaTheme.Amoled || amoled) Color.Black else VoidBlack
            val surface = if (resolved == NovaTheme.Amoled || amoled) Color.Black else MidnightNavy
            darkScheme(primary, bg, surface)
        } else {
            lightScheme(primary)
        }

    MaterialTheme(
        colorScheme = scheme,
        typography = NovaType,
        content = content,
    )
}
