package com.rui.pvpgo.location

import android.content.Context
import org.json.JSONArray

object RadarAlertHistoryStore {
    private const val PREFS = "pvpgo_radar_alerts"
    private const val KEY_SEEN = "seen_spawn_ids"
    private const val MAX_ENTRIES = 250

    fun wasNotified(context: Context, sourceId: String): Boolean = load(context).contains(sourceId)

    fun markNotified(context: Context, sourceId: String) {
        val current = load(context).toMutableList()
        current.remove(sourceId)
        current.add(sourceId)
        val trimmed = current.takeLast(MAX_ENTRIES)
        val array = JSONArray()
        trimmed.forEach(array::put)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SEEN, array.toString()).apply()
    }

    private fun load(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SEEN, "[]")
            .orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) array.optString(i).takeIf { it.isNotBlank() }?.let(::add)
            }
        }.getOrElse { emptyList() }
    }
}
