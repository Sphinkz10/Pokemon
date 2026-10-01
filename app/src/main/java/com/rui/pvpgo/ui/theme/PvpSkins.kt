package com.rui.pvpgo.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color

/**
 * Central semantic palette. Every screen must consume semantic tokens rather
 * than embed hex literals so a skin can change without editing layouts.
 */
data class PvpPalette(
    val canvasStart: Color,
    val canvasMiddle: Color,
    val canvasEnd: Color,
    val surfaceCard: Color,
    val surfaceRaised: Color,
    val surfaceInput: Color,
    val borderDefault: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val neutralMuted: Color,
    val brandYellow: Color,
    val brandBlue: Color,
    val brandRed: Color,
    val stateSuccess: Color,
    val stateWarning: Color,
    val stateUnknown: Color,
    val typeWater: Color,
    val accentSky: Color,
    val accentDeep: Color,
    val accentAmber: Color,
    val accentGreen: Color,
    val accentRed: Color,
    val todayCard: Color,
    val todayCardRaised: Color,
    val todayBorder: Color
)

enum class PvpSkin(val storageKey: String, val label: String, val description: String, val isLight: Boolean = false) {
    DEEP("deep", "Deep", "Azul noturno e dourado"),
    AMOLED("amoled", "AMOLED", "Pretos profundos"),
    MYSTIC("mystic", "Mystic", "Roxo e violeta"),
    CLASSIC("classic", "Classic", "Claro e luminoso", true);

    companion object {
        fun fromStorage(value: String?): PvpSkin =
            entries.firstOrNull { it.storageKey == value } ?: DEEP
    }
}

object PvpSkinPalettes {
    val deep = PvpPalette(
        canvasStart = Color(0xFF070B14),
        canvasMiddle = Color(0xFF0B1220),
        canvasEnd = Color(0xFF101A33),
        surfaceCard = Color(0xFF111A2E),
        surfaceRaised = Color(0xFF182442),
        surfaceInput = Color(0xFF0F1A2F),
        borderDefault = Color(0xFF25375D),
        textPrimary = Color(0xFFFFF8F0),
        textSecondary = Color(0xFFAFC0D8),
        neutralMuted = Color(0xFFAAAABB),
        brandYellow = Color(0xFFFFCC03),
        brandBlue = Color(0xFF38BDF8),
        brandRed = Color(0xFFFF4222),
        stateSuccess = Color(0xFF34D399),
        stateWarning = Color(0xFFFFB84D),
        stateUnknown = Color(0xFF9BA8B7),
        typeWater = Color(0xFF3399FF),
        accentSky = Color(0xFF75D5FF),
        accentDeep = Color(0xFF163651),
        accentAmber = Color(0xFFFFC84D),
        accentGreen = Color(0xFF7BE0A1),
        accentRed = Color(0xFFFF735F),
        todayCard = Color(0xFF101D31),
        todayCardRaised = Color(0xFF172B43),
        todayBorder = Color(0xFF2D5E7B)
    )

    val amoled = deep.copy(
        canvasStart = Color(0xFF000000), canvasMiddle = Color(0xFF060606),
        canvasEnd = Color(0xFF101010), surfaceCard = Color(0xFF141414),
        surfaceRaised = Color(0xFF232323), surfaceInput = Color(0xFF171717),
        borderDefault = Color(0xFF3C3C3C),
        brandYellow = Color(0xFFFFD54A), brandBlue = Color(0xFF8DD9ED),
        accentSky = Color(0xFF8DD9ED), accentDeep = Color(0xFF1D3036),
        todayCard = Color(0xFF151515), todayCardRaised = Color(0xFF252525),
        todayBorder = Color(0xFF393939)
    )

    val mystic = deep.copy(
        canvasStart = Color(0xFF140E23), canvasMiddle = Color(0xFF211632),
        canvasEnd = Color(0xFF302044), surfaceCard = Color(0xFF29203C),
        surfaceRaised = Color(0xFF3C2A58), surfaceInput = Color(0xFF211832),
        borderDefault = Color(0xFF5C4679), brandYellow = Color(0xFFCDABFF),
        brandBlue = Color(0xFF9E91FB), accentSky = Color(0xFFC7A5FF),
        accentDeep = Color(0xFF423263), accentAmber = Color(0xFFCDABFF),
        todayCard = Color(0xFF291F3D), todayCardRaised = Color(0xFF3A2A50),
        todayBorder = Color(0xFF5C4679)
    )

    val classic = deep.copy(
        canvasStart = Color(0xFFF4F6FA), canvasMiddle = Color(0xFFE9EEF7),
        canvasEnd = Color(0xFFE1E8F2), surfaceCard = Color(0xFFFFFFFF),
        surfaceRaised = Color(0xFFE8ECF5), surfaceInput = Color(0xFFF6F8FB),
        borderDefault = Color(0xFFC3CBDB), textPrimary = Color(0xFF16253C),
        textSecondary = Color(0xFF42536B), neutralMuted = Color(0xFF65748B),
        brandYellow = Color(0xFF2360B8), brandBlue = Color(0xFF176F9D),
        brandRed = Color(0xFFCA3636), stateSuccess = Color(0xFF08765C),
        stateWarning = Color(0xFF8D5B00), stateUnknown = Color(0xFF65748B),
        accentSky = Color(0xFF2360B8), accentDeep = Color(0xFFDCE8F9),
        accentAmber = Color(0xFF985700), accentGreen = Color(0xFF08765C),
        accentRed = Color(0xFFBA4444), todayCard = Color(0xFFFFFFFF),
        todayCardRaised = Color(0xFFE8EEF8), todayBorder = Color(0xFFB8C8E0)
    )

    fun palette(skin: PvpSkin): PvpPalette = when(skin) {
        PvpSkin.DEEP -> deep
        PvpSkin.AMOLED -> amoled
        PvpSkin.MYSTIC -> mystic
        PvpSkin.CLASSIC -> classic
    }
}

/** Preferences are device-local and contain no personal/secret data. */
object PvpSkinPreferences {
    private const val PREFERENCES = "pokemon_pvp_appearance"
    private const val SELECTED_SKIN = "selected_skin"

    fun load(context: Context): PvpSkin =
        PvpSkin.fromStorage(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(SELECTED_SKIN, PvpSkin.DEEP.storageKey))

    fun save(context: Context, skin: PvpSkin) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(SELECTED_SKIN, skin.storageKey).apply()
    }
}
