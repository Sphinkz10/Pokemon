package com.rui.pvpgo

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Data contract independent of PvPoke battle simulations and Room user collection. */
internal data class NationalDexLoad(val entries: List<NationalDexEntry>, val status: String, val complete: Boolean)

internal object NationalDexRepository {
    private const val PAGE_LIMIT = 250
    private const val MAX_PAGES = 40
    private const val CACHE_LIFETIME = 7L * 24 * 60 * 60 * 1000
    private const val CACHE_FILE = "national-pokedex-v1.json"
    private val entryId = Regex("^https://pokeapi\\.co/api/v2/pokemon-species/([0-9]+)/?$")

    suspend fun load(context: Context, fallback: List<com.rui.pvpgo.engine.PokemonSpecies>): NationalDexLoad =
        withContext(Dispatchers.IO) {
            val path = File(context.filesDir, CACHE_FILE)
            fun cached(): List<NationalDexEntry>? = runCatching {
                val json = JSONObject(path.readText())
                val array = json.getJSONArray("species")
                val pairs = (0 until array.length()).map { index ->
                    val item = array.getJSONObject(index)
                    item.getInt("dex") to item.getString("slug")
                }
                NationalDexPolicy.fromApiRecords(pairs).takeIf { it.isNotEmpty() }
            }.getOrNull()
            val fresh = path.exists() && System.currentTimeMillis() - path.lastModified() < CACHE_LIFETIME
            if (fresh) cached()?.let { return@withContext NationalDexLoad(it, "Pokédex nacional · cache", true) }

            // API pagination: do not truncate to 150, 1025 or a hardcoded current species count.
            val fetched = runCatching {
                val records = mutableListOf<Pair<Int, String>>()
                var next: String? = "https://pokeapi.co/api/v2/pokemon-species?limit=$PAGE_LIMIT&offset=0"
                val seen = mutableSetOf<String>()
                var pages = 0
                while (next != null && pages < MAX_PAGES) {
                    check(seen.add(next)) { "Pagination loop" }
                    val page = JSONObject(readJson(next))
                    val rows = page.getJSONArray("results")
                    for (i in 0 until rows.length()) {
                        val entry = rows.getJSONObject(i)
                        val id = entryId.matchEntire(entry.getString("url"))?.groupValues?.get(1)?.toIntOrNull()
                        if (id != null) records += id to entry.getString("name")
                    }
                    next = page.optString("next").takeUnless { it.isBlank() || it == "null" }
                    pages++
                }
                check(next == null && records.isNotEmpty()) { "Incomplete national index" }
                NationalDexPolicy.fromApiRecords(records)
            }.getOrNull()
            if (fetched != null) {
                runCatching {
                    val data = JSONObject()
                    val arr = org.json.JSONArray()
                    for (entry in fetched) arr.put(JSONObject().put("dex", entry.dex).put("slug", entry.slug))
                    data.put("species", arr)
                    val temp = File(path.parentFile, "$CACHE_FILE.tmp")
                    temp.writeText(data.toString())
                    if (!temp.renameTo(path)) { path.writeText(data.toString()); temp.delete() }
                }
                return@withContext NationalDexLoad(fetched, "Pokédex nacional · PokéAPI", true)
            }
            cached()?.let { return@withContext NationalDexLoad(it, "Pokédex nacional · cache anterior", true) }
            NationalDexLoad(NationalDexPolicy.fromPvPCatalog(fallback), "Modo offline · catálogo PvP parcial", false)
        }

    private fun readJson(address: String): String {
        val url = URL(address)
        require(url.protocol == "https" && url.host == "pokeapi.co" && url.path.trimEnd('/') == "/api/v2/pokemon-species")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000; readTimeout = 9000; instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
        }
        return try {
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            check(conn.contentLengthLong <= 1024 * 1024) { "Page too large" }
            conn.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    check(output.size() + count <= 1024 * 1024) { "Page too large" }
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
        } finally { conn.disconnect() }
    }
}
