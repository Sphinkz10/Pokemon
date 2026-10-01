package com.rui.pvpgo.location

import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PvPRankEntry

data class SpawnSight(
    val sourceId: String,
    val speciesId: String,
    val iv: IvSpread,
    val cp: Int? = null,
    val level: Double? = null,
    val latitude: Double,
    val longitude: Double,
    val observedAtEpochMs: Long? = null,
    val expiresAtEpochMs: Long? = null,
    val sourceLabel: String? = null
) {
    fun isExpired(nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        expiresAtEpochMs?.let { it <= nowEpochMs } ?: false
}

data class RankedSpawn(
    val spawn: SpawnSight,
    val league: League,
    val rank: PvPRankEntry
)

interface SpawnProvider {
    val id: String
    val displayName: String get() = id
    suspend fun find(speciesId: String): List<SpawnSight>
}
