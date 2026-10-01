package com.rui.pvpgo.events

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Community calendar: third-party Leek Duck data, NOT an official Niantic feed.
 * Source project: https://github.com/zhenga8533/leak-duck (data branch).
 * Local times are parsed in the user's current device timezone; timestamps in
 * seconds are absolute UTC instants. No synthetic events are ever generated.
 */
data class PokemonGoCalendarEvent(
    val title: String,
    val category: String,
    val startMs: Long,
    val endMs: Long,
    val articleUrl: String,
    val usesLocalTime: Boolean
)

data class CalendarSnapshot(
    val events: List<PokemonGoCalendarEvent>,
    val fetchedAtMs: Long?,
    val stale: Boolean,
    val error: String? = null
) {
    val isAvailable: Boolean get() = fetchedAtMs != null
    val sourceLabel: String get() = "Leek Duck · comunitário (não oficial)"
    fun active(now: Long = System.currentTimeMillis()): List<PokemonGoCalendarEvent> =
        events.filter { it.startMs <= now && it.endMs > now }
    fun upcoming(now: Long = System.currentTimeMillis()): List<PokemonGoCalendarEvent> =
        events.filter { it.startMs > now }.sortedBy { it.startMs }
}

object EventCalendarRepository {
    private const val URL_JSON = "https://raw.githubusercontent.com/zhenga8533/leak-duck/data/events.json"
    private const val PREFS = "pokemon_go_event_calendar"
    private const val FIELD_JSON = "events_json"
    private const val FIELD_TIME = "fetched_at_ms"
    const val FETCH_INTERVAL_MS = 6L * 60 * 60 * 1000
    const val STALE_AFTER_MS = 24L * 60 * 60 * 1000

    suspend fun load(context: Context, forceRefresh: Boolean = false): CalendarSnapshot =
        withContext(Dispatchers.IO) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val cached = prefs.getString(FIELD_JSON, null)
            val last = prefs.getLong(FIELD_TIME, 0)
            val cacheValid = cached != null && last in 1..now
            if (!forceRefresh && cacheValid && now - last < FETCH_INTERVAL_MS) {
                try {
                    return@withContext snapshot(cached!!, last, now)
                } catch (_: Exception) {
                    // A corrupted cache is never rendered as valid calendar data.
                }
            }
            try {
                val connection = URL(URL_JSON).openConnection() as HttpURLConnection
                val json = try {
                    connection.connectTimeout = 12000
                    connection.readTimeout = 12000
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty("Accept", "application/json")
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        throw IllegalStateException("HTTP ${connection.responseCode}")
                    }
                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        val text = reader.readText()
                        if (text.length > 2_000_000) throw IllegalStateException("Calendário demasiado grande")
                        text
                    }
                } finally {
                    connection.disconnect()
                }
                val valid = snapshot(json, now, now)
                prefs.edit().putString(FIELD_JSON, json).putLong(FIELD_TIME, now).apply()
                valid
            } catch (e: Exception) {
                if (cacheValid) {
                    try {
                        return@withContext snapshot(cached!!, last, now).copy(
                            stale = true, error = "Sem atualização online; a mostrar cache."
                        )
                    } catch (_: Exception) {}
                }
                CalendarSnapshot(emptyList(), null, true, "Sem ligação ao calendário comunitário.")
            }
        }

    private fun snapshot(json: String, fetchedAt: Long, now: Long): CalendarSnapshot {
        val root = JSONObject(json)
        val items = mutableListOf<PokemonGoCalendarEvent>()
        var recognizedEntries = 0
        val endHorizon = now + 30L * 24 * 60 * 60 * 1000
        val keys = root.keys()
        while (keys.hasNext()) {
            val group = keys.next()
            val entries = root.optJSONArray(group) ?: continue
            for (i in 0 until entries.length()) {
                val entry = entries.optJSONObject(i) ?: continue
                val local = entry.optBoolean("is_local_time", false)
                val start = parseTime(entry.opt("start_time"), local) ?: continue
                val end = parseTime(entry.opt("end_time"), local) ?: continue
                if (end <= start) continue
                // A valid event may be outside our 30-day display window.
                recognizedEntries++
                if (end < now || start > endHorizon) continue
                val title = entry.optString("title").trim().take(140)
                if (title.isBlank()) continue
                val link = entry.optString("article_url")
                if (!link.startsWith("https://leekduck.com/")) continue
                items += PokemonGoCalendarEvent(
                    title, entry.optString("category", group).take(64),
                    start, end, link, local
                )
            }
        }
        // Never promote a blank/changed provider schema to a successful refresh:
        // preserve the previous cache and warn the user instead.
        require(recognizedEntries > 0) { "Estrutura do feed de eventos inválida" }
        return CalendarSnapshot(
            events = items.distinctBy { it.articleUrl + ":" + it.startMs }.sortedBy { it.startMs },
            fetchedAtMs = fetchedAt,
            stale = now - fetchedAt > STALE_AFTER_MS
        )
    }

    private fun parseTime(value: Any?, local: Boolean): Long? = try {
        when (value) {
            is Number -> epochMillis(value.toLong())
            is String -> if (value.all(Char::isDigit) && value.isNotBlank()) {
                epochMillis(value.toLong())
            } else if (local) {
                LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } else {
                try { Instant.parse(value).toEpochMilli() }
                catch (_: Exception) { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            }
            else -> null
        }
    } catch (_: Exception) { null }

    private fun epochMillis(value: Long): Long =
        if (value > 100_000_000_000L) value else Math.multiplyExact(value, 1000L)

    /** WorkManager guarantees resilient periodic updates; Android may defer runs. */
    fun schedule(context: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val work = PeriodicWorkRequestBuilder<EventCalendarRefreshWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "pokemon_go_event_calendar",
            ExistingPeriodicWorkPolicy.KEEP,
            work
        )
    }
}

class EventCalendarRefreshWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val state = EventCalendarRepository.load(applicationContext, forceRefresh = true)
        return if (state.error == null) Result.success() else Result.retry()
    }
}
