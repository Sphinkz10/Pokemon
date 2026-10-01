package com.rui.pvpgo.location

import android.content.Context

object SpawnFeedStore {
    private const val PREFS = "pvpgo_live"
    private const val KEY_ENDPOINT = "json_endpoint"

    fun endpoint(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ENDPOINT, "").orEmpty()

    fun saveEndpoint(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ENDPOINT, value.trim()).apply()
        RadarWatchScheduler.sync(context)
    }
}
