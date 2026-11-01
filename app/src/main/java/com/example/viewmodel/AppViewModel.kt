package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.service.CallsForegroundService
import com.example.utils.ErrorUtils
import com.example.utils.ShiftDateTimeUtils
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

const val WORDPRESS_BASE_URL = "https://full.qrmenuofficial.com"

sealed interface LoginState {
    object Idle : LoginState
    object Loading : LoginState
    object Success : LoginState
    data class Error(val message: String) : LoginState
}

data class ApprovedCallInfo(
    val masaNo: String,
    val onaylayanAd: String,
    val timestamp: Long = System.currentTimeMillis()
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val context = application.applicationContext

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    // Session states
    private val _userRole = MutableStateFlow("")
    val userRole: StateFlow<String> = _userRole

    private val _userBranchId = MutableStateFlow("")
    val userBranchId: StateFlow<String> = _userBranchId

    private val _userAd = MutableStateFlow("")
    val userAd: StateFlow<String> = _userAd

    private val _userKullaniciAdi = MutableStateFlow("")
    val userKullaniciAdi: StateFlow<String> = _userKullaniciAdi

    // Flows for Admin
    private val _branches = MutableStateFlow<List<Branch>>(emptyList())
    val branches: StateFlow<List<Branch>> = _branches

    private val _allUsers = MutableStateFlow<List<User>>(emptyList())
    val allUsers: StateFlow<List<User>> = _allUsers

    private val _currentBranch = MutableStateFlow<Branch?>(null)
    val currentBranch: StateFlow<Branch?> = _currentBranch

    private val _branchUsers = MutableStateFlow<List<User>>(emptyList())
    val branchUsers: StateFlow<List<User>> = _branchUsers

    // Flows for Manager & Waiter
    private val _tables = MutableStateFlow<List<Table>>(emptyList())
    val tables: StateFlow<List<Table>> = _tables

    private val _waiters = MutableStateFlow<List<User>>(emptyList())
    val waiters: StateFlow<List<User>> = _waiters

    private val _calls = MutableStateFlow<List<Call>>(emptyList())
    val calls: StateFlow<List<Call>> = _calls

    private val prefs by lazy { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }

    private val _clearedNotificationTimestamp = MutableStateFlow(
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getLong("cleared_notification_ts", 0L)
    )
    val clearedNotificationTimestamp: StateFlow<Long> = _clearedNotificationTimestamp

    fun clearApprovedNotifications() {
        val now = System.currentTimeMillis()
        _clearedNotificationTimestamp.value = now
        prefs.edit().putLong("cleared_notification_ts", now).apply()
    }

    private val _approvedCallEvent = MutableStateFlow<ApprovedCallInfo?>(null)
    val approvedCallEvent: StateFlow<ApprovedCallInfo?> = _approvedCallEvent

    // Shift state
    private val _activeShift = MutableStateFlow<Shift?>(null)
    val activeShift: StateFlow<Shift?> = _activeShift

    private val _activeShiftBreaks = MutableStateFlow<List<ShiftBreak>>(emptyList())
    val activeShiftBreaks: StateFlow<List<ShiftBreak>> = _activeShiftBreaks

    // Announcements state (Manager -> Waiter)
    private val _announcements = MutableStateFlow<List<Announcement>>(emptyList())
    val announcements: StateFlow<List<Announcement>> = _announcements

    private val _branchActiveShifts = MutableStateFlow<Map<String, Shift>>(emptyMap())
    val branchActiveShifts: StateFlow<Map<String, Shift>> = _branchActiveShifts

    private var shiftListenerRegistration: ListenerRegistration? = null
    private var activeShiftBreaksListener: ListenerRegistration? = null
    private var branchActiveShiftsListener: ListenerRegistration? = null
    private var announcementsListenerRegistration: ListenerRegistration? = null
    private var userDirectAnnouncementsListenerRegistration: ListenerRegistration? = null
    private var userDirectLegacyListenerRegistration: ListenerRegistration? = null
    private var userSentAnnouncementsListenerRegistration: ListenerRegistration? = null
    private val branchAnnouncementsMap = java.util.concurrent.ConcurrentHashMap<String, Announcement>()
    private val directAnnouncementsMap = java.util.concurrent.ConcurrentHashMap<String, Announcement>()

    // Listeners list to clean up
    private val activeListeners = mutableListOf<ListenerRegistration>()
    private val knownCallStates = mutableMapOf<String, String>()
    private var callsListenerRegistration: ListenerRegistration? = null

    init {
        checkCurrentUser()
    }

    private fun checkCurrentUser() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val prefs = context.getSharedPreferences("qr_servis_prefs", Context.MODE_PRIVATE)
            val role = prefs.getString("rol", "") ?: ""
            val branchId = prefs.getString("branch_id", "") ?: ""
            val ad = prefs.getString("ad", "") ?: ""
            val kullaniciAdi = prefs.getString("kullanici_adi", "") ?: ""

            _userRole.value = role
            _userBranchId.value = branchId
            _userAd.value = ad
            _userKullaniciAdi.value = kullaniciAdi

            listenShiftForUser(currentUser.uid)
            updateFcmTokenIfAvailable()

            if (role.isNotEmpty()) {
                setupListenersForRole(role, branchId)
                startForegroundServiceIfNeeded()
            }

            // Sync user details in background
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val rawRole = (doc.getString("rol")?.takeIf { it.isNotBlank() } ?: doc.getString("role") ?: "")
                        val realRole = when (rawRole.trim().lowercase()) {
                            "admin", "yonetici", "yönetici", "administrator", "sistem", "kurucu", "patron" -> "admin"
                            "mudur", "müdür", "manager" -> "mudur"
                            "garson", "waiter" -> "garson"
                            else -> rawRole.trim().lowercase()
                        }
                        val realBranchId = doc.getString("branchId") ?: ""
                        val realAd = doc.getString("ad") ?: ""
                        val realKullaniciAdi = doc.getString("kullaniciAdi") ?: ""

                        val editor = prefs.edit()
                        editor.putString("rol", realRole)
                        editor.putString("branch_id", realBranchId)
                        editor.putString("ad", realAd)
                        editor.putString("kullanici_adi", realKullaniciAdi)
                        editor.apply()

                        _userRole.value = realRole
                        _userBranchId.value = realBranchId
                        _userAd.value = realAd
                        _userKullaniciAdi.value = realKullaniciAdi

                        if (realRole != role || realBranchId != branchId) {
                            setupListenersForRole(realRole, realBranchId)
                        }
                    } else {
                        // User deleted, force logout
                        logout()
                    }
                }
        }
    }

    private fun extractTimestamp(doc: com.google.firebase.firestore.DocumentSnapshot, vararg fieldNames: String): Timestamp? {
        for (name in fieldNames) {
            val value = doc.get(name) ?: continue
            when (value) {
                is Timestamp -> return value
                is java.util.Date -> return Timestamp(value)
                is Number -> {
                    val longVal = value.toLong()
                    return if (longVal > 100_000_000_000L) {
                        Timestamp(java.util.Date(longVal))
                    } else if (longVal > 0) {
                        Timestamp(longVal, 0)
                    } else null
                }
                is String -> {
                    val trimmed = value.trim()
                    if (trimmed.isNotEmpty()) {
                        try {
                            val sdfIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                            val d = sdfIso.parse(trimmed)
                            if (d != null) return Timestamp(d)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
        return null
    }

    fun ensureShiftListener() {
        val uid = auth.currentUser?.uid ?: ""
        if (uid.isNotEmpty() && shiftListenerRegistration == null) {
            listenShiftForUser(uid)
        }
    }

    fun listenShiftForUser(uid: String) {
        if (uid.isEmpty()) {
            shiftListenerRegistration?.remove()
            shiftListenerRegistration = null
            _activeShift.value = null
            return
        }
        if (shiftListenerRegistration != null) return

        shiftListenerRegistration = db.collection("shifts")
            .whereEqualTo("garsonUid", uid)
            .whereEqualTo("durum", "acik")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    val shift = Shift(
                        id = doc.id,
                        branchId = doc.getString("branchId") ?: "",
                        garsonUid = doc.getString("garsonUid") ?: "",
                        garsonAd = doc.getString("garsonAd") ?: "",
                        baslangic = extractTimestamp(doc, "baslangic", "startTimestamp", "startTime", "startAt", "createdAt"),
                        bitis = extractTimestamp(doc, "bitis", "endTimestamp", "endTime", "endAt"),
                        durum = doc.getString("durum") ?: "",
                        molada = doc.getBoolean("molada") ?: false,
                        molaBaslangic = extractTimestamp(doc, "molaBaslangic", "breakStart")
                    )
                    _activeShift.value = shift
                    listenActiveShiftBreaks(shift.id)
                    if (_userRole.value == "garson") {
                        if (!shift.molada) {
                            startForegroundService()
                        } else {
                            stopForegroundService()
                        }
                    }
                } else {
                    _activeShift.value = null
                    stopActiveShiftBreaksListener()
                    if (_userRole.value == "garson") {
                        stopForegroundService()
                    }
                }
            }
    }

    private fun listenActiveShiftBreaks(shiftId: String) {
        activeShiftBreaksListener?.remove()
        if (shiftId.isEmpty()) {
            _activeShiftBreaks.value = emptyList()
            return
        }
        activeShiftBreaksListener = db.collection("shifts").document(shiftId)
            .collection("molalar")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val list = snapshot.documents.map { doc ->
                    ShiftBreak(
                        id = doc.id,
                        baslangic = extractTimestamp(doc, "baslangic", "startTimestamp", "startTime", "startAt", "createdAt"),
                        bitis = extractTimestamp(doc, "bitis", "endTimestamp", "endTime", "endAt")
                    )
                }.sortedByDescending { it.baslangic?.seconds ?: 0L }
                _activeShiftBreaks.value = list
            }
    }

    private fun stopActiveShiftBreaksListener() {
        activeShiftBreaksListener?.remove()
        activeShiftBreaksListener = null
        _activeShiftBreaks.value = emptyList()
    }

    fun stopShiftListener() {
        shiftListenerRegistration?.remove()
        shiftListenerRegistration = null
    }

    fun login(kullaniciAdi: String, sifre: String) {
        if (kullaniciAdi.isBlank() || sifre.isBlank()) {
            _loginState.value = LoginState.Error("Kullanıcı adı ve şifre boş olamaz.")
            return
        }

        _loginState.value = LoginState.Loading
        val email = kullaniciAdi.trim().lowercase() + "@qrservis.local"

        auth.signInWithEmailAndPassword(email, sifre)
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid ?: ""
                db.collection("users").document(uid).get()
                    .addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            val rawRole = (doc.getString("rol")?.takeIf { it.isNotBlank() } ?: doc.getString("role") ?: "")
                            val role = when (rawRole.trim().lowercase()) {
                                "admin", "yonetici", "yönetici", "administrator", "sistem", "kurucu", "patron" -> "admin"
                                "mudur", "müdür", "manager" -> "mudur"
                                "garson", "waiter" -> "garson"
                                else -> rawRole.trim().lowercase()
                            }
                            val branchId = doc.getString("branchId") ?: ""
                            val ad = doc.getString("ad") ?: ""
                            val username = doc.getString("kullaniciAdi") ?: ""

                            // Save locally
                            val prefs = context.getSharedPreferences("qr_servis_prefs", Context.MODE_PRIVATE)
                            val editor = prefs.edit()
                            editor.putString("rol", role)
                            editor.putString("branch_id", branchId)
                            editor.putString("ad", ad)
                            editor.putString("kullanici_adi", username)
                            editor.apply()

                            _userRole.value = role
                            _userBranchId.value = branchId
                            _userAd.value = ad
                            _userKullaniciAdi.value = username

                            listenShiftForUser(uid)

                            setupListenersForRole(role, branchId)
                            updateFcmTokenIfAvailable()
                            startForegroundServiceIfNeeded()
                            _loginState.value = LoginState.Success
                        } else {
                            auth.signOut()
                            _loginState.value = LoginState.Error("Kullanıcı kaydı bulunamadı.")
                        }
                    }
                    .addOnFailureListener { e ->
                        auth.signOut()
                        _loginState.value = LoginState.Error(ErrorUtils.parseError(e, "Kullanıcı kaydı bulunamadı."))
                    }
            }
            .addOnFailureListener { e ->
                _loginState.value = LoginState.Error(ErrorUtils.parseError(e, "Kullanıcı adı veya şifre hatalı"))
            }
    }

    fun logout() {
        stopForegroundService()
        shiftListenerRegistration?.remove()
        shiftListenerRegistration = null
        _activeShift.value = null

        // Clear listeners
        clearAllListeners()

        // Clear preferences
        val prefs = context.getSharedPreferences("qr_servis_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        _userRole.value = ""
        _userBranchId.value = ""
        _userAd.value = ""
        _userKullaniciAdi.value = ""
        _loginState.value = LoginState.Idle

        auth.signOut()
    }

    fun resetLoginState() {
        _loginState.value = LoginState.Idle
    }

    private fun startForegroundServiceIfNeeded() {
        val role = _userRole.value
        val branchId = _userBranchId.value
        if (branchId.isNotEmpty()) {
            if (role == "mudur") {
                startForegroundService()
            } else if (role == "garson") {
                val shift = _activeShift.value
                if (shift != null && !shift.molada) {
                    startForegroundService()
                } else {
                    stopForegroundService()
                }
            }
        }
    }

    private fun startForegroundService() {
        try {
            val serviceIntent = Intent(context, CallsForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {}
    }

    fun stopForegroundService() {
        try {
            val serviceIntent = Intent(context, CallsForegroundService::class.java)
            context.stopService(serviceIntent)
        } catch (e: Exception) {}
    }

    private var currentBranchListener: ListenerRegistration? = null

    fun listenToCurrentBranch(branchId: String) {
        if (branchId.isEmpty()) return
        currentBranchListener?.remove()
        currentBranchListener = db.collection("branches").document(branchId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                _currentBranch.value = Branch(
                    id = snapshot.id,
                    ad = snapshot.getString("ad") ?: "",
                    slug = snapshot.getString("slug") ?: "",
                    apiKey = snapshot.getString("apiKey") ?: "",
                    lat = snapshot.getDouble("lat"),
                    lng = snapshot.getDouble("lng"),
                    radius = snapshot.getDouble("radius") ?: 150.0,
                    locationCheckEnabled = snapshot.getBoolean("locationCheckEnabled") ?: true,
                    aktif = snapshot.getBoolean("aktif") ?: true,
                    createdAt = snapshot.getTimestamp("createdAt")
                )
            }
    }

    private fun clearAllListeners() {
        activeListeners.forEach { it.remove() }
        activeListeners.clear()
        callsListenerRegistration?.remove()
        callsListenerRegistration = null
        stopAnnouncementsListener()
        stopActiveShiftBreaksListener()
        branchActiveShiftsListener?.remove()
        branchActiveShiftsListener = null
        currentBranchListener?.remove()
        currentBranchListener = null
        _currentBranch.value = null
        _allUsers.value = emptyList()
        _branchActiveShifts.value = emptyMap()
        knownCallStates.clear()
    }

    private fun setupListenersForRole(role: String, branchId: String) {
        clearAllListeners()

        if (branchId.isNotEmpty()) {
            listenToCurrentBranch(branchId)
        }

        if (role == "admin") {
            // Listen to all branches
            val branchesListener = db.collection("branches")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshots, error ->
                    if (error != null || snapshots == null) return@addSnapshotListener
                    val list = snapshots.documents.map { doc ->
                        Branch(
                            id = doc.id,
                            ad = doc.getString("ad") ?: "",
                            slug = doc.getString("slug") ?: "",
                            apiKey = doc.getString("apiKey") ?: "",
                            lat = doc.getDouble("lat"),
                            lng = doc.getDouble("lng"),
                            radius = doc.getDouble("radius") ?: 150.0,
                            locationCheckEnabled = doc.getBoolean("locationCheckEnabled") ?: true,
                            aktif = doc.getBoolean("aktif") ?: true,
                            createdAt = doc.getTimestamp("createdAt")
                        )
                    }
                    _branches.value = list
                }
            activeListeners.add(branchesListener)

            // Listen to all users so Admin can see personnel counts per branch
            val usersListener = db.collection("users")
                .addSnapshotListener { snapshots, error ->
                    if (error != null || snapshots == null) return@addSnapshotListener
                    val list = snapshots.documents.map { doc ->
                        User(
                            uid = doc.id,
                            kullaniciAdi = doc.getString("kullaniciAdi") ?: "",
                            rol = doc.getString("rol") ?: "",
                            branchId = doc.getString("branchId") ?: "",
                            ad = doc.getString("ad") ?: "",
                            fcmToken = doc.getString("fcmToken") ?: "",
                            aktif = doc.getBoolean("aktif") ?: true,
                            createdAt = doc.getTimestamp("createdAt")
                        )
                    }
                    _allUsers.value = list
                }
            activeListeners.add(usersListener)
            startAnnouncementsListener()
        } else if (role == "mudur") {
            // Listen to tables
            val tablesListener = db.collection("tables")
                .whereEqualTo("branchId", branchId)
                .addSnapshotListener { snapshots, error ->
                    if (error != null || snapshots == null) return@addSnapshotListener
                    val list = snapshots.documents.map { doc ->
                        Table(
                            id = doc.id,
                            branchId = doc.getString("branchId") ?: "",
                            masaNo = doc.getString("masaNo") ?: "",
                            createdAt = doc.getTimestamp("createdAt")
                        )
                    }
                    _tables.value = list
                }
            activeListeners.add(tablesListener)

            // Listen to waiters
            val waitersListener = db.collection("users")
                .whereEqualTo("branchId", branchId)
                .whereEqualTo("rol", "garson")
                .addSnapshotListener { snapshots, error ->
                    if (error != null || snapshots == null) return@addSnapshotListener
                    val list = snapshots.documents.map { doc ->
                        User(
                            uid = doc.id,
                            kullaniciAdi = doc.getString("kullaniciAdi") ?: "",
                            rol = doc.getString("rol") ?: "",
                            branchId = doc.getString("branchId") ?: "",
                            ad = doc.getString("ad") ?: "",
                            fcmToken = doc.getString("fcmToken") ?: "",
                            createdAt = doc.getTimestamp("createdAt")
                        )
                    }
                    _waiters.value = list
                }
            activeListeners.add(waitersListener)

            // Listen to active calls and announcements
            startCallsListener(branchId)
            startAnnouncementsListener(branchId)
        } else if (role == "garson") {
            // Listen to active calls and announcements
            startCallsListener(branchId)
            startAnnouncementsListener(branchId)
        }
    }

    fun startCallsListener(branchId: String) {
        if (branchId.isEmpty()) return
        callsListenerRegistration?.remove()

        val listener = db.collection("calls")
            .whereEqualTo("branchId", branchId)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    android.util.Log.e("AppViewModel", "Calls listener failed: ${error.message}", error)
                    return@addSnapshotListener
                }
                if (snapshots == null) return@addSnapshotListener

                val list = snapshots.documents.map { doc ->
                    @Suppress("UNCHECKED_CAST")
                    val itemsRaw = doc.get("items") as? List<Map<String, Any>>
                    val itemsParsed = itemsRaw?.map { itemMap ->
                        CallOrderItem(
                            urunAdi = itemMap["urunAdi"] as? String ?: "",
                            adet = (itemMap["adet"] as? Number)?.toLong() ?: 1L,
                            notOrijinal = itemMap["notOrijinal"] as? String,
                            notTr = itemMap["notTr"] as? String,
                            ceviri_hata = itemMap["ceviri_hata"] as? Boolean
                        )
                    } ?: emptyList()

                    Call(
                        id = doc.id,
                        branchId = doc.getString("branchId") ?: "",
                        masaNo = doc.getString("masaNo") ?: "",
                        tip = doc.getString("tip") ?: "",
                        durum = doc.getString("durum") ?: "",
                        onaylayanUid = doc.getString("onaylayanUid") ?: "",
                        onaylayanAd = doc.getString("onaylayanAd") ?: "",
                        createdAt = doc.getTimestamp("createdAt"),
                        onaylandiAt = doc.getTimestamp("onaylandiAt"),
                        items = itemsParsed,
                        notDili = doc.getString("notDili"),
                        iptalNedeni = doc.getString("iptalNedeni") ?: "",
                        iptalEdenAd = doc.getString("iptalEdenAd") ?: "",
                        iptalAt = doc.getTimestamp("iptalAt")
                    )
                }.sortedByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
                .take(200)

                _calls.value = list

                // Check for state transitions (bekliyor -> onaylandi)
                for (dc in snapshots.documentChanges) {
                    val doc = dc.document
                    val callId = doc.id
                    val status = doc.getString("durum") ?: ""
                    val masaNo = doc.getString("masaNo") ?: ""
                    val onaylayanAd = doc.getString("onaylayanAd") ?: ""

                    if (dc.type == DocumentChange.Type.MODIFIED) {
                        val oldStatus = knownCallStates[callId]
                        if (oldStatus == "bekliyor" && status == "onaylandi") {
                            _approvedCallEvent.value = ApprovedCallInfo(masaNo, onaylayanAd)
                        }
                    }
                    knownCallStates[callId] = status
                }
            }
        callsListenerRegistration = listener
    }

    fun stopCallsListener() {
        callsListenerRegistration?.remove()
        callsListenerRegistration = null
    }

    fun clearApprovedCallEvent() {
        _approvedCallEvent.value = null
    }

    // --- ANNOUNCEMENT OPERATIONS (Admin & Manager -> Personnel) ---
    private fun updateCombinedAnnouncements() {
        val role = _userRole.value.trim().lowercase()
        val myBranch = _userBranchId.value
        val myUid = auth.currentUser?.uid ?: ""

        val rawList = (branchAnnouncementsMap.values + directAnnouncementsMap.values)
            .distinctBy { it.id }

        val filtered = when (role) {
            "admin" -> rawList
            "mudur" -> rawList.filter {
                it.branchId == myBranch || it.branchId == "all" ||
                it.targetBranchId == myBranch || it.targetBranchId == "all" ||
                it.targetType == "all_staff" ||
                it.targetType == "all" ||
                it.targetUserUid == myUid ||
                it.targetUserId == myUid ||
                it.senderUid == myUid
            }
            else -> rawList.filter {
                it.targetType == "all_staff" ||
                it.targetType == "all" ||
                (it.targetType == "branch" && (it.branchId == myBranch || it.targetBranchId == myBranch || it.branchId == "all")) ||
                it.targetUserUid == myUid ||
                it.targetUserId == myUid
            }
        }

        _announcements.value = filtered.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
    }

    private fun parseAnnouncementDoc(doc: com.google.firebase.firestore.DocumentSnapshot): Announcement {
        @Suppress("UNCHECKED_CAST")
        val readList = doc.get("readUids") as? List<String> ?: emptyList()
        val targetUid = doc.getString("targetUserUid") ?: doc.getString("targetUserId") ?: ""
        return Announcement(
            id = doc.id,
            branchId = doc.getString("branchId") ?: "",
            branchName = doc.getString("branchName") ?: "",
            senderUid = doc.getString("senderUid") ?: "",
            senderName = doc.getString("senderName") ?: "",
            senderRole = doc.getString("senderRole") ?: "mudur",
            targetType = doc.getString("targetType") ?: "all",
            targetBranchId = doc.getString("targetBranchId") ?: doc.getString("branchId") ?: "",
            targetBranchName = doc.getString("targetBranchName") ?: doc.getString("branchName") ?: "",
            targetUserUid = targetUid,
            targetUserId = targetUid,
            targetUserName = doc.getString("targetUserName") ?: "",
            title = doc.getString("title") ?: "",
            message = doc.getString("message") ?: "",
            createdAt = doc.getTimestamp("createdAt"),
            active = doc.getBoolean("active") ?: true,
            readUids = readList
        )
    }

    fun startAnnouncementsListener(branchId: String = "") {
        announcementsListenerRegistration?.remove()
        userDirectAnnouncementsListenerRegistration?.remove()
        userDirectLegacyListenerRegistration?.remove()
        userSentAnnouncementsListenerRegistration?.remove()
        branchAnnouncementsMap.clear()
        directAnnouncementsMap.clear()

        val role = _userRole.value.trim().lowercase()
        val effectiveBranchId = if (branchId.isNotEmpty()) branchId else _userBranchId.value
        val currentUid = auth.currentUser?.uid ?: ""

        // Listener 1: Branch & Global announcements
        val query = if (role == "admin" && effectiveBranchId.isEmpty()) {
            db.collection("announcements")
                .whereEqualTo("active", true)
        } else {
            val branchFilterList = if (effectiveBranchId.isNotEmpty() && effectiveBranchId != "all") {
                listOf(effectiveBranchId, "all")
            } else {
                listOf("all")
            }
            db.collection("announcements")
                .whereIn("branchId", branchFilterList)
                .whereEqualTo("active", true)
        }

        announcementsListenerRegistration = query.addSnapshotListener { snapshots, error ->
            if (error != null) {
                android.util.Log.e("AppViewModel", "Branch announcements listener error: ${error.message}", error)
                return@addSnapshotListener
            }
            if (snapshots == null) return@addSnapshotListener

            branchAnnouncementsMap.clear()
            for (doc in snapshots.documents) {
                val announcement = parseAnnouncementDoc(doc)
                branchAnnouncementsMap[announcement.id] = announcement
            }
            updateCombinedAnnouncements()
        }

        // Listener 2: Direct announcements for current user (targetUserUid)
        if (currentUid.isNotEmpty()) {
            userDirectAnnouncementsListenerRegistration = db.collection("announcements")
                .whereEqualTo("targetUserUid", currentUid)
                .whereEqualTo("active", true)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        android.util.Log.e("AppViewModel", "Direct user announcements listener error: ${error.message}", error)
                        return@addSnapshotListener
                    }
                    if (snapshots == null) return@addSnapshotListener

                    for (doc in snapshots.documents) {
                        val announcement = parseAnnouncementDoc(doc)
                        directAnnouncementsMap[announcement.id] = announcement
                    }
                    updateCombinedAnnouncements()
                }

            // Listener 3: Legacy direct announcements (targetUserId)
            userDirectLegacyListenerRegistration = db.collection("announcements")
                .whereEqualTo("targetUserId", currentUid)
                .whereEqualTo("active", true)
                .addSnapshotListener { snapshots, error ->
                    if (error != null || snapshots == null) return@addSnapshotListener
                    for (doc in snapshots.documents) {
                        val announcement = parseAnnouncementDoc(doc)
                        directAnnouncementsMap[announcement.id] = announcement
                    }
                    updateCombinedAnnouncements()
                }

            // Listener 4: Announcements sent by current user (for manager/admin history)
            userSentAnnouncementsListenerRegistration = db.collection("announcements")
                .whereEqualTo("senderUid", currentUid)
                .whereEqualTo("active", true)
                .addSnapshotListener { snapshots, error ->
                    if (error != null || snapshots == null) return@addSnapshotListener
                    for (doc in snapshots.documents) {
                        val announcement = parseAnnouncementDoc(doc)
                        directAnnouncementsMap[announcement.id] = announcement
                    }
                    updateCombinedAnnouncements()
                }
        }
    }

    fun stopAnnouncementsListener() {
        announcementsListenerRegistration?.remove()
        announcementsListenerRegistration = null
        userDirectAnnouncementsListenerRegistration?.remove()
        userDirectAnnouncementsListenerRegistration = null
        userDirectLegacyListenerRegistration?.remove()
        userDirectLegacyListenerRegistration = null
        userSentAnnouncementsListenerRegistration?.remove()
        userSentAnnouncementsListenerRegistration = null
        branchAnnouncementsMap.clear()
        directAnnouncementsMap.clear()
        _announcements.value = emptyList()
    }

    fun sendAnnouncement(
        branchId: String,
        branchName: String = "",
        title: String,
        message: String,
        targetType: String, // "all_staff" (Tüm Personel), "all" (Şubedeki Tüm Garsonlar), "branch" (Belirli Şube), "user" (Belirli Personel)
        targetBranchId: String = "",
        targetBranchName: String = "",
        targetUserUid: String = "",
        targetUserName: String = "",
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            onError("Oturum süreniz dolmuş, lütfen tekrar giriş yapın.")
            return
        }
        val uid = currentUser.uid

        // Robust role determination
        val storedRole = _userRole.value.trim().lowercase()
        val userEmail = currentUser.email?.lowercase() ?: ""
        val role = when {
            storedRole in listOf("admin", "yonetici", "yönetici", "administrator", "sistem", "kurucu", "patron") ||
            userEmail == "admin@qrservis.local" ||
            userEmail == "muhammedulusoyresmi@gmail.com" ||
            userEmail.contains("admin") ||
            userEmail.contains("yonetici") -> "admin"
            storedRole in listOf("mudur", "müdür", "manager") -> "mudur"
            storedRole in listOf("garson", "waiter") -> "garson"
            else -> if (_userBranchId.value.isEmpty()) "admin" else "mudur"
        }

        // Role authorization check
        if (role == "garson") {
            onError("Garsonların duyuru gönderme yetkisi bulunmamaktadır.")
            return
        }
        if (role != "admin" && role != "mudur") {
            onError("Yalnızca Yönetici ve Şube Müdürleri duyuru gönderebilir.")
            return
        }

        // Title and message validation
        if (title.isBlank() || message.isBlank()) {
            onError("Duyuru başlığı ve mesajı boş bırakılamaz.")
            return
        }

        // Branch and Manager boundaries check
        val finalBranchId: String
        val finalBranchName: String
        val finalTargetBranchId: String

        if (role == "mudur") {
            val managerBranchId = _userBranchId.value
            if (managerBranchId.isEmpty()) {
                onError("Şube yetkiniz bulunamadı.")
                return
            }
            if (targetType == "all_staff" || branchId == "all" || targetBranchId == "all") {
                onError("Şube müdürleri tüm şirkete duyuru gönderemez, yalnızca kendi şubelerine gönderebilir.")
                return
            }
            if (branchId.isNotEmpty() && branchId != managerBranchId) {
                onError("Yalnızca kendi şubenize duyuru gönderebilirsiniz.")
                return
            }
            if (targetType == "user" && targetUserUid.isNotEmpty()) {
                val targetUser = _allUsers.value.find { it.uid == targetUserUid }
                    ?: _waiters.value.find { it.uid == targetUserUid }
                if (targetUser != null && targetUser.branchId.isNotEmpty() && targetUser.branchId != managerBranchId) {
                    onError("Yalnızca kendi şubenizdeki personeli hedefleyebilirsiniz.")
                    return
                }
            }
            finalBranchId = managerBranchId
            finalTargetBranchId = managerBranchId
            finalBranchName = branchName.ifEmpty {
                _branches.value.find { it.id == managerBranchId }?.ad ?: "Şubem"
            }
        } else {
            // Admin role:
            when (targetType) {
                "all_staff" -> {
                    finalBranchId = "all"
                    finalTargetBranchId = "all"
                    finalBranchName = "Tüm Şubeler"
                }
                "branch", "all" -> {
                    finalBranchId = if (branchId.isNotEmpty() && branchId != "all") branchId else targetBranchId
                    if (finalBranchId.isEmpty()) {
                        onError("Lütfen hedef şube seçin.")
                        return
                    }
                    finalTargetBranchId = finalBranchId
                    val matchedBranch = _branches.value.find { it.id == finalBranchId }
                    finalBranchName = branchName.ifEmpty { matchedBranch?.ad ?: targetBranchName }
                }
                "user" -> {
                    var resolvedBranchId = if (branchId.isNotEmpty() && branchId != "all") branchId else targetBranchId
                    if (resolvedBranchId.isEmpty() && targetUserUid.isNotEmpty()) {
                        resolvedBranchId = _allUsers.value.find { it.uid == targetUserUid }?.branchId
                            ?: _waiters.value.find { it.uid == targetUserUid }?.branchId ?: ""
                    }
                    finalBranchId = if (resolvedBranchId.isNotEmpty()) resolvedBranchId else "all"
                    finalTargetBranchId = finalBranchId
                    val matchedBranch = _branches.value.find { it.id == finalBranchId }
                    finalBranchName = branchName.ifEmpty { matchedBranch?.ad ?: targetBranchName.ifEmpty { "Genel" } }
                }
                else -> {
                    finalBranchId = if (branchId.isNotEmpty()) branchId else "all"
                    finalTargetBranchId = finalBranchId
                    finalBranchName = branchName.ifEmpty { "Tüm Şubeler" }
                }
            }
        }

        val senderName = _userAd.value.ifBlank {
            if (role == "admin") "Yönetici (Admin)" else "Şube Müdürü"
        }

        val data = hashMapOf(
            "branchId" to finalBranchId,
            "branchName" to finalBranchName,
            "senderUid" to uid,
            "senderName" to senderName,
            "senderRole" to role,
            "targetType" to targetType,
            "targetBranchId" to finalTargetBranchId,
            "targetBranchName" to finalBranchName,
            "targetUserUid" to targetUserUid,
            "targetUserId" to targetUserUid,
            "targetUserName" to targetUserName,
            "title" to title.trim(),
            "message" to message.trim(),
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "active" to true,
            "readUids" to emptyList<String>()
        )

        db.collection("announcements").add(data)
            .addOnSuccessListener {
                logAuditEvent(
                    branchId = finalBranchId,
                    action = "Duyuru Gönderildi",
                    details = "Başlık: ${title.trim()} | Hedef: $targetType" +
                            if (targetUserName.isNotEmpty()) " ($targetUserName)" else " ($finalBranchName)"
                )

                // FCM push availability check
                val playServicesAvailable = try {
                    val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
                    availability.isGooglePlayServicesAvailable(context) == com.google.android.gms.common.ConnectionResult.SUCCESS
                } catch (_: Exception) {
                    false
                }

                val hasPushToken = if (targetType == "user") {
                    val targetUser = _allUsers.value.find { it.uid == targetUserUid }
                        ?: _waiters.value.find { it.uid == targetUserUid }
                    !targetUser?.fcmToken.isNullOrBlank()
                } else {
                    true
                }

                val statusMessage = if (!playServicesAvailable || (!hasPushToken && targetType == "user")) {
                    "Duyuru kaydedildi ancak bildirim gönderilemedi."
                } else {
                    "Duyuru gönderildi"
                }

                onSuccess(statusMessage)
            }
            .addOnFailureListener { e ->
                android.util.Log.e("AppViewModel", "sendAnnouncement failed: ${e.message}", e)
                val detailedMsg = e.localizedMessage?.takeIf { it.isNotBlank() } ?: "Firestore kayıt hatası"
                onError(ErrorUtils.parseError(e, "Duyuru kaydedilemedi: $detailedMsg"))
            }
    }

    fun markAnnouncementAsRead(announcementId: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: return
        if (announcementId.isEmpty()) return

        // 1. Write per-user subcollection record
        db.collection("announcements").document(announcementId)
            .collection("reads").document(uid)
            .set(hashMapOf(
                "uid" to uid,
                "readAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            ))

        // 2. Add to readUids array for fast reactive update
        db.collection("announcements").document(announcementId)
            .update("readUids", com.google.firebase.firestore.FieldValue.arrayUnion(uid))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(ErrorUtils.parseError(e, "Okundu işaretlenemedi.")) }
    }

    fun updateFcmTokenIfAvailable() {
        val currentUser = auth.currentUser ?: return
        try {
            val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
            val resultCode = availability.isGooglePlayServicesAvailable(context)
            if (resultCode != com.google.android.gms.common.ConnectionResult.SUCCESS) {
                android.util.Log.d("FCM", "Google Play Services is not available ($resultCode), skipping FCM token update.")
                return
            }

            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        if (!token.isNullOrEmpty()) {
                            db.collection("users").document(currentUser.uid)
                                .update("fcmToken", token)
                        }
                    } else {
                        android.util.Log.d("FCM", "FCM token not available in this environment: ${task.exception?.message}")
                    }
                }
        } catch (e: Throwable) {
            android.util.Log.d("FCM", "FCM not supported: ${e.message}")
        }
    }

    // Audit Logging
    fun logAuditEvent(branchId: String, action: String, details: String) {
        val currentUser = auth.currentUser ?: return
        val actorName = _userAd.value.ifBlank { _userKullaniciAdi.value.ifBlank { "Yönetici" } }
        val data = hashMapOf(
            "branchId" to branchId,
            "actorUid" to currentUser.uid,
            "actorName" to actorName,
            "action" to action,
            "details" to details,
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        db.collection("audit_logs").add(data)
    }

    private val _auditLogs = MutableStateFlow<List<AuditLog>>(emptyList())
    val auditLogs: StateFlow<List<AuditLog>> = _auditLogs
    private var auditLogsListener: ListenerRegistration? = null

    fun startAuditLogsListener(branchId: String) {
        auditLogsListener?.remove()
        auditLogsListener = db.collection("audit_logs")
            .whereEqualTo("branchId", branchId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val list = snapshot.documents.map { doc ->
                    AuditLog(
                        id = doc.id,
                        branchId = doc.getString("branchId") ?: "",
                        actorUid = doc.getString("actorUid") ?: "",
                        actorName = doc.getString("actorName") ?: "",
                        action = doc.getString("action") ?: "",
                        details = doc.getString("details") ?: "",
                        createdAt = doc.getTimestamp("createdAt")
                    )
                }
                _auditLogs.value = list
            }
    }

    fun stopAuditLogsListener() {
        auditLogsListener?.remove()
        auditLogsListener = null
        _auditLogs.value = emptyList()
    }

    // Branch operations for Admin
    fun addBranch(ad: String, slug: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (_userRole.value != "admin") {
            onError("Yalnızca yönetici yeni şube ekleyebilir.")
            return
        }
        val cleanName = ad.trim()
        val cleanSlug = slug.trim().lowercase().replace(" ", "-")
        if (cleanName.isBlank() || cleanSlug.isBlank()) {
            onError("Şube adı ve slug boş bırakılamaz.")
            return
        }

        // Check if slug or name already exists to prevent duplicates
        db.collection("branches").whereEqualTo("slug", cleanSlug).get()
            .addOnSuccessListener { slugSnapshot ->
                if (!slugSnapshot.isEmpty) {
                    onError("Bu slug ($cleanSlug) ile bir şube zaten mevcut. Lütfen farklı bir slug belirleyin.")
                    return@addOnSuccessListener
                }

                db.collection("branches").whereEqualTo("ad", cleanName).get()
                    .addOnSuccessListener { nameSnapshot ->
                        if (!nameSnapshot.isEmpty) {
                            onError("Bu isimde ($cleanName) bir şube zaten mevcut. Lütfen farklı bir isim belirleyin.")
                            return@addOnSuccessListener
                        }

                        // Generate 32-character random alphanumeric apiKey
                        val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
                        val apiKey = (1..32).map { charset.random() }.joinToString("")

                        val branchData = hashMapOf(
                            "ad" to cleanName,
                            "slug" to cleanSlug,
                            "apiKey" to apiKey,
                            "locationCheckEnabled" to true,
                            "aktif" to true,
                            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )

                        db.collection("branches").add(branchData)
                            .addOnSuccessListener { docRef ->
                                logAuditEvent(docRef.id, "Şube Oluşturuldu", "Şube: $cleanName ($cleanSlug)")
                                onSuccess()
                            }
                            .addOnFailureListener { e -> onError(ErrorUtils.parseError(e, "Şube eklenemedi.")) }
                    }
                    .addOnFailureListener { e ->
                        onError(ErrorUtils.parseError(e, "Şube adı kontrolü yapılamadı."))
                    }
            }
            .addOnFailureListener { e ->
                onError(ErrorUtils.parseError(e, "Şube kontrolü yapılamadı."))
            }
    }

    fun updateBranchLocationCheck(
        branchId: String,
        enabled: Boolean,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val role = _userRole.value
        val isAuthorized = role == "admin" || (role == "mudur" && _userBranchId.value == branchId)
        if (!isAuthorized) {
            onError("Bu ayarı değiştirme yetkiniz yok.")
            return
        }
        if (branchId.isBlank()) {
            onError("Şube ID bulunamadı.")
            return
        }

        db.collection("branches").document(branchId)
            .update("locationCheckEnabled", enabled)
            .addOnSuccessListener {
                logAuditEvent(
                    branchId,
                    "GPS Kısıtı Güncellendi",
                    if (enabled) "GPS kısıtı açıldı" else "GPS kısıtı kapatıldı"
                )
                onSuccess()
            }
            .addOnFailureListener { e ->
                onError(ErrorUtils.parseError(e, "Konum doğrulama ayarı güncellenemedi."))
            }
    }

    fun updateBranch(branchId: String, ad: String, slug: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (_userRole.value != "admin") {
            onError("Bu ayarı değiştirme yetkiniz yok.")
            return
        }
        val cleanName = ad.trim()
        val cleanSlug = slug.trim().lowercase().replace(" ", "-")
        if (cleanName.isBlank() || cleanSlug.isBlank()) {
            onError("Şube adı ve slug boş bırakılamaz.")
            return
        }

        val updateData = mapOf(
            "ad" to cleanName,
            "slug" to cleanSlug
        )

        db.collection("branches").document(branchId).update(updateData)
            .addOnSuccessListener {
                logAuditEvent(branchId, "Şube Bilgisi Güncellendi", "Ad: $cleanName, Slug: $cleanSlug")
                onSuccess()
            }
            .addOnFailureListener { e -> onError(ErrorUtils.parseError(e, "Güncelleme başarısız.")) }
    }

    fun updateBranchActiveStatus(branchId: String, aktif: Boolean, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (_userRole.value != "admin") {
            onError("Bu ayarı değiştirme yetkiniz yok.")
            return
        }
        db.collection("branches").document(branchId).update("aktif", aktif)
            .addOnSuccessListener {
                logAuditEvent(
                    branchId,
                    "Şube Durumu Değiştirildi",
                    if (aktif) "Şube aktif edildi" else "Şube pasife alındı"
                )
                onSuccess()
            }
            .addOnFailureListener { e -> onError(ErrorUtils.parseError(e, "Şube durumu güncellenemedi.")) }
    }

    fun renewApiKey(branchId: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        if (_userRole.value != "admin") {
            onError("Bu ayarı değiştirme yetkiniz yok.")
            return
        }
        if (branchId.isBlank()) {
            onError("Şube ID bulunamadı.")
            return
        }
        val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val newKey = (1..32).map { charset.random() }.joinToString("")
        db.collection("branches").document(branchId).update("apiKey", newKey)
            .addOnSuccessListener {
                logAuditEvent(branchId, "API Anahtarı Yenilendi", "API anahtarı yönetici tarafından yenilendi.")
                onSuccess(newKey)
            }
            .addOnFailureListener { e -> onError(ErrorUtils.parseError(e, "API anahtarı yenilenemedi.")) }
    }

    fun updateBranchLocation(
        branchId: String,
        lat: Double,
        lng: Double,
        radius: Double = 150.0,
        address: String? = null,
        locationCheckEnabled: Boolean? = true,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val role = _userRole.value
        val isAuthorized = role == "admin" || (role == "mudur" && _userBranchId.value == branchId)
        if (!isAuthorized) {
            onError("Bu ayarı değiştirme yetkiniz yok.")
            return
        }
        if (branchId.isBlank()) {
            onError("Şube ID bulunamadı.")
            return
        }

        val updateData = mutableMapOf<String, Any>(
            "lat" to lat,
            "lng" to lng,
            "radius" to radius
        )
        if (locationCheckEnabled != null) {
            updateData["locationCheckEnabled"] = locationCheckEnabled
        }
        if (!address.isNullOrBlank()) {
            updateData["address"] = address
        }

        db.collection("branches").document(branchId).update(updateData)
            .addOnSuccessListener {
                logAuditEvent(
                    branchId,
                    "Şube Konumu Güncellendi",
                    "Konum: $lat, $lng, Yarıçap: $radius m" + if (!address.isNullOrBlank()) ", Adres: $address" else ""
                )
                onSuccess()
            }
            .addOnFailureListener { e -> onError(ErrorUtils.parseError(e, "Konum güncellenemedi.")) }
    }

    fun deleteBranch(branchId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (_userRole.value != "admin") {
            onError("Bu ayarı değiştirme yetkiniz yok.")
            return
        }
        db.collection("branches").document(branchId).delete()
            .addOnSuccessListener {
                logAuditEvent(branchId, "Şube Silindi", "Şube kalıcı olarak silindi.")
                onSuccess()
            }
            .addOnFailureListener { e -> onError(ErrorUtils.parseError(e, "Silme başarısız.")) }
    }

    // Listen to users of a specific branch (for Admin Detail view)
    private var branchUsersListener: ListenerRegistration? = null
    fun listenToBranchUsers(branchId: String) {
        branchUsersListener?.remove()
        branchUsersListener = db.collection("users")
            .whereEqualTo("branchId", branchId)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener
                val list = snapshots.documents.map { doc ->
                    User(
                        uid = doc.id,
                        kullaniciAdi = doc.getString("kullaniciAdi") ?: "",
                        rol = doc.getString("rol") ?: "",
                        branchId = doc.getString("branchId") ?: "",
                        ad = doc.getString("ad") ?: "",
                        fcmToken = doc.getString("fcmToken") ?: "",
                        aktif = doc.getBoolean("aktif") ?: true,
                        createdAt = doc.getTimestamp("createdAt")
                    )
                }
                // Filter out admin
                _branchUsers.value = list.filter { it.rol != "admin" }
            }
    }

    fun stopListeningToBranchUsers() {
        branchUsersListener?.remove()
        branchUsersListener = null
        _branchUsers.value = emptyList()
    }

    // Add user/waiter/manager via Server REST endpoint
    fun addUser(
        branchId: String,
        ad: String,
        kullaniciAdi: String,
        sifre: String,
        rol: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (ad.isBlank() || kullaniciAdi.isBlank() || sifre.isBlank()) {
            onError("Tüm alanlar zorunludur.")
            return
        }
        if (sifre.length < 6) {
            onError("Şifre en az 6 karakter olmalı")
            return
        }

        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            onError("Oturum bulunamadı")
            return
        }

        currentUser.getIdToken(false)
            .addOnSuccessListener { result ->
                val idToken = result.token
                if (idToken.isNullOrEmpty()) {
                    onError("Oturum bulunamadı")
                    return@addOnSuccessListener
                }

                val formattedUsername = kullaniciAdi.trim().lowercase()
                val finalBranchId = if (_userRole.value == "mudur") {
                    _userBranchId.value
                } else {
                    branchId
                }

                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val jsonBody = JSONObject().apply {
                            put("idToken", idToken)
                            put("kullaniciAdi", formattedUsername)
                            put("sifre", sifre)
                            put("rol", rol)
                            put("ad", ad.trim())
                            put("branchId", finalBranchId)
                        }.toString()

                        val cleanUrl = WORDPRESS_BASE_URL.trim().trimEnd('/')
                        val url = URL("$cleanUrl/wp-json/qrservis/v1/create-user")
                        val conn = (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                            setRequestProperty("Accept", "application/json")
                            doOutput = true
                            doInput = true
                            connectTimeout = 15000
                            readTimeout = 15000
                        }

                        conn.outputStream.use { os ->
                            os.write(jsonBody.toByteArray(Charsets.UTF_8))
                        }

                        val responseCode = conn.responseCode
                        val inputStream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                        val responseText = inputStream?.bufferedReader()?.use { it.readText() } ?: ""

                        val jsonResponse = try { JSONObject(responseText) } catch (_: Exception) { null }
                        val success = jsonResponse?.optBoolean("success", false) ?: false

                        withContext(Dispatchers.Main) {
                            if (success) {
                                onSuccess()
                            } else {
                                val serverMsg = jsonResponse?.optString("msg")?.takeIf { it.isNotBlank() }
                                    ?: jsonResponse?.optString("message")?.takeIf { it.isNotBlank() }
                                    ?: jsonResponse?.optString("error")?.takeIf { it.isNotBlank() }
                                    ?: if (responseText.isNotBlank() && responseText.length < 300) responseText else "Kullanıcı oluşturulamadı (HTTP $responseCode)"

                                Toast.makeText(context, serverMsg, Toast.LENGTH_LONG).show()
                                onError(serverMsg)
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            val errMsg = e.localizedMessage?.takeIf { it.isNotBlank() } ?: ErrorUtils.parseError(e, "Bağlantı hatası, tekrar deneyin")
                            Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                            onError(errMsg)
                        }
                    }
                }
            }
            .addOnFailureListener { err ->
                val errMsg = err.localizedMessage?.takeIf { it.isNotBlank() } ?: ErrorUtils.parseError(err, "Oturum bulunamadı")
                Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                onError(errMsg)
            }
    }

    fun deleteUser(uid: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        db.collection("users").document(uid).delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(ErrorUtils.parseError(e, "Kullanıcı silinemedi.")) }
    }

    fun updateUserActiveStatus(uid: String, aktif: Boolean, onSuccess: () -> Unit, onError: (String) -> Unit) {
        db.collection("users").document(uid).update("aktif", aktif)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(ErrorUtils.parseError(e, "Kullanıcı durumu güncellenemedi.")) }
    }

    fun updateUserDetails(uid: String, ad: String, rol: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (ad.isBlank()) {
            onError("Ad Soyad alanı boş bırakılamaz.")
            return
        }
        db.collection("users").document(uid).update(
            mapOf(
                "ad" to ad.trim(),
                "rol" to rol
            )
        ).addOnSuccessListener { onSuccess() }
         .addOnFailureListener { e -> onError(ErrorUtils.parseError(e, "Kullanıcı bilgileri güncellenemedi.")) }
    }

    fun updateUserPassword(
        user: User,
        newPassword: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (newPassword.length < 6) {
            onError("Şifre en az 6 karakter olmalıdır.")
            return
        }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            onError("Oturum bulunamadı.")
            return
        }

        currentUser.getIdToken(false)
            .addOnSuccessListener { tokenResult ->
                val idToken = tokenResult.token ?: ""
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val cleanUrl = WORDPRESS_BASE_URL.trim().trimEnd('/')
                        val url = URL("$cleanUrl/wp-json/qrservis/v1/update-password")
                        val jsonBody = JSONObject().apply {
                            put("idToken", idToken)
                            put("targetUid", user.uid)
                            put("kullaniciAdi", user.kullaniciAdi.trim().lowercase())
                            put("yeniSifre", newPassword)
                        }.toString()

                        val conn = (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                            setRequestProperty("Accept", "application/json")
                            doOutput = true
                            doInput = true
                            connectTimeout = 15000
                            readTimeout = 15000
                        }

                        conn.outputStream.use { os ->
                            os.write(jsonBody.toByteArray(Charsets.UTF_8))
                        }

                        val code = conn.responseCode
                        val inputStream = if (code in 200..299) conn.inputStream else conn.errorStream
                        val responseText = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                        val jsonResponse = try { JSONObject(responseText) } catch (_: Exception) { null }
                        val isSuccess = jsonResponse?.optBoolean("success", false) ?: (code in 200..299)

                        if (isSuccess || code in 200..299) {
                            // Log update in Firestore document metadata
                            db.collection("users").document(user.uid).update(
                                mapOf(
                                    "passwordUpdatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                )
                            )
                            withContext(Dispatchers.Main) {
                                onSuccess()
                            }
                        } else {
                            val serverMsg = jsonResponse?.optString("msg")?.takeIf { it.isNotBlank() }
                                ?: jsonResponse?.optString("message")?.takeIf { it.isNotBlank() }
                                ?: jsonResponse?.optString("error")?.takeIf { it.isNotBlank() }
                                ?: if (responseText.isNotBlank() && responseText.length < 300) responseText else "Şifre güncellenemedi (HTTP $code)"
                            withContext(Dispatchers.Main) {
                                onError(serverMsg)
                            }
                        }
                    } catch (e: Exception) {
                        // Record updated timestamp on user record in Firestore
                        db.collection("users").document(user.uid).update(
                            mapOf(
                                "passwordUpdatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            )
                        )
                        withContext(Dispatchers.Main) {
                            val errMsg = e.localizedMessage?.takeIf { it.isNotBlank() } ?: ErrorUtils.parseError(e, "Bağlantı hatası, tekrar deneyin")
                            onError(errMsg)
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                onError(ErrorUtils.parseError(e, "Oturum doğrulanamadı."))
            }
    }

    // Backwards-compatible alias for existing calls
    fun updateManagerPassword(
        user: User,
        newPassword: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        updateUserPassword(user, newPassword, onSuccess, onError)
    }

    fun sendPasswordResetForUser(email: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val targetEmail = if (email.contains("@")) email else "$email@qrservis.local"
        auth.sendPasswordResetEmail(targetEmail)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(ErrorUtils.parseError(e, "Sıfırlama bağlantısı gönderilemedi.")) }
    }

    fun deleteAnnouncement(announcementId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        db.collection("announcements").document(announcementId).delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(ErrorUtils.parseError(e, "Duyuru silinemedi.")) }
    }

    // Tables management (Manager)
    fun addTable(masaNo: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val branchId = _userBranchId.value
        if (masaNo.isBlank()) {
            onError("Masa numarası boş bırakılamaz.")
            return
        }

        val tableData = hashMapOf(
            "branchId" to branchId,
            "masaNo" to masaNo.trim(),
            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        db.collection("tables").add(tableData)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(ErrorUtils.parseError(e, "Masa eklenemedi.")) }
    }

    fun deleteTable(tableId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        db.collection("tables").document(tableId).delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(ErrorUtils.parseError(e, "Masa silinemedi.")) }
    }

    // Call approval operations (Manager & Waiter)
    fun approveCall(callId: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: ""
        val ad = _userAd.value

        val updateData = mapOf(
            "durum" to "onaylandi",
            "onaylayanUid" to uid,
            "onaylayanAd" to ad,
            "onaylandiAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        db.collection("calls").document(callId).update(updateData)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { e ->
                val errMsg = ErrorUtils.parseError(e, "Çağrı onaylanamadı.")
                Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                onError(errMsg)
            }
    }

    fun cancelCall(callId: String, reason: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        val ad = _userAd.value.ifEmpty { "Personel" }

        val updateData = mapOf(
            "durum" to "iptal",
            "iptalNedeni" to reason,
            "iptalEdenAd" to ad,
            "iptalAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        db.collection("calls").document(callId).update(updateData)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { e ->
                val errMsg = ErrorUtils.parseError(e, "Sipariş iptal edilemedi.")
                Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                onError(errMsg)
            }
    }

    // Shift management (Waiter)
    fun startShiftWithLocationCheck(
        context: Context,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            onError("Oturum bulunamadı")
            return
        }

        if (_activeShift.value != null) {
            onError("Zaten açık bir vardiyanız var.")
            return
        }

        val branchId = _userBranchId.value
        if (branchId.isEmpty()) {
            onError("Şube bilgisi bulunamadı.")
            return
        }

        db.collection("branches").document(branchId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    onError("Şube bulunamadı.")
                    return@addOnSuccessListener
                }

                val locationCheckEnabled = doc.getBoolean("locationCheckEnabled") ?: true
                if (!locationCheckEnabled) {
                    // Konum kontrolü kapalı: GPS sorgusu yapılmadan doğrudan vardiya başlatılır
                    startShift(onSuccess = onSuccess, onError = onError)
                    return@addOnSuccessListener
                }

                val branchLat = doc.getDouble("lat")
                val branchLng = doc.getDouble("lng")
                val radius = doc.getDouble("radius") ?: 150.0

                if (branchLat == null || branchLng == null) {
                    // Şube konumu ayarlanmamışsa: izin ver ama "Şube konumu ayarlanmamış" uyarısı göster
                    Toast.makeText(context, "Şube konumu ayarlanmamış", Toast.LENGTH_SHORT).show()
                    startShift(onSuccess = onSuccess, onError = onError)
                    return@addOnSuccessListener
                }

                com.example.utils.LocationUtils.getCurrentLocation(
                    context = context,
                    onLocationObtained = { devLoc ->
                        val distance = com.example.utils.LocationUtils.calculateDistanceMeters(
                            devLoc.latitude,
                            devLoc.longitude,
                            branchLat,
                            branchLng
                        )
                        if (distance <= radius) {
                            startShift(onSuccess = onSuccess, onError = onError)
                        } else {
                            onError("İşyerinde olmadığınız için vardiya başlatılamıyor. Restorana geldiğinizde tekrar deneyin.")
                        }
                    },
                    onError = { err ->
                        if (err == "PERMISSION_DENIED" || err == "LOCATION_DISABLED") {
                            onError("Vardiya başlatmak için konum izni gerekli")
                        } else {
                            onError(err)
                        }
                    }
                )
            }
            .addOnFailureListener { e ->
                onError(ErrorUtils.parseError(e, "Şube verisi okunamadı."))
            }
    }

    fun startShift(onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        val currentUser = auth.currentUser
        val uid = currentUser?.uid ?: run {
            onError("Oturum bulunamadı")
            return
        }

        if (_activeShift.value != null) {
            onError("Zaten açık bir vardiyanız var.")
            return
        }

        val branchId = _userBranchId.value
        val garsonAd = _userAd.value.ifEmpty { _userKullaniciAdi.value }

        val now = com.google.firebase.firestore.FieldValue.serverTimestamp()
        val shiftData = hashMapOf(
            "branchId" to branchId,
            "garsonUid" to uid,
            "garsonAd" to garsonAd,
            "startAt" to now,
            "baslangic" to now,
            "endAt" to null,
            "bitis" to null,
            "durum" to "acik",
            "molada" to false,
            "molaBaslangic" to null
        )

        db.collection("shifts").add(shiftData)
            .addOnSuccessListener {
                startForegroundServiceIfNeeded()
                onSuccess()
            }
            .addOnFailureListener { e ->
                onError(ErrorUtils.parseError(e, "Vardiya başlatılamadı."))
            }
    }

    fun startBreak(onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        val active = _activeShift.value ?: run {
            onError("Açık vardiya bulunamadı")
            return
        }
        if (active.id.isEmpty()) {
            onError("Vardiya id geçersiz")
            return
        }

        val shiftRef = db.collection("shifts").document(active.id)
        val now = com.google.firebase.firestore.FieldValue.serverTimestamp()

        shiftRef.update(
            "molada", true,
            "molaBaslangic", now
        ).addOnSuccessListener {
            val molaData = hashMapOf(
                "startAt" to now,
                "baslangic" to now,
                "endAt" to null,
                "bitis" to null,
                "sure" to null,
                "süre" to null
            )
            shiftRef.collection("molalar").add(molaData)
                .addOnSuccessListener {
                    stopForegroundService()
                    onSuccess()
                }
                .addOnFailureListener {
                    stopForegroundService()
                    onSuccess()
                }
        }.addOnFailureListener { e ->
            onError(ErrorUtils.parseError(e, "Molaya çıkılamadı."))
        }
    }

    fun endBreak(onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        val active = _activeShift.value ?: run {
            onError("Açık vardiya bulunamadı")
            return
        }
        if (active.id.isEmpty()) {
            onError("Vardiya id geçersiz")
            return
        }

        val shiftRef = db.collection("shifts").document(active.id)

        shiftRef.update(
            "molada", false,
            "molaBaslangic", null
        ).addOnSuccessListener {
            val now = com.google.firebase.firestore.FieldValue.serverTimestamp()
            shiftRef.collection("molalar")
                .get()
                .addOnSuccessListener { snapshot ->
                    val batch = db.batch()
                    var hasUnfinished = false
                    for (doc in snapshot.documents) {
                        val endTs = extractTimestamp(doc, "endAt", "bitis")
                        if (endTs == null) {
                            hasUnfinished = true
                            val startTs = extractTimestamp(doc, "startAt", "baslangic")
                            val diffMs = if (startTs != null) {
                                (System.currentTimeMillis() - startTs.toDate().time).coerceAtLeast(0L)
                            } else null
                            val durationStr = if (diffMs != null) ShiftDateTimeUtils.formatDurationDetailed(diffMs) else null

                            val updates = mutableMapOf<String, Any?>(
                                "bitis" to now,
                                "endAt" to now
                            )
                            if (durationStr != null) {
                                updates["sure"] = durationStr
                                updates["süre"] = durationStr
                            }
                            batch.update(doc.reference, updates)
                        }
                    }
                    if (hasUnfinished) {
                        batch.commit()
                    }
                    if (_userRole.value == "garson") {
                        startForegroundService()
                    }
                    onSuccess()
                }
                .addOnFailureListener {
                    if (_userRole.value == "garson") {
                        startForegroundService()
                    }
                    onSuccess()
                }
        }.addOnFailureListener { e ->
            onError(ErrorUtils.parseError(e, "Moladan dönülemedi."))
        }
    }

    fun endShift(onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        val currentUser = auth.currentUser
        val uid = currentUser?.uid ?: run {
            onError("Oturum bulunamadı")
            return
        }

        db.collection("shifts")
            .whereEqualTo("garsonUid", uid)
            .whereEqualTo("durum", "acik")
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    _activeShift.value = null
                    stopForegroundService()
                    onSuccess()
                } else {
                    val batch = db.batch()
                    var processedCount = 0
                    val totalDocs = snapshot.documents.size
                    val now = com.google.firebase.firestore.FieldValue.serverTimestamp()

                    fun checkAndCommit() {
                        processedCount++
                        if (processedCount >= totalDocs) {
                            batch.commit()
                                .addOnSuccessListener {
                                    stopForegroundService()
                                    _activeShift.value = null
                                    onSuccess()
                                }
                                .addOnFailureListener { e ->
                                    onError(ErrorUtils.parseError(e, "Vardiya bitirilemedi."))
                                }
                        }
                    }

                    for (doc in snapshot.documents) {
                        val isMolada = doc.getBoolean("molada") ?: false
                        if (isMolada) {
                            doc.reference.collection("molalar")
                                .get()
                                .addOnSuccessListener { breakSnapshot ->
                                    for (breakDoc in breakSnapshot.documents) {
                                        val endTs = extractTimestamp(breakDoc, "endAt", "bitis")
                                        if (endTs == null) {
                                            val startTs = extractTimestamp(breakDoc, "startAt", "baslangic")
                                            val diffMs = if (startTs != null) {
                                                (System.currentTimeMillis() - startTs.toDate().time).coerceAtLeast(0L)
                                            } else null
                                            val durationStr = if (diffMs != null) ShiftDateTimeUtils.formatDurationDetailed(diffMs) else null

                                            val updates = mutableMapOf<String, Any?>(
                                                "bitis" to now,
                                                "endAt" to now
                                            )
                                            if (durationStr != null) {
                                                updates["sure"] = durationStr
                                                updates["süre"] = durationStr
                                            }
                                            batch.update(breakDoc.reference, updates)
                                        }
                                    }
                                    batch.update(
                                        doc.reference,
                                        "bitis", now,
                                        "endAt", now,
                                        "durum", "kapali",
                                        "molada", false,
                                        "molaBaslangic", null
                                    )
                                    checkAndCommit()
                                }
                                .addOnFailureListener {
                                    batch.update(
                                        doc.reference,
                                        "bitis", now,
                                        "endAt", now,
                                        "durum", "kapali",
                                        "molada", false,
                                        "molaBaslangic", null
                                    )
                                    checkAndCommit()
                                }
                        } else {
                            batch.update(
                                doc.reference,
                                "bitis", now,
                                "endAt", now,
                                "durum", "kapali",
                                "molada", false,
                                "molaBaslangic", null
                            )
                            checkAndCommit()
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                onError(ErrorUtils.parseError(e, "Vardiya bitirilemedi."))
            }
    }

    fun startBranchActiveShiftsListener(branchId: String) {
        if (branchId.isEmpty()) return
        branchActiveShiftsListener?.remove()
        branchActiveShiftsListener = db.collection("shifts")
            .whereEqualTo("branchId", branchId)
            .whereEqualTo("durum", "acik")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val map = mutableMapOf<String, Shift>()
                for (doc in snapshot.documents) {
                    val garsonUid = doc.getString("garsonUid") ?: ""
                    if (garsonUid.isNotEmpty()) {
                        map[garsonUid] = Shift(
                            id = doc.id,
                            branchId = doc.getString("branchId") ?: "",
                            garsonUid = garsonUid,
                            garsonAd = doc.getString("garsonAd") ?: "",
                            baslangic = extractTimestamp(doc, "baslangic", "startTimestamp", "startTime", "startAt", "createdAt"),
                            bitis = extractTimestamp(doc, "bitis", "endTimestamp", "endTime", "endAt"),
                            durum = doc.getString("durum") ?: "",
                            molada = doc.getBoolean("molada") ?: false,
                            molaBaslangic = extractTimestamp(doc, "molaBaslangic", "breakStart")
                        )
                    }
                }
                _branchActiveShifts.value = map
            }
    }

    fun stopBranchActiveShiftsListener() {
        branchActiveShiftsListener?.remove()
        branchActiveShiftsListener = null
        _branchActiveShifts.value = emptyMap()
    }

    fun getWaitersForBranch(branchId: String, onWaitersLoaded: (List<User>) -> Unit): ListenerRegistration {
        return db.collection("users")
            .whereEqualTo("branchId", branchId)
            .whereEqualTo("rol", "garson")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val list = snapshot.documents.map { doc ->
                    User(
                        uid = doc.id,
                        kullaniciAdi = doc.getString("kullaniciAdi") ?: "",
                        rol = doc.getString("rol") ?: "",
                        branchId = doc.getString("branchId") ?: "",
                        ad = doc.getString("ad") ?: "",
                        fcmToken = doc.getString("fcmToken") ?: "",
                        aktif = doc.getBoolean("aktif") ?: true,
                        createdAt = doc.getTimestamp("createdAt")
                    )
                }
                onWaitersLoaded(list)
            }
    }

    fun listenGarsonShiftsWithBreaks(
        garsonUid: String,
        onShiftsUpdated: (List<ShiftWithBreaks>) -> Unit
    ): ListenerRegistration {
        return db.collection("shifts")
            .whereEqualTo("garsonUid", garsonUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val shifts = snapshot.documents.map { doc ->
                    Shift(
                        id = doc.id,
                        branchId = doc.getString("branchId") ?: "",
                        garsonUid = doc.getString("garsonUid") ?: "",
                        garsonAd = doc.getString("garsonAd") ?: "",
                        baslangic = extractTimestamp(doc, "baslangic", "startTimestamp", "startTime", "startAt", "createdAt"),
                        bitis = extractTimestamp(doc, "bitis", "endTimestamp", "endTime", "endAt"),
                        durum = doc.getString("durum") ?: "",
                        molada = doc.getBoolean("molada") ?: false,
                        molaBaslangic = extractTimestamp(doc, "molaBaslangic", "breakStart")
                    )
                }.sortedByDescending { it.baslangic?.seconds ?: 0L }

                if (shifts.isEmpty()) {
                    onShiftsUpdated(emptyList())
                    return@addSnapshotListener
                }

                val shiftWithBreaksList = arrayOfNulls<ShiftWithBreaks>(shifts.size)
                var count = 0

                shifts.forEachIndexed { index, shift ->
                    val shiftDocRef = db.collection("shifts").document(shift.id)
                    shiftDocRef.collection("molalar")
                        .get()
                        .addOnSuccessListener { molaSnapshot ->
                            if (!molaSnapshot.isEmpty) {
                                val breaks = molaSnapshot.documents.map { mDoc ->
                                    ShiftBreak(
                                        id = mDoc.id,
                                        baslangic = extractTimestamp(mDoc, "baslangic", "startTimestamp", "startTime", "startAt", "createdAt"),
                                        bitis = extractTimestamp(mDoc, "bitis", "endTimestamp", "endTime", "endAt")
                                    )
                                }.sortedBy { it.baslangic?.seconds ?: 0L }

                                shiftWithBreaksList[index] = ShiftWithBreaks(shift, breaks)
                                count++
                                if (count == shifts.size) {
                                    onShiftsUpdated(shiftWithBreaksList.filterNotNull())
                                }
                            } else {
                                // Fallback to "breaks" collection
                                shiftDocRef.collection("breaks")
                                    .get()
                                    .addOnSuccessListener { breakSnapshot ->
                                        val breaks = breakSnapshot.documents.map { mDoc ->
                                            ShiftBreak(
                                                id = mDoc.id,
                                                baslangic = extractTimestamp(mDoc, "baslangic", "startTimestamp", "startTime", "startAt", "createdAt"),
                                                bitis = extractTimestamp(mDoc, "bitis", "endTimestamp", "endTime", "endAt")
                                            )
                                        }.sortedBy { it.baslangic?.seconds ?: 0L }

                                        shiftWithBreaksList[index] = ShiftWithBreaks(shift, breaks)
                                        count++
                                        if (count == shifts.size) {
                                            onShiftsUpdated(shiftWithBreaksList.filterNotNull())
                                        }
                                    }
                                    .addOnFailureListener {
                                        shiftWithBreaksList[index] = ShiftWithBreaks(shift, emptyList())
                                        count++
                                        if (count == shifts.size) {
                                            onShiftsUpdated(shiftWithBreaksList.filterNotNull())
                                        }
                                    }
                            }
                        }
                        .addOnFailureListener {
                            shiftWithBreaksList[index] = ShiftWithBreaks(shift, emptyList())
                            count++
                            if (count == shifts.size) {
                                onShiftsUpdated(shiftWithBreaksList.filterNotNull())
                            }
                        }
                }
            }
    }

    fun fetchAnalytics(
        baslangic: String,
        bitis: String,
        siteUrl: String = WORDPRESS_BASE_URL,
        onResult: (AnalyticsResponse?, String?) -> Unit
    ) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            onResult(null, "Oturum bulunamadı")
            return
        }

        currentUser.getIdToken(true)
            .addOnSuccessListener { tokenResult ->
                val idToken = tokenResult.token
                if (idToken.isNullOrEmpty()) {
                    onResult(null, "ID token alınamadı")
                    return@addOnSuccessListener
                }

                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val cleanUrl = (if (siteUrl.isBlank()) WORDPRESS_BASE_URL else siteUrl).trim().trimEnd('/')
                        val fullUrl = "$cleanUrl/wp-json/qrservis/v1/analytics"
                        val jsonBody = JSONObject().apply {
                            put("idToken", idToken)
                            put("baslangic", baslangic)
                            put("bitis", bitis)
                        }.toString()

                        val url = URL(fullUrl)
                        val conn = (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                            setRequestProperty("Accept", "application/json")
                            setRequestProperty("Cache-Control", "no-cache")
                            setRequestProperty("Pragma", "no-cache")
                            useCaches = false
                            doOutput = true
                            doInput = true
                            connectTimeout = 15000
                            readTimeout = 15000
                        }

                        conn.outputStream.use { os ->
                            os.write(jsonBody.toByteArray(Charsets.UTF_8))
                        }

                        val responseCode = conn.responseCode
                        val inputStream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                        val responseText = inputStream?.bufferedReader()?.use { it.readText() } ?: ""

                        if (responseCode !in 200..299) {
                            withContext(Dispatchers.Main) {
                                onResult(null, "Sunucu yanıt vermedi, lütfen tekrar deneyin.")
                            }
                            return@launch
                        }

                        val responseObj = JSONObject(responseText)
                        val parsedData = parseAnalyticsJson(responseObj)
                        withContext(Dispatchers.Main) {
                            onResult(parsedData, null)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            onResult(null, ErrorUtils.parseError(e, "Analiz verileri alınamadı."))
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                onResult(null, ErrorUtils.parseError(e, "Oturum doğrulanamadı."))
            }
    }

    private fun parseAnalyticsJson(root: JSONObject): AnalyticsResponse {
        val json = root.optJSONObject("data") ?: root.optJSONObject("result") ?: root

        val toplamGoruntulenme = json.optInt("toplamGoruntulenme", json.optInt("toplam_goruntulenme", json.optInt("goruntulenme", 0)))
        val tekilZiyaretci = json.optInt("tekilZiyaretci", json.optInt("tekil_ziyaretci", json.optInt("tekil", 0)))
        val toplamTiklama = json.optInt("toplamTiklama", json.optInt("toplam_tiklama", json.optInt("tiklama", 0)))

        fun parseProductList(key1: String, key2: String): List<AnalyticsProductItem> {
            val arr = json.optJSONArray(key1) ?: json.optJSONArray(key2) ?: return emptyList()
            val list = mutableListOf<AnalyticsProductItem>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val urunAd = item.optString("item_name", item.optString("urunAd", item.optString("urun_ad", item.optString("ad", item.optString("title", item.optString("name", item.optString("product_name", "")))))))
                val kategori = item.optString("category_name", item.optString("kategori", item.optString("category", "")))
                val tiklamaAdedi = item.optInt("adet", item.optInt("tiklamaAdedi", item.optInt("tiklama_adedi", item.optInt("tiklama", item.optInt("count", item.optInt("clicks", 0))))))
                list.add(AnalyticsProductItem(urunAd = urunAd, kategori = kategori, tiklamaAdedi = tiklamaAdedi))
            }
            return list
        }

        fun parseTableList(key1: String, key2: String): List<AnalyticsTableItem> {
            val arr = json.optJSONArray(key1) ?: json.optJSONArray(key2) ?: return emptyList()
            val list = mutableListOf<AnalyticsTableItem>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val masaAd = item.optString("table_slug", item.optString("tableSlug", item.optString("masaAd", item.optString("masa_ad", item.optString("masa", item.optString("title", item.optString("name", "")))))))
                val count = item.optInt("adet", item.optInt("goruntulenmeAdedi", item.optInt("goruntulenme_adedi", item.optInt("count", item.optInt("tiklama", item.optInt("views", 0))))))
                list.add(AnalyticsTableItem(masaAd = masaAd, goruntulenmeAdedi = count))
            }
            return list
        }

        val enCok = parseProductList("enCok", "en_cok")
        val enAz = parseProductList("enAz", "en_az")
        val masalar = parseTableList("masalar", "tables")

        return AnalyticsResponse(
            toplamGoruntulenme = toplamGoruntulenme,
            tekilZiyaretci = tekilZiyaretci,
            toplamTiklama = toplamTiklama,
            enCok = enCok,
            enAz = enAz,
            masalar = masalar
        )
    }

    override fun onCleared() {
        clearAllListeners()
        shiftListenerRegistration?.remove()
        branchUsersListener?.remove()
        branchActiveShiftsListener?.remove()
        super.onCleared()
    }
}
