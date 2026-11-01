package com.example.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.utils.FormatUtils
import com.example.utils.SoundSettingsManager
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class CallsForegroundService : Service() {

    private var listenerRegistration: ListenerRegistration? = null
    private var announcementListenerRegistration: ListenerRegistration? = null
    private var directAnnouncementListenerRegistration: ListenerRegistration? = null
    private val serviceStartTime = System.currentTimeMillis()
    private val activeNotifications = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("qr_servis_prefs", Context.MODE_PRIVATE)
        val branchId = prefs.getString("branch_id", "") ?: ""

        // Show persistent notification to keep service alive
        val mainPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val persistentNotification = NotificationCompat.Builder(this, CHANNEL_STATUS_ID)
            .setContentTitle("QR Servis Çalışıyor")
            .setContentText("Masalardan gelen çağrılar dinleniyor.")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentIntent(mainPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        // Start as foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_FOREGROUND_ID,
                persistentNotification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_FOREGROUND_ID, persistentNotification)
        }

        if (branchId.isNotEmpty()) {
            startListening(branchId)
        } else {
            stopSelf()
        }

        return START_STICKY
    }

    private fun startListening(branchId: String) {
        listenerRegistration?.remove()

        val db = FirebaseFirestore.getInstance()
        listenerRegistration = db.collection("calls")
            .whereEqualTo("branchId", branchId)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener

                for (dc in snapshots.documentChanges) {
                    val doc = dc.document
                    val callId = doc.id
                    val masaNo = doc.getString("masaNo") ?: ""
                    val tip = doc.getString("tip") ?: ""
                    val durum = doc.getString("durum") ?: ""
                    val createdAt = doc.getTimestamp("createdAt")

                    @Suppress("UNCHECKED_CAST")
                    val itemsList = doc.get("items") as? List<Map<String, Any>>
                    val itemCount = itemsList?.size ?: 0

                    val isNew = if (createdAt != null) {
                        createdAt.toDate().time >= serviceStartTime
                    } else {
                        false
                    }

                    when (dc.type) {
                        DocumentChange.Type.ADDED -> {
                            if (durum == "bekliyor" && isNew) {
                                showCallNotification(callId, masaNo, tip, itemCount)
                            }
                        }
                        DocumentChange.Type.MODIFIED -> {
                            if (durum == "onaylandi") {
                                cancelCallNotification(callId)
                            } else if (durum == "bekliyor" && !activeNotifications.contains(callId) && isNew) {
                                showCallNotification(callId, masaNo, tip, itemCount)
                            }
                        }
                        DocumentChange.Type.REMOVED -> {
                            cancelCallNotification(callId)
                        }
                    }
                }
            }

        // Listen for announcements in real-time
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        announcementListenerRegistration?.remove()
        directAnnouncementListenerRegistration?.remove()

        val branchFilterList = if (branchId.isNotEmpty() && branchId != "all") listOf(branchId, "all") else listOf("all")
        announcementListenerRegistration = db.collection("announcements")
            .whereIn("branchId", branchFilterList)
            .whereEqualTo("active", true)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                for (dc in snapshots.documentChanges) {
                    if (dc.type == DocumentChange.Type.ADDED) {
                        val doc = dc.document
                        val senderUid = doc.getString("senderUid") ?: ""
                        // Do not notify self for announcements sent by the current user
                        if (senderUid.isNotEmpty() && senderUid == currentUid) {
                            continue
                        }

                        val createdAt = doc.getTimestamp("createdAt")
                        val isNew = createdAt != null && createdAt.toDate().time >= serviceStartTime
                        val targetType = doc.getString("targetType") ?: "all"
                        val targetUserUid = doc.getString("targetUserUid") ?: doc.getString("targetUserId") ?: ""
                        @Suppress("UNCHECKED_CAST")
                        val readUids = doc.get("readUids") as? List<String> ?: emptyList()

                        val isForMe = targetType == "all" || targetType == "all_staff" || targetType == "branch" || targetUserUid == currentUid
                        val isUnread = !readUids.contains(currentUid)

                        if (isNew && isForMe && isUnread && !activeNotifications.contains(doc.id)) {
                            val title = doc.getString("title") ?: "Duyuru"
                            val message = doc.getString("message") ?: ""
                            val senderName = doc.getString("senderName") ?: "Yönetim"
                            val senderRole = doc.getString("senderRole") ?: "mudur"
                            val docBranchId = doc.getString("branchId") ?: ""
                            val isManagement = senderRole.trim().lowercase() in listOf("admin", "yonetici", "yönetici", "administrator", "sistem", "kurucu", "patron") ||
                                    targetType == "all_staff" ||
                                    (docBranchId == "all" && senderRole.trim().lowercase() !in listOf("mudur", "müdür", "manager"))
                            val sourceCategory = if (isManagement) "Yönetim Duyurusu" else "Müdür Duyurusu"
                            showAnnouncementNotification(doc.id, title, message, senderName, sourceCategory)
                        }
                    }
                }
            }

        // Direct user announcement listener (e.g. personal announcements from admin/manager)
        if (currentUid.isNotEmpty()) {
            directAnnouncementListenerRegistration = db.collection("announcements")
                .whereEqualTo("targetUserUid", currentUid)
                .whereEqualTo("active", true)
                .addSnapshotListener { snapshots, error ->
                    if (error != null || snapshots == null) return@addSnapshotListener
                    for (dc in snapshots.documentChanges) {
                        if (dc.type == DocumentChange.Type.ADDED) {
                            val doc = dc.document
                            val senderUid = doc.getString("senderUid") ?: ""
                            // Do not notify self for announcements sent by the current user
                            if (senderUid.isNotEmpty() && senderUid == currentUid) {
                                continue
                            }

                            val createdAt = doc.getTimestamp("createdAt")
                            val isNew = createdAt != null && createdAt.toDate().time >= serviceStartTime
                            @Suppress("UNCHECKED_CAST")
                            val readUids = doc.get("readUids") as? List<String> ?: emptyList()

                            if (isNew && !readUids.contains(currentUid) && !activeNotifications.contains(doc.id)) {
                                val title = doc.getString("title") ?: "Özel Duyuru"
                                val message = doc.getString("message") ?: ""
                                val senderName = doc.getString("senderName") ?: "Yönetim"
                                val senderRole = doc.getString("senderRole") ?: "mudur"
                                val docBranchId = doc.getString("branchId") ?: ""
                                val targetType = doc.getString("targetType") ?: "user"
                                val isManagement = senderRole.trim().lowercase() in listOf("admin", "yonetici", "yönetici", "administrator", "sistem", "kurucu", "patron") ||
                                        targetType == "all_staff" ||
                                        (docBranchId == "all" && senderRole.trim().lowercase() !in listOf("mudur", "müdür", "manager"))
                                val sourceCategory = if (isManagement) "Yönetim Duyurusu" else "Müdür Duyurusu"
                                showAnnouncementNotification(doc.id, title, message, senderName, sourceCategory)
                            }
                        }
                    }
                }
        }
    }

    private fun showAnnouncementNotification(
        announcementId: String,
        title: String,
        message: String,
        senderName: String,
        sourceCategory: String = "Duyuru"
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "announcements")
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            announcementId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ANNOUNCEMENT_ID)
            .setContentTitle("📢 $sourceCategory: $title")
            .setContentText("$senderName: $message")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(announcementId.hashCode(), notification)
        activeNotifications.add(announcementId)
    }

    private fun showCallNotification(callId: String, masaNo: String, tip: String, itemCount: Int = 0) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            callId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val formattedMasa = FormatUtils.formatMasaTitle(masaNo)
        val (title, message) = when (tip) {
            "siparis" -> "$formattedMasa — Yeni Sipariş" to "$itemCount ürün"
            "hesap" -> formattedMasa to "Hesap istiyor"
            else -> formattedMasa to "Garson çağırıyor"
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_CALL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pendingIntent)
            .setSound(null)
            .setVibrate(longArrayOf(0L))
            .setAutoCancel(true)
            .build()

        notificationManager.notify(callId.hashCode(), notification)
        activeNotifications.add(callId)

        // Play custom notification sound & vibration according to user settings
        SoundSettingsManager.playSoundAndVibrateForCallType(this, tip)
    }

    private fun cancelCallNotification(callId: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(callId.hashCode())
        activeNotifications.remove(callId)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Status channel for persistent notification
            val statusChannel = NotificationChannel(
                CHANNEL_STATUS_ID,
                "QR Servis Durumu",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Uygulamanın arka planda çalıştığını belirtir."
            }
            notificationManager.createNotificationChannel(statusChannel)

            // Call channel for active notifications (silent sound so MediaPlayer plays custom sound)
            val callChannel = NotificationChannel(
                CHANNEL_CALL_ID,
                "Masalardan Çağrılar",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Yeni garson veya hesap çağrılarını bildirir."
                enableLights(true)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(callChannel)

            // Announcement channel for Manager -> Waiter operations announcements (distinct from calls)
            val announcementChannel = NotificationChannel(
                CHANNEL_ANNOUNCEMENT_ID,
                "Müdür Duyuruları",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Müdürlerden personele operasyon duyuruları."
                enableLights(true)
            }
            notificationManager.createNotificationChannel(announcementChannel)
        }
    }

    override fun onDestroy() {
        listenerRegistration?.remove()
        announcementListenerRegistration?.remove()
        directAnnouncementListenerRegistration?.remove()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_STATUS_ID = "com.aistudio.qrservis.pqwtzm.durum_kanali"
        const val CHANNEL_CALL_ID = "com.aistudio.qrservis.pqwtzm.cagri_kanali"
        const val CHANNEL_ANNOUNCEMENT_ID = "com.aistudio.qrservis.pqwtzm.duyuru_kanali"
        const val NOTIFICATION_FOREGROUND_ID = 9991
    }
}
