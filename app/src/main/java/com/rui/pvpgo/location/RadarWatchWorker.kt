package com.rui.pvpgo.location

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rui.pvpgo.PokemonRepository
import com.rui.pvpgo.notifications.RadarNotifier
import com.rui.pvpgo.engine.RankSettings

class RadarWatchWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val endpoint = SpawnFeedStore.endpoint(applicationContext)
        if (endpoint.isBlank()) return Result.success()

        val targets = RadarTargetStore.all(applicationContext).filter { it.enabled }
        if (targets.isEmpty()) return Result.success()

        return runCatching {
            val catalog = PokemonRepository.load(applicationContext).pokemon.associateBy { it.speciesId }
            val provider = JsonFeedSpawnProvider(endpoint, "Radar background")
            var providerSuccesses = 0
            var providerFailures = 0

            for ((speciesId, speciesTargets) in targets.groupBy { it.speciesId }) {
                val species = catalog[speciesId] ?: continue
                val query = ProviderHealthMonitor.query(provider, speciesId)
                if (!query.health.ok) {
                    providerFailures++
                    continue
                }
                providerSuccesses++

                for (target in speciesTargets) {
                    val matches = SpawnMatcher.bestMatches(
                        species = species,
                        spawns = query.spawns,
                        league = target.league,
                        maxRank = target.maxRank,
                        settings = RankSettings(maxLevel = if (target.bestBuddy) 51.0 else 50.0)
                    )

                    val unseen = matches.firstOrNull { match ->
                        val alertId = "${target.key}|${match.spawn.sourceId}"
                        !RadarAlertHistoryStore.wasNotified(applicationContext, alertId)
                    } ?: continue

                    val alertId = "${target.key}|${unseen.spawn.sourceId}"
                    if (RadarNotifier.notifyMatch(applicationContext, target, unseen)) {
                        RadarAlertHistoryStore.markNotified(applicationContext, alertId)
                    }
                }
            }
            if (providerFailures > 0 && providerSuccesses == 0) Result.retry() else Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}
