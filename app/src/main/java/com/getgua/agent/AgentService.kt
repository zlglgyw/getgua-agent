package com.getgua.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager

/**
 * Foreground service utama aplikasi agen.
 *
 * Rumah bagi loop agen + penjadwalan (fase 5). START_STICKY +
 * BootReceiver membuatnya hidup kembali sesudah reboot / sesudah
 * sempat di-kill sistem. Tanpa idle timeout — service ini dirancang
 * selalu hidup.
 *
 * v1.0.1: BUKAN getgua client — aplikasi ini TIDAK PUNYA server HTTP
 * kendali jarak jauh dan TIDAK PUNYA token HTTP. Aplikasi jalan
 * sendiri di HP, diperintah lewat chat melalui otak (API key).
 */
class AgentService : Service() {

    companion object {
        const val ACTION_STOP = "com.getgua.agent.STOP"

        private const val NOTIF_ID = 1
        private const val CHANNEL_ID = "agen_getgua"

        @Volatile var running = false
            private set
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        Automation.init(this)
        Prefs.init(this)
        ActionLog.init(this)
        ChatStore.init(this)
        LearningStore.init(this)
        AgentLoop.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        startForeground(
            NOTIF_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        acquireWakeLock()
        running = true
        TelegramBot.start()
        AppLog.log("service", "started (loop agen + jadwal, tanpa HTTP)")
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        TelegramBot.stop()
        releaseWakeLock()
        AppLog.log("service", "destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notif_channel_desc)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "getgua-agent:loop").apply {
                acquire()
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
        wakeLock = null
    }
}
