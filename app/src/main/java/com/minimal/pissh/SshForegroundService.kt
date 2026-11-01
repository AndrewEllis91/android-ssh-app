package com.minimal.pissh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SshForegroundService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): SshForegroundService = this@SshForegroundService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val sshClient = SshClient()

    private var wakeLock: PowerManager.WakeLock? = null
    private var currentHostname: String = "Raspberry Pi"
    private var isForegroundStarted: Boolean = false

    var onUserDisconnectAction: (() -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Observe SSH session state so if the network drops, the service stops itself cleanly
        serviceScope.launch {
            sshClient.isConnected.collect { connected ->
                if (!connected && isForegroundStarted) {
                    stopSessionForeground()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SESSION -> {
                val host = intent.getStringExtra(EXTRA_HOSTNAME)?.ifBlank { "Raspberry Pi" } ?: "Raspberry Pi"
                startSessionForeground(host)
            }
            ACTION_DISCONNECT -> {
                onUserDisconnectAction?.invoke()
                sshClient.disconnect()
                stopSessionForeground()
            }
        }
        return START_NOT_STICKY
    }

    fun startSessionForeground(hostname: String) {
        currentHostname = hostname
        acquireWakeLock()
        val notification = buildNotification(hostname)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isForegroundStarted = true
    }

    fun stopSessionForeground() {
        releaseWakeLock()
        if (isForegroundStarted) {
            isForegroundStarted = false
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PiSSH::SessionWakeLock").apply {
            setReferenceCounted(false)
            // 4-hour safety ceiling to guarantee zero runaway battery drain
            acquire(4 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Active SSH Session",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the active SSH connection alive while switching apps"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(hostname: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, SshForegroundService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle("SSH Session Active — Connected to $hostname")
            .setContentText("Tap to return to terminal")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Disconnect",
                disconnectPendingIntent
            )
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Zero background idle drain when user swipes the app away
        sshClient.disconnect()
        stopSessionForeground()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        releaseWakeLock()
        sshClient.disconnect()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "pissh_active_session"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_SESSION = "com.minimal.pissh.action.START_SESSION"
        const val ACTION_DISCONNECT = "com.minimal.pissh.action.DISCONNECT"
        const val EXTRA_HOSTNAME = "extra_hostname"
    }
}
