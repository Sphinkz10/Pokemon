package com.rui.pvpgo.location

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object RadarWatchScheduler {
    private const val UNIQUE_PERIODIC_WORK = "pvp-radar-watch"
    private const val UNIQUE_IMMEDIATE_WORK = "pvp-radar-watch-now"

    fun sync(context: Context) {
        val enabledTargets = RadarTargetStore.all(context).any { it.enabled }
        val hasFeed = SpawnFeedStore.endpoint(context).isNotBlank()
        val workManager = WorkManager.getInstance(context)

        if (!enabledTargets || !hasFeed) {
            workManager.cancelUniqueWork(UNIQUE_PERIODIC_WORK)
            workManager.cancelUniqueWork(UNIQUE_IMMEDIATE_WORK)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Immediate best-effort check so enabling a target does not have to wait
        // for the first periodic WorkManager window.
        val immediate = OneTimeWorkRequestBuilder<RadarWatchWorker>()
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniqueWork(
            UNIQUE_IMMEDIATE_WORK,
            ExistingWorkPolicy.REPLACE,
            immediate
        )

        // Android periodic background work is inexact and has a 15-minute minimum.
        val periodic = PeriodicWorkRequestBuilder<RadarWatchWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic
        )
    }

    fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(UNIQUE_PERIODIC_WORK)
        workManager.cancelUniqueWork(UNIQUE_IMMEDIATE_WORK)
    }
}
