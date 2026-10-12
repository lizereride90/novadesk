package com.novadesk.core.terminal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Keeps terminal processes alive while the user moves between screens.
 * Android can still reclaim us under pressure — sessions are best-effort,
 * and the UI always handles a dead session gracefully.
 */
class TerminalService : Service() {

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun service(): TerminalService = this@TerminalService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "terminal"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.createNotificationChannel(
                NotificationChannel(channelId, "Terminal sessions", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val note = NotificationCompat.Builder(this, channelId)
            .setContentTitle("NovaDesk terminal")
            .setContentText("Terminal sessions are running")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .build()
        startForeground(41, note)
    }
}
