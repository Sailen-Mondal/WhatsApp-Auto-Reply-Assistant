package com.whatsappautoreply.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.whatsappautoreply.data.database.dao.SettingsDao
import com.whatsappautoreply.data.service.KeepAliveService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsDao: SettingsDao

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device rebooted, checking if auto-reply should be started")
            
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                val enabled = settingsDao.getSetting("auto_reply_enabled")?.value?.toBoolean() ?: false
                if (enabled) {
                    Log.i("BootReceiver", "Auto-reply is enabled, starting KeepAliveService")
                    KeepAliveService.start(context)
                }
            }
        }
    }
}
