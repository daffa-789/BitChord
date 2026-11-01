package com.music.bitchord.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Night Stage: a warm obsidian canvas with artwork-tinted glass, white as the
 * only action colour, and coral reserved for what is live.
 */
object BcColors {
    val Canvas = Color(0xFF0E0C10)
    val Surface = Color(0xFF171419)
    val Raised = Color(0xFF221E25)
    val Hover = Color(0xFF2B2630)

    val TextPrimary = Color(0xFFF4F1F7)
    val TextMuted = Color(0xFFA7A0AE)
    val TextFaint = Color(0xFF6E6775)

    val Hairline = Color(0x14FFFFFF)
    val HairlineStrong = Color(0x24FFFFFF)

    val Action = Color.White
    val Live = Color(0xFFFF5A4E)
    val Destructive = Color(0xFFFF453A)

    /** The player surfaces: dock, island, headers and Now Playing. */
    val Glass = Color(0xD10A090C)
    val GlassSoft = Color(0x990A090C)
    val Scrim = Color(0xCC0A090C)
}

object BcShapes {
    val chip = 4.dp
    val artwork = 8.dp
    val card = 12.dp
    val dock = 16.dp
    val capsule = 24.dp
}

object BcSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** The page edge. Wider than the phone because a desktop page has the room. */
    val pageGutter = 24.dp
}

object BcType {
    val family: FontFamily = FontFamily.SansSerif
}

object BcMotion {
    const val hoverMs = 120
    const val standardMs = 180
    const val sheetMs = 280
    const val islandDamping = 0.85f
    const val dockDamping = 0.88f

    fun <T> islandSpring() = spring<T>(
        dampingRatio = islandDamping,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> dockSpring() = spring<T>(
        dampingRatio = dockDamping,
        stiffness = Spring.StiffnessMediumLow,
    )
}

/** The artwork tint a player surface can pick up without repainting the shell. */
val LocalBcTint = staticCompositionLocalOf { Color.Transparent }

fun bcColorScheme() = darkColorScheme(
    primary = BcColors.Action,
    onPrimary = Color.Black,
    primaryContainer = BcColors.HairlineStrong,
    onPrimaryContainer = BcColors.TextPrimary,
    background = BcColors.Canvas,
    onBackground = BcColors.TextPrimary,
    surface = BcColors.Surface,
    onSurface = BcColors.TextPrimary,
    surfaceVariant = BcColors.Raised,
    onSurfaceVariant = BcColors.TextMuted,
    outline = BcColors.HairlineStrong,
    outlineVariant = BcColors.Hairline,
    surfaceTint = BcColors.Action,
    error = BcColors.Destructive,
)

fun bcTypography(): Typography {
    val defaults = Typography()
    val family = BcType.family
    return defaults.copy(
        displayLarge = defaults.displayLarge.copy(fontFamily = family, fontWeight = FontWeight.W800, fontSize = 34.sp, letterSpacing = (-0.8).sp),
        displayMedium = defaults.displayMedium.copy(fontFamily = family),
        displaySmall = defaults.displaySmall.copy(fontFamily = family),
        headlineLarge = defaults.headlineLarge.copy(fontFamily = family, fontWeight = FontWeight.W800, fontSize = 28.sp, letterSpacing = (-0.5).sp),
        headlineMedium = defaults.headlineMedium.copy(fontFamily = family, fontWeight = FontWeight.W700, fontSize = 22.sp, letterSpacing = (-0.4).sp),
        headlineSmall = defaults.headlineSmall.copy(fontFamily = family, fontWeight = FontWeight.W700, fontSize = 20.sp, letterSpacing = (-0.3).sp),
        titleLarge = defaults.titleLarge.copy(fontFamily = family, fontWeight = FontWeight.W700, fontSize = 20.sp, letterSpacing = (-0.3).sp),
        titleMedium = defaults.titleMedium.copy(fontFamily = family, fontWeight = FontWeight.W500, fontSize = 14.sp, letterSpacing = (-0.1).sp),
        titleSmall = defaults.titleSmall.copy(fontFamily = family, fontWeight = FontWeight.W500, fontSize = 13.sp),
        bodyLarge = defaults.bodyLarge.copy(fontFamily = family, fontWeight = FontWeight.W400, fontSize = 16.sp),
        bodyMedium = defaults.bodyMedium.copy(fontFamily = family, fontWeight = FontWeight.W400, fontSize = 14.sp),
        bodySmall = defaults.bodySmall.copy(fontFamily = family, fontWeight = FontWeight.W400, fontSize = 12.sp),
        labelLarge = defaults.labelLarge.copy(fontFamily = family, fontWeight = FontWeight.W500, fontSize = 14.sp),
        labelMedium = defaults.labelMedium.copy(fontFamily = family, fontWeight = FontWeight.W400, fontSize = 12.sp),
        labelSmall = defaults.labelSmall.copy(fontFamily = family, fontWeight = FontWeight.W600, fontSize = 11.sp),
    )
}
