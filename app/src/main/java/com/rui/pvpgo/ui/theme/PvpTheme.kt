package com.rui.pvpgo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
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
    val CanvasStart = Color(0xFF070B14)
    val CanvasMiddle = Color(0xFF0B1220)
    val CanvasEnd = Color(0xFF101A33)

    val SurfaceCard = Color(0xFF111A2E)
    val SurfaceRaised = Color(0xFF182442)
    val SurfaceInput = Color(0xFF0F1A2F)
    val BorderDefault = Color(0xFF25375D)

    val TextPrimary = Color(0xFFFFF8F0)
    val TextSecondary = Color(0xFFAFC0D8)
    val NeutralMuted = Color(0xFFAAAABB)

    val BrandYellow = Color(0xFFFFCC03)
    val BrandBlue = Color(0xFF38BDF8)
    val BrandRed = Color(0xFFFF4222)

    val StateSuccess = Color(0xFF34D399)
    val StateWarning = Color(0xFFFFB84D)
    val StateUnknown = Color(0xFF9BA8B7)

    val TypeWater = Color(0xFF3399FF)
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

private val PvpColorScheme = darkColorScheme(
    primary = PvpColors.BrandYellow,
    onPrimary = PvpColors.CanvasStart,
    primaryContainer = PvpColors.SurfaceRaised,
    onPrimaryContainer = PvpColors.TextPrimary,
    secondary = PvpColors.BrandBlue,
    onSecondary = PvpColors.CanvasStart,
    secondaryContainer = PvpColors.SurfaceRaised,
    onSecondaryContainer = PvpColors.TextPrimary,
    tertiary = PvpColors.StateSuccess,
    onTertiary = PvpColors.CanvasStart,
    error = PvpColors.BrandRed,
    onError = PvpColors.TextPrimary,
    background = PvpColors.CanvasStart,
    onBackground = PvpColors.TextPrimary,
    surface = PvpColors.SurfaceCard,
    onSurface = PvpColors.TextPrimary,
    surfaceVariant = PvpColors.SurfaceRaised,
    onSurfaceVariant = PvpColors.TextSecondary,
    outline = PvpColors.BorderDefault,
    outlineVariant = PvpColors.BorderDefault,
    scrim = Color.Black
)

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
fun PvpTheme(content: @Composable () -> Unit) {
    // The redesign is intentionally dark-first. A light palette can be added
    // later without changing call sites.
    MaterialTheme(
        colorScheme = PvpColorScheme,
        typography = PvpTypography,
        shapes = PvpShapes,
        content = content
    )
}
