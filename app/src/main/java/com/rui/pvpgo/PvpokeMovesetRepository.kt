package com.rui.pvpgo

import android.content.Context
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.RecommendedMoveset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads PvPoke's generated Overall ranking files and exposes only the moveset
 * that PvPoke currently associates with each species in that league.
 *
 * This is intentionally kept separate from the factual movepool in PokemonRepository:
 * - pokemon.json says what a species CAN learn.
 * - rankings/.../overall says what PvPoke currently recommends after simulations.
 *
 * A missing entry remains null; we never invent a recommendation.
 */
object PvpokeMovesetRepository {
    private const val BASE = "https://raw.githubusercontent.com/pvpoke/pvpoke/master/src/data/rankings/all/overall"
    private const val MAX_CACHE_AGE_MS = 24L * 60 * 60 * 1000
    private val memory = ConcurrentHashMap<League, Map<String, RecommendedMoveset>>()

    data class LoadResult(
        val recommendations: Map<String, RecommendedMoveset>,
        val source: String,
        val updatedAtEpochMs: Long? = null,
        val warning: String? = null
    )

    suspend fun load(context: Context, league: League, forceRefresh: Boolean = false): LoadResult = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            memory[league]?.let { return@withContext LoadResult(it, "PvPoke Overall · memória") }
        }

        val cap = when (league) {
            League.LITTLE -> 500
            League.GREAT -> 1500
            League.ULTRA -> 2500
            League.MASTER -> 10000
        }
        val cache = File(context.filesDir, "pvpoke_movesets_$cap.json")
        val cacheFresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < MAX_CACHE_AGE_MS

        if (!forceRefresh && cacheFresh) {
            parseSafely(cache.readText(), league, cache.lastModified())?.let { parsed ->
                memory[league] = parsed
                return@withContext LoadResult(parsed, "PvPoke Overall · cache", cache.lastModified())
            }
        }

        val downloaded = runCatching { download("$BASE/rankings-$cap.json") }.getOrNull()
        if (downloaded != null) {
            parseSafely(downloaded, league, System.currentTimeMillis())?.let { parsed ->
                runCatching { cache.writeText(downloaded) }
                memory[league] = parsed
                return@withContext LoadResult(parsed, "PvPoke Overall", System.currentTimeMillis())
            }
        }

        if (cache.exists()) {
            parseSafely(cache.readText(), league, cache.lastModified())?.let { parsed ->
                memory[league] = parsed
                return@withContext LoadResult(
                    parsed,
                    "PvPoke Overall · cache anterior",
                    cache.lastModified(),
                    "Não foi possível atualizar as recomendações desta liga agora."
                )
            }
        }

        LoadResult(
            emptyMap(),
            "sem recomendações",
            warning = "Sem ranking PvPoke em cache para ${league.name.lowercase()}; não será inventado um moveset recomendado."
        )
    }

    fun clearMemory() = memory.clear()

    private fun parseSafely(json: String, league: League, updatedAt: Long): Map<String, RecommendedMoveset>? =
        runCatching { parse(json, league, updatedAt) }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun parse(json: String, league: League, updatedAt: Long): Map<String, RecommendedMoveset> {
        val array = JSONArray(json)
        val out = LinkedHashMap<String, RecommendedMoveset>(array.length())
        for (i in 0 until array.length()) {
            val row = array.optJSONObject(i) ?: continue
            val speciesId = row.optString("speciesId")
            val moveset = row.optJSONArray("moveset") ?: continue
            if (speciesId.isBlank() || moveset.length() < 2) continue
            val fast = moveset.optString(0)
            val charged = buildList {
                for (j in 1 until moveset.length()) moveset.optString(j).takeIf { it.isNotBlank() }?.let(::add)
            }.take(2)
            if (fast.isBlank() || charged.isEmpty()) continue
            out[speciesId] = RecommendedMoveset(
                speciesId = speciesId,
                league = league,
                fastMoveId = fast,
                chargedMoveIds = charged,
                source = "PvPoke Overall",
                metaScore = row.optDouble("score", Double.NaN).takeUnless { it.isNaN() },
                sourceUpdatedAtEpochMs = updatedAt
            )
        }
        return out
    }

    private fun download(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 25_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "PvPGoPersonal/1.0")
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
