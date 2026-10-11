package com.example.rehabianex

import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.core.session.UserSession
import com.example.rehabianex.data.local.NetworkStatusProvider
import com.example.rehabianex.data.local.db.AdminDashboardSummaryDao
import com.example.rehabianex.data.local.db.AdminDashboardSummaryEntity
import com.example.rehabianex.data.local.db.AdminProfileSnapshotDao
import com.example.rehabianex.data.local.db.AdminProfileSnapshotEntity
import com.example.rehabianex.data.local.db.RehabiAnexDatabase
import com.example.rehabianex.data.local.db.SupervisorDashboardSummaryDao
import com.example.rehabianex.data.local.db.SupervisorDashboardSummaryEntity
import com.example.rehabianex.data.local.db.SupervisorProfileSnapshotDao
import com.example.rehabianex.data.local.db.SupervisorProfileSnapshotEntity
import com.example.rehabianex.data.model.MeResponse
import com.example.rehabianex.data.remote.ApiService
import com.example.rehabianex.data.repository.AdminDataResult
import com.example.rehabianex.data.repository.AdminRepository
import com.example.rehabianex.data.repository.SupervisorDataResult
import com.example.rehabianex.data.repository.SupervisorRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class SafeRoleCacheTest {
    @Test
    fun supervisorHome_readsOnlySafeCachedProfileAndCounters() = runTest {
        val profileDao = FakeSupervisorProfileDao(
            SupervisorProfileSnapshotEntity(
                ownerUid = SUPERVISOR_UID,
                displayName = "Supervisor seguro",
                supervisorType = "Psicología clínica",
                supervisorCode = "RA-12345678",
                authorized = true,
                verified = true,
                status = "active",
                lastSyncedAt = 10L,
                isStale = false,
            ),
        )
        val summaryDao = FakeSupervisorSummaryDao(
            SupervisorDashboardSummaryEntity(SUPERVISOR_UID, 4, 2, 0, 12L),
        )
        val repository = SupervisorRepository(
            apiService = unexpectedApi(),
            sessionManager = RoleSessionManager(SUPERVISOR_UID, "supervisor"),
            profileSnapshotDao = profileDao,
            dashboardSummaryDao = summaryDao,
        )

        val data = (repository.loadCachedHomeData() as SupervisorDataResult.Success).data

        assertEquals("Supervisor seguro", data.fullName)
        assertEquals(4, data.patientsCount)
        assertEquals(2, data.pendingRequestsCount)
        assertTrue(data.isFromCache)
        assertTrue(data.isRefreshing)
        assertTrue(data.patients.isEmpty())
        assertTrue(data.pendingRequests.isEmpty())
    }

    @Test
    fun supervisor403_clearsSafeSnapshot() = runTest {
        val profileDao = FakeSupervisorProfileDao(
            SupervisorProfileSnapshotEntity(
                SUPERVISOR_UID, "Supervisor", "Clínico", "RA-12345678",
                true, true, "active", 10L, false,
            ),
        )
        val summaryDao = FakeSupervisorSummaryDao(
            SupervisorDashboardSummaryEntity(SUPERVISOR_UID, 1, 0, 0, 10L),
        )
        val api = apiService { method ->
            when (method) {
                "me" -> Response.error<MeResponse>(
                    403,
                    """{"ok":false,"message":"Acceso revocado"}"""
                        .toResponseBody("application/json".toMediaType()),
                )
                else -> error("Unexpected ApiService call: $method")
            }
        }
        val repository = SupervisorRepository(
            apiService = api,
            sessionManager = RoleSessionManager(SUPERVISOR_UID, "supervisor"),
            profileSnapshotDao = profileDao,
            dashboardSummaryDao = summaryDao,
        )

        val result = repository.loadHomeData()

        assertEquals(403, (result as SupervisorDataResult.Failure).statusCode)
        assertNull(profileDao.item)
        assertNull(summaryDao.item)
    }

    @Test
    fun adminHome_readsCachedOwnIdentityAndAggregateCounts() = runTest {
        val repository = AdminRepository(
            apiService = unexpectedApi(),
            sessionManager = RoleSessionManager(ADMIN_UID, "admin"),
            profileSnapshotDao = FakeAdminProfileDao(
                AdminProfileSnapshotEntity(ADMIN_UID, "Admin", "admin@example.com", "active", 20L),
            ),
            dashboardSummaryDao = FakeAdminSummaryDao(
                AdminDashboardSummaryEntity(ADMIN_UID, 3, 8, 1, 21L),
            ),
        )

        val data = repository.cachedDashboard()

        assertEquals(3, data?.pendingSupervisors)
        assertEquals(8, data?.activeSupervisors)
        assertEquals(1, data?.suspendedSupervisors)
    }

    @Test
    fun adminActions_areRejectedOfflineWithoutCallingApi() = runTest {
        var apiCalls = 0
        val repository = AdminRepository(
            apiService = apiService { apiCalls += 1; error("No network call expected") },
            sessionManager = RoleSessionManager(ADMIN_UID, "admin"),
            networkStatusProvider = NetworkStatusProvider { false },
        )

        val result = repository.authorize("supervisor-001")

        assertTrue(result is AdminDataResult.Failure)
        assertEquals(0, apiCalls)
    }

    @Test
    fun roomSurface_hasNoSupervisedPatientOrAiChatDao() {
        val daoMethods = RehabiAnexDatabase::class.java.declaredMethods.map { it.name.lowercase() }

        assertFalse(daoMethods.any { "supervisedpatient" in it })
        assertFalse(daoMethods.any { "aichat" in it || "chatmessage" in it })
    }

    private fun unexpectedApi(): ApiService = apiService { method ->
        error("Unexpected ApiService call: $method")
    }

    private fun apiService(response: (String) -> Any): ApiService = Proxy.newProxyInstance(
        ApiService::class.java.classLoader,
        arrayOf(ApiService::class.java),
    ) { _, method, _ -> response(method.name) } as ApiService

    private class RoleSessionManager(
        private val uid: String,
        private val role: String,
    ) : SessionManager {
        override fun saveSession(session: UserSession) = Unit
        override fun getIdToken(): String = "token"
        override fun getRefreshToken(): String = "refresh"
        override fun getUid(): String = uid
        override fun getEmail(): String = "$role@example.com"
        override fun getRole(): String = role
        override fun getDisplayName(): String = role.replaceFirstChar(Char::uppercase)
        override fun clear() = Unit
    }

    private class FakeSupervisorProfileDao(var item: SupervisorProfileSnapshotEntity?) :
        SupervisorProfileSnapshotDao {
        override suspend fun get(ownerUid: String) = item?.takeIf { it.ownerUid == ownerUid }
        override suspend fun upsert(item: SupervisorProfileSnapshotEntity) { this.item = item }
        override suspend fun markStale(ownerUid: String) {
            item = item?.takeIf { it.ownerUid == ownerUid }?.copy(isStale = true)
        }
        override suspend fun clearOwner(ownerUid: String) {
            if (item?.ownerUid == ownerUid) item = null
        }
    }

    private class FakeSupervisorSummaryDao(var item: SupervisorDashboardSummaryEntity?) :
        SupervisorDashboardSummaryDao {
        override suspend fun get(ownerUid: String) = item?.takeIf { it.ownerUid == ownerUid }
        override suspend fun upsert(item: SupervisorDashboardSummaryEntity) { this.item = item }
        override suspend fun clearOwner(ownerUid: String) {
            if (item?.ownerUid == ownerUid) item = null
        }
    }

    private class FakeAdminProfileDao(var item: AdminProfileSnapshotEntity?) : AdminProfileSnapshotDao {
        override suspend fun get(ownerUid: String) = item?.takeIf { it.ownerUid == ownerUid }
        override suspend fun upsert(item: AdminProfileSnapshotEntity) { this.item = item }
        override suspend fun clearOwner(ownerUid: String) {
            if (item?.ownerUid == ownerUid) item = null
        }
    }

    private class FakeAdminSummaryDao(var item: AdminDashboardSummaryEntity?) : AdminDashboardSummaryDao {
        override suspend fun get(ownerUid: String) = item?.takeIf { it.ownerUid == ownerUid }
        override suspend fun upsert(item: AdminDashboardSummaryEntity) { this.item = item }
        override suspend fun clearOwner(ownerUid: String) {
            if (item?.ownerUid == ownerUid) item = null
        }
    }

    private companion object {
        const val SUPERVISOR_UID = "supervisor-owner"
        const val ADMIN_UID = "admin-owner"
    }
}
