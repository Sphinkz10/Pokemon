package com.rui.pvpgo.location

data class ProviderHealth(
    val ok: Boolean,
    val providerName: String,
    val checkedAtEpochMs: Long,
    val latencyMs: Long,
    val totalSpawns: Int,
    val freshSpawns: Int,
    val expiredSpawns: Int,
    val error: String? = null
)

data class ProviderQueryResult(
    val spawns: List<SpawnSight>,
    val health: ProviderHealth
)

object ProviderHealthMonitor {
    suspend fun query(
        provider: SpawnProvider,
        speciesId: String,
        nowEpochMs: Long = System.currentTimeMillis()
    ): ProviderQueryResult {
        val startNs = System.nanoTime()
        return try {
            val spawns = provider.find(speciesId)
            val elapsed = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(0L)
            val expired = spawns.count { it.isExpired(nowEpochMs) }
            ProviderQueryResult(
                spawns = spawns,
                health = ProviderHealth(
                    ok = true,
                    providerName = provider.displayName,
                    checkedAtEpochMs = nowEpochMs,
                    latencyMs = elapsed,
                    totalSpawns = spawns.size,
                    freshSpawns = spawns.size - expired,
                    expiredSpawns = expired
                )
            )
        } catch (t: Throwable) {
            val elapsed = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(0L)
            ProviderQueryResult(
                spawns = emptyList(),
                health = ProviderHealth(
                    ok = false,
                    providerName = provider.displayName,
                    checkedAtEpochMs = nowEpochMs,
                    latencyMs = elapsed,
                    totalSpawns = 0,
                    freshSpawns = 0,
                    expiredSpawns = 0,
                    error = t.message ?: t::class.java.simpleName
                )
            )
        }
    }
}
