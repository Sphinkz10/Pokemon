package com.rui.pvpgo

import android.content.Context
import com.rui.pvpgo.engine.PvpMove
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object MoveRepository {
    private const val URL_MOVES = "https://raw.githubusercontent.com/pvpoke/pvpoke/master/src/data/gamemaster/moves.json"
    private const val CACHE_NAME = "pvpoke_moves.json"
    private const val MAX_CACHE_AGE_MS = 7L * 24 * 60 * 60 * 1000

    data class LoadResult(
        val moves: List<PvpMove>,
        val source: String,
        val updatedAtEpochMs: Long? = null,
        val warning: String? = null
    )

    suspend fun load(context: Context, forceRefresh: Boolean = false): LoadResult = withContext(Dispatchers.IO) {
        val cache = File(context.filesDir, CACHE_NAME)
        val cacheFresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < MAX_CACHE_AGE_MS
        if (!forceRefresh && cacheFresh) {
            parseSafely(cache.readText())?.let { return@withContext LoadResult(it, "cache local", cache.lastModified()) }
        }

        val downloaded = runCatching { download(URL_MOVES) }.getOrNull()
        if (downloaded != null) {
            parseSafely(downloaded)?.let { parsed ->
                runCatching { cache.writeText(downloaded) }
                return@withContext LoadResult(parsed, "PvPoke atualizado", System.currentTimeMillis())
            }
        }

        if (cache.exists()) {
            parseSafely(cache.readText())?.let {
                return@withContext LoadResult(it, "cache anterior", cache.lastModified(), "Não foi possível atualizar os ataques agora.")
            }
        }
        LoadResult(emptyList(), "sem dados", warning = "Sem rede e sem cache de ataques; a análise de movesets fica indisponível.")
    }

    private fun download(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 20_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "PvPGoPersonal/1.0")
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSafely(json: String): List<PvpMove>? = runCatching { parse(json) }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun parse(json: String): List<PvpMove> {
        val array = JSONArray(json)
        return buildList {
            for (i in 0 until array.length()) {
                val m = array.optJSONObject(i) ?: continue
                val id = m.optString("moveId")
                val name = m.optString("name")
                val type = m.optString("type")
                if (id.isBlank() || name.isBlank() || type.isBlank()) continue
                val energyGain = m.optInt("energyGain", 0)
                val energyCost = m.optInt("energy", 0)
                val buffs = m.optJSONArray("buffs")
                val buffsSelf = m.optJSONArray("buffsSelf")
                val buffsOpponent = m.optJSONArray("buffsOpponent")
                val buffTarget = m.optString("buffTarget")
                val genericAttackDelta = buffs?.optInt(0, 0) ?: 0
                val genericDefenseDelta = buffs?.optInt(1, 0) ?: 0
                val selfAttackDelta = when {
                    buffsSelf != null -> buffsSelf.optInt(0, 0)
                    buffTarget == "self" || buffTarget == "both" -> genericAttackDelta
                    else -> 0
                }
                val selfDefenseDelta = when {
                    buffsSelf != null -> buffsSelf.optInt(1, 0)
                    buffTarget == "self" || buffTarget == "both" -> genericDefenseDelta
                    else -> 0
                }
                val opponentAttackDelta = when {
                    buffsOpponent != null -> buffsOpponent.optInt(0, 0)
                    buffTarget == "opponent" || buffTarget == "both" -> genericAttackDelta
                    else -> 0
                }
                val opponentDefenseDelta = when {
                    buffsOpponent != null -> buffsOpponent.optInt(1, 0)
                    buffTarget == "opponent" || buffTarget == "both" -> genericDefenseDelta
                    else -> 0
                }
                val chance = m.opt("buffApplyChance")?.toString()?.toDoubleOrNull() ?: 0.0
                add(
                    PvpMove(
                        moveId = id,
                        name = name,
                        abbreviation = m.optString("abbreviation").takeIf { it.isNotBlank() },
                        type = type,
                        power = m.optInt("power", 0),
                        energyCost = energyCost,
                        energyGain = energyGain,
                        turns = m.optInt("turns", 1).coerceAtLeast(1),
                        cooldownMs = m.optInt("cooldown", 500).coerceAtLeast(0),
                        attackerAttackStageDelta = selfAttackDelta,
                        attackerDefenseStageDelta = selfDefenseDelta,
                        opponentAttackStageDelta = opponentAttackDelta,
                        opponentDefenseStageDelta = opponentDefenseDelta,
                        buffApplyChance = chance.coerceIn(0.0, 1.0),
                        archetype = m.optString("archetype").takeIf { it.isNotBlank() },
                        isMegaMove = m.optBoolean("isMegaMove", false)
                    )
                )
            }
        }.distinctBy { it.moveId }
    }
}
