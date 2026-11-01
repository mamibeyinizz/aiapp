package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("qr_servis_prefs", Context.MODE_PRIVATE)
            val branchId = prefs.getString("branch_id", "") ?: ""

            if (branchId.isNotEmpty()) {
                val serviceIntent = Intent(context, CallsForegroundService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BootReceiver", "Failed to start service on boot", e)
                }
            }
        }
    }
}
