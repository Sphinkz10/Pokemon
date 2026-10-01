package com.rui.pvpgo

import android.content.Context
import com.rui.pvpgo.engine.PokemonSpecies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object PokemonRepository {
    private const val URL_PVPOKE = "https://raw.githubusercontent.com/pvpoke/pvpoke/master/src/data/gamemaster/pokemon.json"
    private const val CACHE_NAME = "pvpoke_pokemon.json"
    private const val MAX_CACHE_AGE_MS = 7L * 24 * 60 * 60 * 1000

    data class LoadResult(
        val pokemon: List<PokemonSpecies>,
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

        val downloaded = runCatching { download(URL_PVPOKE) }.getOrNull()
        if (downloaded != null) {
            parseSafely(downloaded)?.let { parsed ->
                runCatching { cache.writeText(downloaded) }
                return@withContext LoadResult(parsed, "PvPoke atualizado", System.currentTimeMillis())
            }
        }

        if (cache.exists()) {
            parseSafely(cache.readText())?.let {
                return@withContext LoadResult(it, "cache anterior", cache.lastModified(), "Não foi possível atualizar os dados agora.")
            }
        }

        LoadResult(
            pokemon = SamplePokemonRepository.all,
            source = "base de emergência",
            updatedAtEpochMs = null,
            warning = "Sem rede e sem cache: disponíveis apenas os Pokémon de validação. Liga a internet e toca em Atualizar."
        )
    }

    private fun parseSafely(json: String): List<PokemonSpecies>? =
        runCatching { parse(json) }.getOrNull()?.takeIf { it.isNotEmpty() }

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

    private fun parse(json: String): List<PokemonSpecies> {
        val array = JSONArray(json)
        val result = ArrayList<PokemonSpecies>(array.length())
        for (i in 0 until array.length()) {
            val p = array.optJSONObject(i) ?: continue
            val stats = p.optJSONObject("baseStats") ?: continue
            val atk = stats.optInt("atk", -1)
            val def = stats.optInt("def", -1)
            val hp = stats.optInt("hp", -1)
            if (atk <= 0 || def <= 0 || hp <= 0) continue

            val released = p.optBoolean("released", true)
            if (!released) continue

            val speciesId = p.optString("speciesId")
            if (speciesId.isBlank()) continue

            val types = p.optJSONArray("types").toStringList().filterNot { it.equals("none", ignoreCase = true) }
            val tags = p.optJSONArray("tags").toStringList().toSet()
            val nicknames = p.optJSONArray("nicknames").toStringList()
            val family = p.optJSONObject("family")
            val displayName = p.optString("speciesName", speciesId)

            result += PokemonSpecies(
                dex = p.optInt("dex", 0),
                name = displayName,
                form = formFrom(displayName, tags),
                speciesId = speciesId,
                types = types,
                tags = tags,
                nicknames = nicknames,
                searchPriority = p.optInt("searchPriority", 0),
                familyId = family?.optString("id")?.takeIf { it.isNotBlank() },
                parentSpeciesId = family?.optString("parent")?.takeIf { it.isNotBlank() },
                evolutionSpeciesIds = family?.optJSONArray("evolutions").toStringList(),
                fastMoveIds = p.optJSONArray("fastMoves").toStringList(),
                chargedMoveIds = p.optJSONArray("chargedMoves").toStringList(),
                eliteMoveIds = p.optJSONArray("eliteMoves").toStringList().toSet(),
                thirdMoveStardustCost = p.optInt("thirdMoveCost", -1).takeIf { it >= 0 },
                released = released,
                baseAttack = atk,
                baseDefense = def,
                baseStamina = hp
            )
        }

        return result.distinctBy { it.speciesId }
            .sortedWith(compareBy<PokemonSpecies> { it.dex }.thenByDescending { it.searchPriority }.thenBy { it.name })
    }

    private fun JSONArray?.toStringList(): List<String> = buildList {
        if (this@toStringList != null) {
            for (i in 0 until length()) optString(i).takeIf { it.isNotBlank() }?.let(::add)
        }
    }

    private fun formFrom(name: String, tags: Set<String>): String {
        val explicit = name.substringAfter('(', "").substringBeforeLast(')', "").trim()
        if (explicit.isNotBlank()) return explicit
        return when {
            "shadow" in tags -> "Shadow"
            "mega" in tags || "supermega" in tags -> "Mega"
            else -> "Normal"
        }
    }
}
