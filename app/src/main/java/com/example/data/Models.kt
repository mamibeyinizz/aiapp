package com.example.data

import com.google.firebase.Timestamp

data class Branch(
    val id: String = "",
    val ad: String = "",
    val slug: String = "",
    val apiKey: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val radius: Double? = 150.0,
    val locationCheckEnabled: Boolean = true,
    val aktif: Boolean = true,
    val createdAt: Timestamp? = null,
    val address: String? = null
)

data class User(
    val uid: String = "",
    val kullaniciAdi: String = "",
    val rol: String = "", // admin, mudur, garson
    val branchId: String = "",
    val ad: String = "",
    val fcmToken: String = "",
    val aktif: Boolean = true,
    val createdAt: Timestamp? = null
)

data class Table(
    val id: String = "",
    val branchId: String = "",
    val masaNo: String = "",
    val createdAt: Timestamp? = null
)

data class CallOrderItem(
    val urunAdi: String = "",
    val adet: Long = 1,
    val notOrijinal: String? = null,
    val notTr: String? = null,
    val ceviri_hata: Boolean? = null
)

data class Call(
    val id: String = "",
    val branchId: String = "",
    val masaNo: String = "",
    val tip: String = "", // "garson", "hesap", "siparis"
    val durum: String = "", // "bekliyor", "onaylandi", "iptal"
    val onaylayanUid: String = "",
    val onaylayanAd: String = "",
    val createdAt: Timestamp? = null,
    val onaylandiAt: Timestamp? = null,
    val items: List<CallOrderItem> = emptyList(),
    val notDili: String? = null,
    val iptalNedeni: String = "",
    val iptalEdenAd: String = "",
    val iptalAt: Timestamp? = null
)

data class Shift(
    val id: String = "",
    val branchId: String = "",
    val garsonUid: String = "",
    val garsonAd: String = "",
    val baslangic: Timestamp? = null,
    val bitis: Timestamp? = null,
    val durum: String = "", // "acik", "kapali"
    val molada: Boolean = false,
    val molaBaslangic: Timestamp? = null,
    val startAt: Timestamp? = baslangic,
    val endAt: Timestamp? = bitis
)

data class ShiftBreak(
    val id: String = "",
    val baslangic: Timestamp? = null,
    val bitis: Timestamp? = null,
    val startAt: Timestamp? = baslangic,
    val endAt: Timestamp? = bitis,
    val sure: String = ""
)

data class ShiftWithBreaks(
    val shift: Shift,
    val breaks: List<ShiftBreak> = emptyList()
)

data class AnalyticsProductItem(
    val urunAd: String = "",
    val kategori: String = "",
    val tiklamaAdedi: Int = 0
)

data class AnalyticsTableItem(
    val masaAd: String = "",
    val goruntulenmeAdedi: Int = 0
)

data class AnalyticsResponse(
    val toplamGoruntulenme: Int = 0,
    val tekilZiyaretci: Int = 0,
    val toplamTiklama: Int = 0,
    val enCok: List<AnalyticsProductItem> = emptyList(),
    val enAz: List<AnalyticsProductItem> = emptyList(),
    val masalar: List<AnalyticsTableItem> = emptyList()
)

data class Announcement(
    val id: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val senderRole: String = "mudur",
    val targetType: String = "all", // "all_staff" (Tüm Personel), "all" (Tüm Garsonlar), "branch" (Belirli Şube), "user" (Belirli Personel)
    val targetBranchId: String = "",
    val targetBranchName: String = "",
    val targetUserUid: String = "",
    val targetUserId: String = targetUserUid,
    val targetUserName: String = "",
    val title: String = "",
    val message: String = "",
    val createdAt: Timestamp? = null,
    val active: Boolean = true,
    val readUids: List<String> = emptyList()
) {
    fun isRead(uid: String): Boolean = readUids.contains(uid)

    fun isManagement(): Boolean {
        val role = senderRole.trim().lowercase()
        return role in listOf("admin", "yonetici", "yönetici", "administrator", "sistem", "kurucu", "patron") ||
                targetType == "all_staff" ||
                (branchId == "all" && role !in listOf("mudur", "müdür", "manager"))
    }

    fun isManager(): Boolean = !isManagement()

    fun getSourceCategoryName(): String = if (isManagement()) "Yönetim Duyurusu" else "Müdür Duyurusu"
}

data class AnnouncementRead(
    val uid: String = "",
    val readAt: Timestamp? = null
)

data class AuditLog(
    val id: String = "",
    val branchId: String = "",
    val actorUid: String = "",
    val actorName: String = "",
    val action: String = "",
    val details: String = "",
    val createdAt: Timestamp? = null
)

data class DailyTask(
    val id: String = "",
    val branchId: String = "",
    val title: String = "",
    val description: String = "",
    val completed: Boolean = false,
    val completedByName: String = "",
    val completedAt: Timestamp? = null,
    val createdAt: Timestamp? = null
)

data class ShiftPlan(
    val id: String = "",
    val branchId: String = "",
    val userUid: String = "",
    val userName: String = "",
    val dayOfWeek: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val isOffDay: Boolean = false,
    val notes: String = ""
)
