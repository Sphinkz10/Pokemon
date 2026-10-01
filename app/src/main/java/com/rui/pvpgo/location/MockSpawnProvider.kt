package com.rui.pvpgo.location

import com.rui.pvpgo.engine.IvSpread

/** Local demo provider used to validate the complete radar flow without any external service. */
class MockSpawnProvider : SpawnProvider {
    override val id = "demo"
    override val displayName = "Demonstração local"

    override suspend fun find(speciesId: String): List<SpawnSight> {
        if (speciesId != "charizard") return emptyList()
        val now = System.currentTimeMillis()
        return listOf(
            SpawnSight(
                sourceId = "demo-charizard-1",
                speciesId = "charizard",
                iv = IvSpread(0, 15, 13),
                cp = 1500,
                level = 19.5,
                latitude = 41.1579,
                longitude = -8.6291,
                observedAtEpochMs = now,
                expiresAtEpochMs = now + 20 * 60_000,
                sourceLabel = displayName
            ),
            SpawnSight(
                sourceId = "demo-charizard-2",
                speciesId = "charizard",
                iv = IvSpread(0, 13, 15),
                level = 35.0,
                latitude = 38.7223,
                longitude = -9.1393,
                observedAtEpochMs = now,
                expiresAtEpochMs = now + 12 * 60_000,
                sourceLabel = displayName
            )
        )
    }
}
