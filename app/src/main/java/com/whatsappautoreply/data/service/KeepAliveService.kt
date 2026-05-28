package com.whatsappautoreply.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.whatsappautoreply.presentation.MainActivity
import com.whatsappautoreply.R
import com.whatsappautoreply.data.database.dao.SettingsDao
import com.whatsappautoreply.data.database.entity.SettingsEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class KeepAliveService : Service() {

    @Inject
    lateinit var settingsDao: SettingsDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isPaused = false

    companion object {
        private const val CHANNEL_ID = "KeepAliveChannel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_PAUSE = "com.whatsappautoreply.ACTION_PAUSE"
        const val ACTION_RESUME = "com.whatsappautoreply.ACTION_RESUME"
        const val ACTION_STOP = "com.whatsappautoreply.ACTION_STOP"

        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            context.stopService(intent)
        }
    }

    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PAUSE -> {
                    isPaused = true
                    serviceScope.launch {
                        settingsDao.insertSetting(SettingsEntity("auto_reply_enabled", "false"))
                    }
                    updateNotificationStatus()
                }
                ACTION_RESUME -> {
                    isPaused = false
                    serviceScope.launch {
                        settingsDao.insertSetting(SettingsEntity("auto_reply_enabled", "true"))
                    }
                    updateNotificationStatus()
                }
                ACTION_STOP -> {
                    stopSelf()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        val filter = IntentFilter().apply {
            addAction(ACTION_PAUSE)
            addAction(ACTION_RESUME)
            addAction(ACTION_STOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(actionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(actionReceiver, filter)
        }
        
        serviceScope.launch {
            val enabled = settingsDao.getSetting("auto_reply_enabled")?.value?.toBoolean() ?: false
            isPaused = !enabled
            updateNotificationStatus()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(actionReceiver)
    }

    private fun updateNotificationStatus() {
        startForeground(NOTIFICATION_ID, createNotification(isPaused))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Auto-Reply Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the auto-reply service running in the background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(paused: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseResumeAction = if (paused) {
            val resumeIntent = Intent(ACTION_RESUME).setPackage(packageName)
            val resumePending = PendingIntent.getBroadcast(this, 1, resumeIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            NotificationCompat.Action.Builder(0, "Resume", resumePending).build()
        } else {
            val pauseIntent = Intent(ACTION_PAUSE).setPackage(packageName)
            val pausePending = PendingIntent.getBroadcast(this, 2, pauseIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            NotificationCompat.Action.Builder(0, "Pause", pausePending).build()
        }

        val stopIntent = Intent(ACTION_STOP).setPackage(packageName)
        val stopPending = PendingIntent.getBroadcast(this, 3, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stopAction = NotificationCompat.Action.Builder(0, "Stop Service", stopPending).build()

        val title = if (paused) "Auto-Reply Paused" else "Auto-Reply Active"
        val text = if (paused) "Service is running but auto-reply is disabled" else "Scanning for notifications..."

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(pauseResumeAction)
            .addAction(stopAction)
            .build()
    }
}
