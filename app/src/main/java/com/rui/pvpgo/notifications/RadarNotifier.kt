package com.rui.pvpgo.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.rui.pvpgo.MainActivity
import com.rui.pvpgo.location.RankedSpawn
import com.rui.pvpgo.location.RadarTarget
import java.util.Locale

object RadarNotifier {
    const val CHANNEL_ID = "pvp_radar_matches"
    private const val CHANNEL_NAME = "Radar PvP"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas quando aparece um Pokémon dentro do Rank PvP escolhido"
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled()
    }

    fun notifyMatch(context: Context, target: RadarTarget, match: RankedSpawn): Boolean {
        ensureChannel(context)
        if (!canNotify(context)) return false

        val spawn = match.spawn
        val coords = String.format(Locale.US, "%.6f, %.6f", spawn.latitude, spawn.longitude)
        val title = "${target.speciesName} · Rank #${match.rank.rank} ${shortLeague(target)}"
        val text = "${match.rank.iv} · ${spawn.cp?.let { "$it CP" } ?: "CP —"} · $coords"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("speciesId", target.speciesId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (target.key + spawn.sourceId).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(context)
        }.setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText("$text\nToca para abrir a app e consultar o Radar."))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_RECOMMENDATION)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify((target.key + spawn.sourceId).hashCode(), notification)
        return true
    }

    private fun shortLeague(target: RadarTarget): String = when (target.league.name) {
        "GREAT" -> "Great"
        "ULTRA" -> "Ultra"
        "MASTER" -> "Master"
        else -> target.league.name
    }
}
