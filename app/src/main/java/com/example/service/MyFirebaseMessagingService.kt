package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        try {
            Log.d("FCM", "New FCM token received: $token")
            val prefs = getSharedPreferences("qr_servis_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("fcm_token", token).apply()

            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null && token.isNotEmpty()) {
                FirebaseFirestore.getInstance().collection("users")
                    .document(currentUser.uid)
                    .update("fcmToken", token)
                    .addOnSuccessListener {
                        Log.d("FCM", "FCM token updated in Firestore for user ${currentUser.uid}")
                    }
                    .addOnFailureListener { e ->
                        Log.d("FCM", "Failed to update FCM token in Firestore: ${e.message}")
                    }
            }
        } catch (e: Exception) {
            Log.d("FCM", "Error handling onNewToken: ${e.message}")
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM", "Message received from: ${remoteMessage.from}")

        val rawRole = remoteMessage.data["senderRole"] ?: remoteMessage.data["source"] ?: ""
        val targetType = remoteMessage.data["targetType"] ?: ""
        val branchId = remoteMessage.data["branchId"] ?: ""
        val isManagement = rawRole.trim().lowercase() in listOf("admin", "yonetici", "yönetici", "administrator", "sistem", "kurucu", "patron") ||
                targetType == "all_staff" ||
                (branchId == "all" && rawRole.trim().lowercase() !in listOf("mudur", "müdür", "manager"))
        val sourceCategory = if (isManagement) "Yönetim Duyurusu" else "Müdür Duyurusu"

        val rawTitle = remoteMessage.data["title"] ?: remoteMessage.notification?.title
        val title = if (!rawTitle.isNullOrBlank()) {
            if (rawTitle.contains("Duyuru", ignoreCase = true)) rawTitle else "$sourceCategory: $rawTitle"
        } else {
            sourceCategory
        }

        val message = remoteMessage.data["message"]
            ?: remoteMessage.notification?.body
            ?: "Yeni bir operasyon duyurusu var."

        showAnnouncementNotification(title, message)
    }

    private fun showAnnouncementNotification(title: String, message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createAnnouncementChannel(notificationManager)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "announcements")
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CallsForegroundService.CHANNEL_ANNOUNCEMENT_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify((System.currentTimeMillis() % 100000).toInt(), notification)
    }

    private fun createAnnouncementChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CallsForegroundService.CHANNEL_ANNOUNCEMENT_ID,
                "Müdür Duyuruları",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Müdürlerden personele iletilen operasyon duyuruları."
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
