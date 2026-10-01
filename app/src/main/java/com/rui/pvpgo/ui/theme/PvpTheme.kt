package com.rui.pvpgo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Visual foundation extracted from the approved v1.37 Figma direction.
 *
 * Figma remains the visual source of truth. The product specification remains
 * the source of truth for behavior, states and flows.
 */
object PvpColors {
    var skin by mutableStateOf(PvpSkin.DEEP)
        private set

    fun useSkin(next: PvpSkin) {
        if (skin != next) skin = next
    }

    private val palette: PvpPalette get() = PvpSkinPalettes.palette(skin)
    val CanvasStart: Color get() = palette.canvasStart
    val CanvasMiddle: Color get() = palette.canvasMiddle
    val CanvasEnd: Color get() = palette.canvasEnd
    val SurfaceCard: Color get() = palette.surfaceCard
    val SurfaceRaised: Color get() = palette.surfaceRaised
    val SurfaceInput: Color get() = palette.surfaceInput
    val BorderDefault: Color get() = palette.borderDefault
    val TextPrimary: Color get() = palette.textPrimary
    val TextSecondary: Color get() = palette.textSecondary
    val NeutralMuted: Color get() = palette.neutralMuted
    val BrandYellow: Color get() = palette.brandYellow
    val BrandBlue: Color get() = palette.brandBlue
    val BrandRed: Color get() = palette.brandRed
    val StateSuccess: Color get() = palette.stateSuccess
    val StateWarning: Color get() = palette.stateWarning
    val StateUnknown: Color get() = palette.stateUnknown
    val TypeWater: Color get() = palette.typeWater
    val AccentSky: Color get() = palette.accentSky
    val AccentDeep: Color get() = palette.accentDeep
    val AccentAmber: Color get() = palette.accentAmber
    val AccentGreen: Color get() = palette.accentGreen
    val AccentRed: Color get() = palette.accentRed
    val TodayCard: Color get() = palette.todayCard
    val TodayCardRaised: Color get() = palette.todayCardRaised
    val TodayBorder: Color get() = palette.todayBorder
}

object PvpSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
    val Xxl = 24.dp
    val Xxxl = 32.dp
    val TouchTarget = 48.dp
}

object PvpRadius {
    val Small = 8.dp
    val Button = 10.dp
    val Medium = 12.dp
    val Card = 14.dp
    val Large = 18.dp
    val Sheet = 24.dp
}

private fun pvpColorScheme(palette: PvpPalette, isLight: Boolean) =
    (if (isLight) lightColorScheme(
        primary = palette.brandYellow,
        onPrimary = palette.surfaceCard,
        primaryContainer = palette.surfaceRaised,
        onPrimaryContainer = palette.textPrimary,
        secondary = palette.brandBlue,
        onSecondary = palette.surfaceCard,
        secondaryContainer = palette.surfaceRaised,
        onSecondaryContainer = palette.textPrimary,
        tertiary = palette.stateSuccess,
        onTertiary = palette.surfaceCard,
        error = palette.brandRed,
        onError = palette.surfaceCard,
        background = palette.canvasStart,
        onBackground = palette.textPrimary,
        surface = palette.surfaceCard,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceRaised,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.borderDefault,
        outlineVariant = palette.borderDefault,
        scrim = Color.Black
    ) else darkColorScheme(
        primary = palette.brandYellow,
        onPrimary = palette.canvasStart,
        primaryContainer = palette.surfaceRaised,
        onPrimaryContainer = palette.textPrimary,
        secondary = palette.brandBlue,
        onSecondary = palette.canvasStart,
        secondaryContainer = palette.surfaceRaised,
        onSecondaryContainer = palette.textPrimary,
        tertiary = palette.stateSuccess,
        onTertiary = palette.canvasStart,
        error = palette.brandRed,
        onError = palette.textPrimary,
        background = palette.canvasStart,
        onBackground = palette.textPrimary,
        surface = palette.surfaceCard,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceRaised,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.borderDefault,
        outlineVariant = palette.borderDefault,
        scrim = Color.Black
    ))

private val PvpTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 38.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 21.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 19.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

private val PvpShapes = Shapes(
    extraSmall = RoundedCornerShape(PvpRadius.Small),
    small = RoundedCornerShape(PvpRadius.Button),
    medium = RoundedCornerShape(PvpRadius.Medium),
    large = RoundedCornerShape(PvpRadius.Card),
    extraLarge = RoundedCornerShape(PvpRadius.Sheet)
)

@Composable
fun PvpTheme(skin: PvpSkin = PvpSkin.DEEP, content: @Composable () -> Unit) {
    val palette = PvpSkinPalettes.palette(skin)
    MaterialTheme(
        colorScheme = pvpColorScheme(palette, skin.isLight),
        typography = PvpTypography,
        shapes = PvpShapes,
        content = content
    )
}
