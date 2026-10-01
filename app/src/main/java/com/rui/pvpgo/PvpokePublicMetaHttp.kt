package com.rui.pvpgo

import com.rui.pvpgo.engine.PvpokeFetchedText
import com.rui.pvpgo.engine.PvpokeFormatRecord
import com.rui.pvpgo.engine.PvpokePayloadDecoder
import com.rui.pvpgo.engine.PvpokePublicPaths
import com.rui.pvpgo.engine.PvpokeRankingRecord
import com.rui.pvpgo.engine.PvpokeSourceTransport
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

/** Android/JVM network adapter for the public, open-source PvPoke repository. */
class JavaNetPvpokeSourceTransport(
    private val userAgent: String = "PvPGoPersonal/1.29"
) : PvpokeSourceTransport {
    override fun resolveMasterRevision(): String {
        val response = get("https://api.github.com/repos/pvpoke/pvpoke/commits/master")
        val sha = JSONObject(response.body).optString("sha").trim()
        require(sha.matches(Regex("[0-9a-fA-F]{40}"))) { "GitHub did not return a commit SHA" }
        return sha.lowercase()
    }

    override fun getPinnedText(revision: String, path: String): PvpokeFetchedText =
        get(PvpokePublicPaths.rawUrl(revision, path))

    private fun get(url: String): PvpokeFetchedText {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 30_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", userAgent)
        connection.setRequestProperty("Accept", "application/vnd.github+json, application/json, text/plain")
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode} for $url")
            return PvpokeFetchedText(
                body = connection.inputStream.bufferedReader().use { it.readText() },
                fetchedAtEpochMs = System.currentTimeMillis(),
                etag = connection.getHeaderField("ETag"),
                lastModified = connection.getHeaderField("Last-Modified")
            )
        } finally {
            connection.disconnect()
        }
    }
}

object OrgJsonPvpokePayloadDecoder : PvpokePayloadDecoder {
    override fun decodeRankings(json: String): List<PvpokeRankingRecord> {
        val array = JSONArray(json)
        return buildList {
            for (i in 0 until array.length()) {
                val row = array.optJSONObject(i) ?: continue
                val id = row.optString("speciesId").trim()
                val moves = row.optJSONArray("moveset") ?: continue
                if (id.isBlank() || moves.length() < 2) continue
                val decodedMoves = buildList {
                    for (j in 0 until moves.length()) moves.optString(j).trim().takeIf { it.isNotBlank() }?.let(::add)
                }
                if (decodedMoves.size < 2) continue
                add(PvpokeRankingRecord(id, decodedMoves, row.optDouble("score", Double.NaN).takeUnless(Double::isNaN)))
            }
        }
    }

    override fun decodeFormats(json: String): List<PvpokeFormatRecord> {
        val array = JSONArray(json)
        return buildList {
            for (i in 0 until array.length()) {
                val row = array.optJSONObject(i) ?: continue
                val title = row.optString("title").trim()
                val cup = row.optString("cup").trim()
                val cpRaw = row.opt("cp")
                val cp = when (cpRaw) {
                    is Number -> cpRaw.toInt()
                    is String -> cpRaw.toIntOrNull()
                    else -> null
                } ?: continue
                if (title.isBlank() || cup.isBlank()) continue
                add(PvpokeFormatRecord(title, cup, cp, row.optBoolean("showCup", false)))
            }
        }
    }
}
