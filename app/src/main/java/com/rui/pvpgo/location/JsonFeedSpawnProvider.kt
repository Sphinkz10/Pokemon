package com.rui.pvpgo.location

import com.rui.pvpgo.engine.IvSpread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * Provider for a user-controlled/public JSON feed. It deliberately accepts a few common
 * field aliases so a legal webhook relay or community feed can be plugged in without
 * changing the PvP engine.
 *
 * Accepted root shapes:
 *   [ { ...spawn... } ]
 *   { "spawns": [ ... ] }
 *   { "pokemon": [ ... ] }
 *   { "data": [ ... ] }
 */
class JsonFeedSpawnProvider(
    private val endpoint: String,
    override val displayName: String = "JSON feed"
) : SpawnProvider {
    override val id: String = "json:${endpoint.hashCode()}"

    private var cacheAtEpochMs: Long = 0L
    private var cachedSpawns: List<SpawnSight> = emptyList()
    private val instanceCacheMs = 30_000L

    override suspend fun find(speciesId: String): List<SpawnSight> = withContext(Dispatchers.IO) {
        require(endpoint.startsWith("https://")) {
            "O feed tem de usar HTTPS."
        }
        val now = System.currentTimeMillis()
        val all = if (cacheAtEpochMs > 0L && now - cacheAtEpochMs <= instanceCacheMs) {
            cachedSpawns
        } else {
            parse(download(endpoint)).also { parsed ->
                cachedSpawns = parsed
                cacheAtEpochMs = now
            }
        }
        val wanted = normalize(speciesId)
        all.filter { normalize(it.speciesId) == wanted }
    }

    private fun download(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "PvPGoPersonal/0.5")
        try {
            if (connection.responseCode !in 200..299) error("Feed HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(raw: String): List<SpawnSight> {
        val trimmed = raw.trim()
        val array = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val root = JSONObject(trimmed)
                root.optJSONArray("spawns")
                    ?: root.optJSONArray("pokemon")
                    ?: root.optJSONArray("data")
                    ?: JSONArray().put(root)
            }
            else -> error("Resposta JSON inválida")
        }

        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                parseSpawn(obj)?.let(::add)
            }
        }
    }

    private fun parseSpawn(o: JSONObject): SpawnSight? {
        val species = firstString(o, "speciesId", "species_id", "pokemonId", "pokemon_id", "pokemon", "name")
            ?.takeIf { it.isNotBlank() } ?: return null
        val atk = firstInt(o, "attackIv", "attack_iv", "atkIv", "atk_iv", "iv_attack") ?: return null
        val def = firstInt(o, "defenseIv", "defense_iv", "defIv", "def_iv", "iv_defense") ?: return null
        val sta = firstInt(o, "staminaIv", "stamina_iv", "staIv", "sta_iv", "iv_stamina", "hpIv", "hp_iv") ?: return null
        if (atk !in 0..15 || def !in 0..15 || sta !in 0..15) return null

        val lat = firstDouble(o, "latitude", "lat") ?: return null
        val lon = firstDouble(o, "longitude", "lon", "lng") ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null

        val cp = firstInt(o, "cp")
        val level = firstDouble(o, "level", "pokemonLevel", "pokemon_level")
        val observedAt = firstEpochMs(o, "observedAt", "observed_at", "seenAt", "seen_at", "timestamp")
        val expiresAt = firstEpochMs(o, "expiresAt", "expires_at", "despawn", "despawnTime", "despawn_time", "expiration")
        val sourceId = firstString(o, "id", "spawnId", "spawn_id", "encounterId", "encounter_id")
            ?: listOf(species, atk, def, sta, lat, lon, cp ?: "", level ?: "", expiresAt ?: observedAt ?: "")
                .joinToString(":")

        return SpawnSight(
            sourceId = sourceId,
            speciesId = species,
            iv = IvSpread(atk, def, sta),
            cp = cp,
            level = level,
            latitude = lat,
            longitude = lon,
            observedAtEpochMs = observedAt,
            expiresAtEpochMs = expiresAt,
            sourceLabel = displayName
        )
    }

    private fun firstString(o: JSONObject, vararg names: String): String? =
        names.firstNotNullOfOrNull { name -> if (o.has(name) && !o.isNull(name)) o.optString(name).takeIf { it.isNotBlank() } else null }

    private fun firstInt(o: JSONObject, vararg names: String): Int? = names.firstNotNullOfOrNull { name ->
        if (!o.has(name) || o.isNull(name)) return@firstNotNullOfOrNull null
        when (val v = o.opt(name)) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull()
            else -> null
        }
    }

    private fun firstDouble(o: JSONObject, vararg names: String): Double? = names.firstNotNullOfOrNull { name ->
        if (!o.has(name) || o.isNull(name)) return@firstNotNullOfOrNull null
        when (val v = o.opt(name)) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull()
            else -> null
        }
    }

    private fun firstEpochMs(o: JSONObject, vararg names: String): Long? = names.firstNotNullOfOrNull { name ->
        if (!o.has(name) || o.isNull(name)) return@firstNotNullOfOrNull null
        when (val v = o.opt(name)) {
            is Number -> normalizeEpoch(v.toLong())
            is String -> v.toLongOrNull()?.let(::normalizeEpoch) ?: runCatching { Instant.parse(v).toEpochMilli() }.getOrNull()
            else -> null
        }
    }

    private fun normalizeEpoch(value: Long): Long = if (value in 1..9_999_999_999L) value * 1000L else value

    private fun normalize(value: String): String = value
        .trim().lowercase().replace('-', '_').replace(' ', '_')
}
