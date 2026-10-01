package com.rui.pvpgo.location

import android.content.Context
import com.rui.pvpgo.engine.League
import org.json.JSONArray
import org.json.JSONObject

data class RadarTarget(
    val speciesId: String,
    val speciesName: String,
    val league: League,
    val maxRank: Int,
    val bestBuddy: Boolean = false,
    val enabled: Boolean = true
) {
    val key: String get() = "$speciesId|${league.name}|$maxRank|$bestBuddy"
}

object RadarTargetStore {
    private const val PREFS = "pvpgo_radar_targets"
    private const val KEY_TARGETS = "targets_json"

    fun all(context: Context): List<RadarTarget> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TARGETS, "[]")
            .orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val speciesId = o.optString("speciesId")
                    val speciesName = o.optString("speciesName", speciesId)
                    val league = runCatching { League.valueOf(o.optString("league")) }.getOrNull() ?: continue
                    val maxRank = o.optInt("maxRank", 10).coerceAtLeast(1)
                    if (speciesId.isBlank()) continue
                    add(
                        RadarTarget(
                            speciesId = speciesId,
                            speciesName = speciesName,
                            league = league,
                            maxRank = maxRank,
                            bestBuddy = o.optBoolean("bestBuddy", false),
                            enabled = o.optBoolean("enabled", true)
                        )
                    )
                }
            }.distinctBy { it.key }
        }.getOrElse { emptyList() }
    }

    fun upsert(context: Context, target: RadarTarget): List<RadarTarget> {
        val next = (all(context).filterNot { it.key == target.key } + target)
            .sortedWith(compareBy<RadarTarget> { it.speciesName }.thenBy { it.league.ordinal }.thenBy { it.maxRank })
        save(context, next)
        return next
    }

    fun remove(context: Context, key: String): List<RadarTarget> {
        val next = all(context).filterNot { it.key == key }
        save(context, next)
        return next
    }

    fun setEnabled(context: Context, key: String, enabled: Boolean): List<RadarTarget> {
        val next = all(context).map { if (it.key == key) it.copy(enabled = enabled) else it }
        save(context, next)
        return next
    }

    private fun save(context: Context, targets: List<RadarTarget>) {
        val array = JSONArray()
        targets.forEach { target ->
            array.put(
                JSONObject()
                    .put("speciesId", target.speciesId)
                    .put("speciesName", target.speciesName)
                    .put("league", target.league.name)
                    .put("maxRank", target.maxRank)
                    .put("bestBuddy", target.bestBuddy)
                    .put("enabled", target.enabled)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_TARGETS, array.toString()).apply()
        RadarWatchScheduler.sync(context)
    }
}
