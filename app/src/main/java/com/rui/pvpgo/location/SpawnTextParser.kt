package com.rui.pvpgo.location

import com.rui.pvpgo.engine.IvSpread

/**
 * Best-effort parser for text copied from a coordinate site or Discord alert.
 * It intentionally requires both an IV triplet and GPS coordinates, so random
 * chat text is not silently treated as a spawn.
 */
object SpawnTextParser {
    private val ivPattern = Regex("(?<!\\d)(\\d{1,2})\\s*[/\\-]\\s*(\\d{1,2})\\s*[/\\-]\\s*(\\d{1,2})(?!\\d)")
    private val coordsPattern = Regex("(-?\\d{1,2}(?:\\.\\d+)?)\\s*[,;]\\s*(-?\\d{1,3}(?:\\.\\d+)?)")
    private val cpPattern = Regex("(?i)\\bCP\\s*[:#-]?\\s*(\\d{1,5})\\b")
    private val levelPattern = Regex("(?i)\\b(?:LVL?|LEVEL|L)\\s*[:#-]?\\s*(\\d{1,2}(?:\\.5)?)\\b")

    fun parse(text: String, speciesId: String, sourceLabel: String = "clipboard"): SpawnSight? {
        val ivMatch = ivPattern.find(text) ?: return null
        val atk = ivMatch.groupValues[1].toIntOrNull() ?: return null
        val def = ivMatch.groupValues[2].toIntOrNull() ?: return null
        val sta = ivMatch.groupValues[3].toIntOrNull() ?: return null
        if (atk !in 0..15 || def !in 0..15 || sta !in 0..15) return null

        val coordinate = coordsPattern.findAll(text)
            .mapNotNull { match ->
                val lat = match.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
                val lon = match.groupValues[2].toDoubleOrNull() ?: return@mapNotNull null
                if (lat !in -90.0..90.0 || lon !in -180.0..180.0) null else lat to lon
            }
            .firstOrNull() ?: return null

        val cp = cpPattern.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val level = levelPattern.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()

        return SpawnSight(
            sourceId = "clipboard:${text.hashCode()}",
            speciesId = speciesId,
            iv = IvSpread(atk, def, sta),
            cp = cp,
            level = level,
            latitude = coordinate.first,
            longitude = coordinate.second,
            sourceLabel = sourceLabel
        )
    }
}
