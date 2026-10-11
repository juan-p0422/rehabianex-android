package com.example.rehabianex

import android.content.Context
import com.example.rehabianex.core.network.ApiClient
import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.core.session.OfflineDataCleanupCoordinator
import com.example.rehabianex.data.local.PatientIdentityStore
import com.example.rehabianex.data.local.AndroidNetworkStatusProvider
import com.example.rehabianex.data.local.SupervisorNotificationSettingsStore
import com.example.rehabianex.data.local.db.RehabiAnexDatabase
import com.example.rehabianex.data.remote.ApiService
import com.example.rehabianex.data.repository.AdminRepository
import com.example.rehabianex.data.repository.AuthRepository
import com.example.rehabianex.data.repository.PatientRepository
import com.example.rehabianex.data.repository.SupervisorRepository
import com.example.rehabianex.data.repository.FcmDemoRepository
import com.example.rehabianex.session.SecureSessionManager
import com.example.rehabianex.sync.PatientSyncWorker
import com.example.rehabianex.notifications.FcmTokenManager
import kotlinx.coroutines.withTimeoutOrNull

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    private val secureSessionManager = SecureSessionManager(context)
    val sessionManager: SessionManager = secureSessionManager

    private val apiService: ApiService = ApiClient.createApiService(sessionManager)
    val fcmDemoRepository = FcmDemoRepository(apiService)
    val fcmTokenManager = FcmTokenManager(applicationContext, apiService, sessionManager)
    private val database = RehabiAnexDatabase.getInstance(applicationContext)
    private val networkStatusProvider = AndroidNetworkStatusProvider(applicationContext)
    private val patientIdentityStore = PatientIdentityStore(context)
    private val supervisorNotificationSettingsStore = SupervisorNotificationSettingsStore(context)
    val adminRepository = AdminRepository(
        apiService = apiService,
        sessionManager = sessionManager,
        profileSnapshotDao = database.adminProfileSnapshotDao(),
        dashboardSummaryDao = database.adminDashboardSummaryDao(),
        networkStatusProvider = networkStatusProvider,
    )

    val patientRepository = PatientRepository(
        apiService = apiService,
        sessionManager = sessionManager,
        identityStore = patientIdentityStore,
        database = database,
        networkStatusProvider = networkStatusProvider,
        schedulePendingSync = { ownerUid ->
            PatientSyncWorker.enqueue(applicationContext, ownerUid)
        },
    )

    val supervisorRepository = SupervisorRepository(
        apiService = apiService,
        sessionManager = sessionManager,
        notificationSettingsStore = supervisorNotificationSettingsStore,
        profileSnapshotDao = database.supervisorProfileSnapshotDao(),
        dashboardSummaryDao = database.supervisorDashboardSummaryDao(),
    )

    private val offlineDataCleanupCoordinator = OfflineDataCleanupCoordinator(
        context = applicationContext,
        database = database,
        patientIdentityStore = patientIdentityStore,
        supervisorNotificationSettingsStore = supervisorNotificationSettingsStore,
        clearInMemoryState = supervisorRepository::clearSessionMemory,
    )

    val authRepository = AuthRepository(
        apiService = apiService,
        sessionManager = sessionManager,
        clearLocalUserData = offlineDataCleanupCoordinator::clearAfterConfirmedLogout,
        pendingLocalNotesCount = offlineDataCleanupCoordinator::pendingPatientNotes,
        clearPreviousUserData = offlineDataCleanupCoordinator::clearForUserSwitch,
        restrictPrivatePatientIdentity = patientRepository::restrictPrivateIdentityFromLogin,
        registerFcmToken = {
            withTimeoutOrNull(FCM_OPERATION_TIMEOUT_MILLIS) {
                fcmTokenManager.registerAfterAuthentication()
            }
        },
        revokeFcmToken = {
            withTimeoutOrNull(FCM_OPERATION_TIMEOUT_MILLIS) {
                fcmTokenManager.revokeCurrentToken()
            }
        },
    )

    private companion object {
        const val FCM_OPERATION_TIMEOUT_MILLIS = 5_000L
    }
}
