package com.rui.pvpgo

import android.content.Context

object UserStore {
    private const val PREFS = "pvpgo_user"
    private const val FAVORITES = "favorites"
    private const val RECENTS = "recents"
    private const val MAX_RECENTS = 12

    fun favorites(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(FAVORITES, emptySet())
            ?.toSet()
            .orEmpty()

    fun toggleFavorite(context: Context, speciesId: String, current: Set<String>): Set<String> {
        val next = current.toMutableSet().apply {
            if (!add(speciesId)) remove(speciesId)
        }.toSet()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(FAVORITES, next).apply()
        return next
    }

    fun recents(context: Context): List<String> = decode(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(RECENTS, "").orEmpty()
    )

    fun pushRecent(context: Context, speciesId: String, current: List<String>): List<String> {
        val next = (listOf(speciesId) + current.filterNot { it == speciesId }).take(MAX_RECENTS)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(RECENTS, next.joinToString("|")).apply()
        return next
    }

    private fun decode(value: String): List<String> = value.split('|').filter { it.isNotBlank() }.distinct()
}
