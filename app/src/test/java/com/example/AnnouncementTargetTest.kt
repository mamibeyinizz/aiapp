package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementTargetTest {

    data class AnnouncementTargetPayload(
        val role: String,
        val targetScope: String,
        val branchId: String,
        val selectedBranchId: String,
        val selectedBranchName: String,
        val selectedPersonnelUid: String,
        val selectedPersonnelName: String,
        val managerBranchId: String = ""
    ) {
        val isAdmin = role == "admin"
        val isManager = role == "mudur"
        val isWaiter = role == "garson"

        fun canSend(): Boolean {
            if (isWaiter) return false
            if (!isAdmin && !isManager) return false
            if (isManager && (targetScope == "all_staff" || branchId == "all")) return false
            if (isManager && selectedBranchId.isNotEmpty() && selectedBranchId != managerBranchId) return false
            return true
        }

        fun getSummary(): String {
            return when {
                isAdmin && targetScope == "all_staff" -> "Tüm Personel"
                isAdmin && targetScope == "branch" -> "Şube: $selectedBranchName"
                !isAdmin && targetScope == "all" -> "Şube: $selectedBranchName"
                targetScope == "user" -> "Personel: $selectedPersonnelName"
                else -> "Tüm Personel"
            }
        }

        fun getFinalBranchId(): String {
            return when {
                isAdmin && targetScope == "all_staff" -> "all"
                isAdmin && targetScope == "branch" -> selectedBranchId
                isAdmin && targetScope == "user" -> selectedBranchId.ifEmpty { "all" }
                else -> managerBranchId
            }
        }
    }

    @Test
    fun testAdminToAllStaff() {
        val payload = AnnouncementTargetPayload(
            role = "admin",
            targetScope = "all_staff",
            branchId = "all",
            selectedBranchId = "all",
            selectedBranchName = "Tüm Şubeler",
            selectedPersonnelUid = "",
            selectedPersonnelName = ""
        )
        assertTrue(payload.canSend())
        assertEquals("Tüm Personel", payload.getSummary())
        assertEquals("all", payload.getFinalBranchId())
    }

    @Test
    fun testAdminToSingleBranch() {
        val payload = AnnouncementTargetPayload(
            role = "admin",
            targetScope = "branch",
            branchId = "b_kadikoy",
            selectedBranchId = "b_kadikoy",
            selectedBranchName = "Kadıköy Şubesi",
            selectedPersonnelUid = "",
            selectedPersonnelName = ""
        )
        assertTrue(payload.canSend())
        assertEquals("Şube: Kadıköy Şubesi", payload.getSummary())
        assertEquals("b_kadikoy", payload.getFinalBranchId())
    }

    @Test
    fun testAdminToSinglePersonnel() {
        val payload = AnnouncementTargetPayload(
            role = "admin",
            targetScope = "user",
            branchId = "b_kadikoy",
            selectedBranchId = "b_kadikoy",
            selectedBranchName = "Kadıköy Şubesi",
            selectedPersonnelUid = "uid_ahmet",
            selectedPersonnelName = "Ahmet Yılmaz"
        )
        assertTrue(payload.canSend())
        assertEquals("Personel: Ahmet Yılmaz", payload.getSummary())
        assertEquals("b_kadikoy", payload.getFinalBranchId())
    }

    @Test
    fun testManagerToOwnBranch() {
        val payload = AnnouncementTargetPayload(
            role = "mudur",
            targetScope = "all",
            branchId = "b_kadikoy",
            selectedBranchId = "b_kadikoy",
            selectedBranchName = "Kadıköy Şubesi",
            selectedPersonnelUid = "",
            selectedPersonnelName = "",
            managerBranchId = "b_kadikoy"
        )
        assertTrue(payload.canSend())
        assertEquals("Şube: Kadıköy Şubesi", payload.getSummary())
        assertEquals("b_kadikoy", payload.getFinalBranchId())
    }

    @Test
    fun testManagerForbiddenFromAllStaff() {
        val payload = AnnouncementTargetPayload(
            role = "mudur",
            targetScope = "all_staff",
            branchId = "all",
            selectedBranchId = "all",
            selectedBranchName = "Tüm Şubeler",
            selectedPersonnelUid = "",
            selectedPersonnelName = "",
            managerBranchId = "b_kadikoy"
        )
        assertFalse("Müdür tüm personele global duyuru gönderemez", payload.canSend())
    }

    @Test
    fun testManagerForbiddenFromOtherBranch() {
        val payload = AnnouncementTargetPayload(
            role = "mudur",
            targetScope = "branch",
            branchId = "b_besiktas",
            selectedBranchId = "b_besiktas",
            selectedBranchName = "Beşiktaş Şubesi",
            selectedPersonnelUid = "",
            selectedPersonnelName = "",
            managerBranchId = "b_kadikoy"
        )
        assertFalse("Müdür başka şubeye duyuru gönderemez", payload.canSend())
    }

    @Test
    fun testWaiterCannotSendAnnouncement() {
        val payload = AnnouncementTargetPayload(
            role = "garson",
            targetScope = "all",
            branchId = "b_kadikoy",
            selectedBranchId = "b_kadikoy",
            selectedBranchName = "Kadıköy Şubesi",
            selectedPersonnelUid = "",
            selectedPersonnelName = "",
            managerBranchId = "b_kadikoy"
        )
        assertFalse("Garson kesinlikle duyuru gönderemez", payload.canSend())
    }

    @Test
    fun testAdminToWaiterInDifferentBranch() {
        val payload = AnnouncementTargetPayload(
            role = "admin",
            targetScope = "user",
            branchId = "b_besiktas",
            selectedBranchId = "b_besiktas",
            selectedBranchName = "Beşiktaş Şubesi",
            selectedPersonnelUid = "uid_mehmet",
            selectedPersonnelName = "Mehmet Demir"
        )
        assertTrue(payload.canSend())
        assertEquals("Personel: Mehmet Demir", payload.getSummary())
        assertEquals("b_besiktas", payload.getFinalBranchId())
    }

    @Test
    fun testFcmStatusDetermination() {
        // Test: FCM token missing or play services off yields descriptive notice
        fun determineStatusMessage(isPlayServicesAvailable: Boolean, hasPushToken: Boolean, targetType: String): String {
            return if (!isPlayServicesAvailable || (!hasPushToken && targetType == "user")) {
                "Duyuru kaydedildi ancak bildirim gönderilemedi."
            } else {
                "Duyuru gönderildi"
            }
        }

        // When FCM/Play Services is available and user has token:
        assertEquals("Duyuru gönderildi", determineStatusMessage(true, true, "user"))
        assertEquals("Duyuru gönderildi", determineStatusMessage(true, false, "all_staff"))

        // When user has no push token:
        assertEquals("Duyuru kaydedildi ancak bildirim gönderilemedi.", determineStatusMessage(true, false, "user"))

        // When Google Play Services is off:
        assertEquals("Duyuru kaydedildi ancak bildirim gönderilemedi.", determineStatusMessage(false, true, "branch"))
    }

    @Test
    fun testWaiterVisibilityAndReadUnreadFiltering() {
        data class TestAnnouncement(
            val id: String,
            val branchId: String,
            val targetType: String,
            val targetUserUid: String,
            val readUids: List<String>
        )

        val waiterUid = "waiter_123"
        val waiterBranch = "branch_kadikoy"

        val announcements = listOf(
            TestAnnouncement("1", "all", "all_staff", "", listOf()), // Global broadcast -> visible, unread
            TestAnnouncement("2", "branch_kadikoy", "branch", "", listOf(waiterUid)), // Own branch broadcast -> visible, read
            TestAnnouncement("3", "branch_besiktas", "branch", "", listOf()), // Other branch broadcast -> NOT visible
            TestAnnouncement("4", "branch_besiktas", "user", waiterUid, listOf()), // Direct user announcement -> visible, unread
            TestAnnouncement("5", "branch_kadikoy", "user", "other_waiter", listOf()) // Other user direct announcement -> NOT visible
        )

        // Filter for this waiter
        val visibleAnnouncements = announcements.filter { a ->
            a.targetType == "all_staff" ||
            a.targetType == "all" ||
            (a.targetType == "branch" && (a.branchId == waiterBranch || a.branchId == "all")) ||
            a.targetUserUid == waiterUid
        }

        assertEquals(3, visibleAnnouncements.size)
        assertTrue(visibleAnnouncements.any { it.id == "1" })
        assertTrue(visibleAnnouncements.any { it.id == "2" })
        assertTrue(visibleAnnouncements.any { it.id == "4" })
        assertFalse(visibleAnnouncements.any { it.id == "3" })
        assertFalse(visibleAnnouncements.any { it.id == "5" })

        // Check unread count
        val unreadList = visibleAnnouncements.filter { !it.readUids.contains(waiterUid) }
        assertEquals(2, unreadList.size)
        assertTrue(unreadList.any { it.id == "1" })
        assertTrue(unreadList.any { it.id == "4" })
    }

    @Test
    fun testDualSourceTabsManagementAndManagerSeparation() {
        val waiterUid = "garson_1"
        val waiterBranch = "branch_kadikoy"

        // 1. Admin -> Garson announcement
        val adminToWaiter = com.example.data.Announcement(
            id = "a1",
            branchId = "branch_kadikoy",
            senderRole = "admin",
            targetType = "user",
            targetUserUid = waiterUid,
            readUids = emptyList()
        )
        assertTrue("Admin'in garsona duyurusu Yönetim sekmesine aittir", adminToWaiter.isManagement())
        assertFalse(adminToWaiter.isManager())
        assertEquals("Yönetim Duyurusu", adminToWaiter.getSourceCategoryName())

        // 2. Admin -> Tüm personel announcement
        val adminToAllStaff = com.example.data.Announcement(
            id = "a2",
            branchId = "all",
            senderRole = "admin",
            targetType = "all_staff",
            readUids = emptyList()
        )
        assertTrue("Admin'in tüm personele duyurusu Yönetim sekmesine aittir", adminToAllStaff.isManagement())
        assertEquals("Yönetim Duyurusu", adminToAllStaff.getSourceCategoryName())

        // 3. Müdür -> Garson announcement
        val managerToWaiter = com.example.data.Announcement(
            id = "a3",
            branchId = "branch_kadikoy",
            senderRole = "mudur",
            targetType = "user",
            targetUserUid = waiterUid,
            readUids = emptyList()
        )
        assertTrue("Müdür'ün garsona duyurusu Müdür sekmesine aittir", managerToWaiter.isManager())
        assertFalse(managerToWaiter.isManagement())
        assertEquals("Müdür Duyurusu", managerToWaiter.getSourceCategoryName())

        // 4. Müdür -> Kendi şubesine announcement
        val managerToBranch = com.example.data.Announcement(
            id = "a4",
            branchId = "branch_kadikoy",
            senderRole = "mudur",
            targetType = "all",
            readUids = listOf(waiterUid) // already read
        )
        assertTrue("Müdür'ün şubeye duyurusu Müdür sekmesine aittir", managerToBranch.isManager())

        // Combine into list
        val announcements = listOf(adminToWaiter, adminToAllStaff, managerToWaiter, managerToBranch)

        // Count unread per tab
        val unreadManagement = announcements.count { it.isManagement() && !it.readUids.contains(waiterUid) }
        val unreadManager = announcements.count { it.isManager() && !it.readUids.contains(waiterUid) }

        assertEquals(2, unreadManagement)
        assertEquals(1, unreadManager)

        // When a3 is marked read, manager unread count becomes 0, management remains 2
        val updatedAnnouncements = announcements.map {
            if (it.id == "a3") it.copy(readUids = listOf(waiterUid)) else it
        }

        val updatedUnreadManagement = updatedAnnouncements.count { it.isManagement() && !it.readUids.contains(waiterUid) }
        val updatedUnreadManager = updatedAnnouncements.count { it.isManager() && !it.readUids.contains(waiterUid) }

        assertEquals(2, updatedUnreadManagement)
        assertEquals(0, updatedUnreadManager)
    }

    @Test
    fun testEmptyStateMessages() {
        fun getEmptyState(tab: Int, filterType: Int): String {
            return when {
                filterType == 1 && tab == 0 -> "Okunmamış yönetim duyurunuz yok"
                filterType == 1 && tab == 1 -> "Okunmamış müdür duyurunuz yok"
                tab == 0 -> "Henüz yönetim duyurusu yok"
                else -> "Henüz müdür duyurusu yok"
            }
        }

        assertEquals("Henüz yönetim duyurusu yok", getEmptyState(0, 0))
        assertEquals("Henüz müdür duyurusu yok", getEmptyState(1, 0))
        assertEquals("Okunmamış yönetim duyurunuz yok", getEmptyState(0, 1))
        assertEquals("Okunmamış müdür duyurunuz yok", getEmptyState(1, 1))
    }

    @Test
    fun testLegacyAnnouncementsSourceClassification() {
        // Legacy announcement with empty senderRole but targetType = all_staff
        val legacy1 = com.example.data.Announcement(
            id = "leg1",
            branchId = "all",
            senderRole = "",
            targetType = "all_staff"
        )
        assertTrue("Eski all_staff duyurusu Yönetim'e ait olmalıdır", legacy1.isManagement())

        // Legacy announcement with senderRole = mudur
        val legacy2 = com.example.data.Announcement(
            id = "leg2",
            branchId = "branch_kadikoy",
            senderRole = "mudur",
            targetType = "all"
        )
        assertTrue("Eski müdür duyurusu Müdür'e ait olmalıdır", legacy2.isManager())
    }

    @Test
    fun testManagerAnnouncementVisibilityFromAdmin() {
        val managerUid = "mgr_kadikoy"
        val managerBranch = "branch_kadikoy"

        // 1. Admin -> Tüm personel (all_staff)
        val adminToAll = com.example.data.Announcement(
            id = "a_all",
            branchId = "all",
            senderRole = "admin",
            targetType = "all_staff"
        )
        // 2. Admin -> Müdürün şubesi (branch_kadikoy)
        val adminToManagerBranch = com.example.data.Announcement(
            id = "a_branch_kadikoy",
            branchId = "branch_kadikoy",
            targetBranchId = "branch_kadikoy",
            senderRole = "admin",
            targetType = "branch"
        )
        // 3. Admin -> Başka şube (branch_besiktas)
        val adminToOtherBranch = com.example.data.Announcement(
            id = "a_branch_besiktas",
            branchId = "branch_besiktas",
            targetBranchId = "branch_besiktas",
            senderRole = "admin",
            targetType = "branch"
        )
        // 4. Admin -> Doğrudan müdüre (user)
        val adminToDirectManager = com.example.data.Announcement(
            id = "a_direct_mgr",
            branchId = "branch_kadikoy",
            senderRole = "admin",
            targetType = "user",
            targetUserUid = managerUid
        )

        val allAnnouncements = listOf(adminToAll, adminToManagerBranch, adminToOtherBranch, adminToDirectManager)

        // Manager's visibility filter for Management announcements
        val visibleForManager = allAnnouncements.filter { a ->
            val isForManager = a.targetType == "all_staff" ||
                    a.targetType == "all" ||
                    (a.targetType == "branch" && (a.branchId == managerBranch || a.targetBranchId == managerBranch || a.branchId == "all")) ||
                    a.targetUserUid == managerUid ||
                    a.targetUserId == managerUid
            isForManager && a.isManagement()
        }

        // Test 1: Admin -> tüm personele -> müdür görmeli
        assertTrue("Müdür tüm personele duyuruyu görmeli", visibleForManager.any { it.id == "a_all" })

        // Test 2: Admin -> müdürün şubesine -> müdür görmeli
        assertTrue("Müdür kendi şubesine duyuruyu görmeli", visibleForManager.any { it.id == "a_branch_kadikoy" })

        // Test 3: Admin -> başka şubeye -> müdür görmemeli
        assertFalse("Müdür başka şubenin duyurusunu görmemeli", visibleForManager.any { it.id == "a_branch_besiktas" })

        // Test 4: Admin -> doğrudan müdüre -> müdür görmeli
        assertTrue("Müdür doğrudan kendisine gelen duyuruyu görmeli", visibleForManager.any { it.id == "a_direct_mgr" })

        assertEquals("Müdürün gördüğü yönetim duyurusu sayısı 3 olmalıdır", 3, visibleForManager.size)
    }

    @Test
    fun testManagerAnnouncementSendPermissionsAndBoundaries() {
        data class SendRequest(
            val userRole: String,
            val userBranchId: String,
            val targetType: String,
            val targetBranchId: String,
            val targetUserUid: String,
            val targetUserBranchId: String = ""
        ) {
            fun validate(): Result<String> {
                if (userRole == "garson") {
                    return Result.failure(Exception("Garsonların duyuru gönderme yetkisi bulunmamaktadır."))
                }
                if (userRole != "admin" && userRole != "mudur") {
                    return Result.failure(Exception("Yetkisiz kullanıcı."))
                }
                if (userRole == "mudur") {
                    if (userBranchId.isEmpty()) {
                        return Result.failure(Exception("Şube yetkiniz bulunamadı."))
                    }
                    if (targetType == "all_staff" || targetBranchId == "all") {
                        return Result.failure(Exception("Şube müdürleri tüm şirkete duyuru gönderemez."))
                    }
                    if (targetBranchId.isNotEmpty() && targetBranchId != userBranchId) {
                        return Result.failure(Exception("Yalnızca kendi şubenize duyuru gönderebilirsiniz."))
                    }
                    if (targetType == "user" && targetUserBranchId.isNotEmpty() && targetUserBranchId != userBranchId) {
                        return Result.failure(Exception("Yalnızca kendi şubenizdeki personeli hedefleyebilirsiniz."))
                    }
                }
                return Result.success("Duyuru gönderildi")
            }
        }

        // Test 5: Müdür -> kendi personeline -> çalışmalı
        val mgrToOwnStaff = SendRequest(
            userRole = "mudur",
            userBranchId = "branch_kadikoy",
            targetType = "branch",
            targetBranchId = "branch_kadikoy",
            targetUserUid = ""
        )
        assertTrue("Müdür kendi personeline gönderebilmeli", mgrToOwnStaff.validate().isSuccess)

        // Test 6: Müdür -> belirli kendi personeline -> çalışmalı
        val mgrToOwnSingleStaff = SendRequest(
            userRole = "mudur",
            userBranchId = "branch_kadikoy",
            targetType = "user",
            targetBranchId = "branch_kadikoy",
            targetUserUid = "user_ahmet",
            targetUserBranchId = "branch_kadikoy"
        )
        assertTrue("Müdür kendi personeline özel gönderebilmeli", mgrToOwnSingleStaff.validate().isSuccess)

        // Test 7: Müdür -> başka şube personeli -> engellenmeli
        val mgrToOtherBranchStaff = SendRequest(
            userRole = "mudur",
            userBranchId = "branch_kadikoy",
            targetType = "user",
            targetBranchId = "branch_kadikoy",
            targetUserUid = "user_mehmet",
            targetUserBranchId = "branch_besiktas"
        )
        assertTrue("Müdür başka şube personeline gönderemez", mgrToOtherBranchStaff.validate().isFailure)

        // Test 8: Müdür -> tüm şirket -> engellenmeli
        val mgrToAllCompany = SendRequest(
            userRole = "mudur",
            userBranchId = "branch_kadikoy",
            targetType = "all_staff",
            targetBranchId = "all",
            targetUserUid = ""
        )
        assertTrue("Müdür tüm şirkete duyuru gönderemez", mgrToAllCompany.validate().isFailure)

        // Test 9: Garson -> duyuru gönderme denemesi -> engellenmeli
        val waiterAttempt = SendRequest(
            userRole = "garson",
            userBranchId = "branch_kadikoy",
            targetType = "branch",
            targetBranchId = "branch_kadikoy",
            targetUserUid = ""
        )
        assertTrue("Garson duyuru gönderemez", waiterAttempt.validate().isFailure)
    }

    @Test
    fun testForegroundServiceSuppressesSelfNotifications() {
        val currentUserId = "manager_123"

        data class NotificationCheck(
            val senderUid: String,
            val currentUid: String
        ) {
            val shouldNotify: Boolean = senderUid.isNotEmpty() && senderUid != currentUid
        }

        // Own announcement:
        val ownAnnouncement = NotificationCheck(senderUid = currentUserId, currentUid = currentUserId)
        assertFalse("Kullanıcı kendi gönderdiği duyurudan bildirim almamalı", ownAnnouncement.shouldNotify)

        // Admin announcement:
        val adminAnnouncement = NotificationCheck(senderUid = "admin_999", currentUid = currentUserId)
        assertTrue("Admin duyurusu bildirimi gelmeli", adminAnnouncement.shouldNotify)
    }

    @Test
    fun testManagerUnreadCountDecreasesOnMarkAsRead() {
        val managerUid = "mgr_kadikoy"
        val managerBranch = "branch_kadikoy"

        val a1 = com.example.data.Announcement(
            id = "a1",
            branchId = "all",
            senderRole = "admin",
            targetType = "all_staff",
            readUids = emptyList()
        )
        val a2 = com.example.data.Announcement(
            id = "a2",
            branchId = managerBranch,
            senderRole = "admin",
            targetType = "branch",
            readUids = emptyList()
        )

        var list = listOf(a1, a2)

        val unreadBefore = list.count { it.isManagement() && !it.readUids.contains(managerUid) }
        assertEquals(2, unreadBefore)

        // Manager marks a1 as read
        list = list.map {
            if (it.id == "a1") it.copy(readUids = listOf(managerUid)) else it
        }

        val unreadAfter = list.count { it.isManagement() && !it.readUids.contains(managerUid) }
        assertEquals(1, unreadAfter)
    }
}
