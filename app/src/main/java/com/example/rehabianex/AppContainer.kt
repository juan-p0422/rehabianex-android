package com.example.rehabianex

import android.content.Context
import com.example.rehabianex.core.network.ApiClient
import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.core.session.OfflineDataCleanupCoordinator
import com.example.rehabianex.core.session.SessionRefreshCoordinator
import com.example.rehabianex.data.local.PatientIdentityStore
import com.example.rehabianex.data.export.DataExportFileManager
import com.example.rehabianex.data.local.AndroidNetworkStatusProvider
import com.example.rehabianex.data.local.SupervisorNotificationSettingsStore
import com.example.rehabianex.data.local.SharedPreferencesPrivacyNoticeReadStore
import com.example.rehabianex.data.local.db.RehabiAnexDatabase
import com.example.rehabianex.data.remote.ApiService
import com.example.rehabianex.data.repository.AdminRepository
import com.example.rehabianex.data.repository.AuthRepository
import com.example.rehabianex.data.repository.DataControlRepository
import com.example.rehabianex.data.repository.PatientRepository
import com.example.rehabianex.data.repository.PrivacyNoticeRepository
import com.example.rehabianex.data.repository.SupervisorRepository
import com.example.rehabianex.data.repository.FcmDemoRepository
import com.example.rehabianex.session.SecureSessionManager
import com.example.rehabianex.sync.PatientSyncWorker
import com.example.rehabianex.adaptive.AdaptiveSupportManager
import com.example.rehabianex.notifications.FcmTokenManager
import kotlinx.coroutines.withTimeoutOrNull

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext
    private val secureSessionManager = SecureSessionManager(context)
    val sessionManager: SessionManager = secureSessionManager

    private val apiService: ApiService = ApiClient.createApiService(sessionManager)
    private val sessionRefreshCoordinator = SessionRefreshCoordinator(apiService, sessionManager)
    val fcmDemoRepository = FcmDemoRepository(apiService)
    val fcmTokenManager = FcmTokenManager(applicationContext, apiService, sessionManager)
    private val database = RehabiAnexDatabase.getInstance(applicationContext)
    private val networkStatusProvider = AndroidNetworkStatusProvider(applicationContext)
    val adaptiveSupportManager = AdaptiveSupportManager(applicationContext, database, sessionManager)
    private val patientIdentityStore = PatientIdentityStore(context)
    private val supervisorNotificationSettingsStore = SupervisorNotificationSettingsStore(context)
    private val privacyNoticeReadStore = SharedPreferencesPrivacyNoticeReadStore(context)
    val dataExportFileManager = DataExportFileManager(applicationContext)
    val privacyNoticeRepository = PrivacyNoticeRepository(
        apiService = apiService,
        sessionManager = sessionManager,
        readStore = privacyNoticeReadStore,
    ) {
        applicationContext.assets.open("legal/privacy-notice-2026-10-18.md")
            .bufferedReader()
            .use { it.readText() }
    }
    val adminRepository = AdminRepository(
        apiService = apiService,
        sessionManager = sessionManager,
        profileSnapshotDao = database.adminProfileSnapshotDao(),
        dashboardSummaryDao = database.adminDashboardSummaryDao(),
        networkStatusProvider = networkStatusProvider,
        sessionRefreshCoordinator = sessionRefreshCoordinator,
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
        onAdaptiveNoteSaved = adaptiveSupportManager::onNoteSaved,
        onNotificationSettingsSaved = adaptiveSupportManager::scheduleInactivity,
        sessionRefreshCoordinator = sessionRefreshCoordinator,
    )

    val supervisorRepository = SupervisorRepository(
        apiService = apiService,
        sessionManager = sessionManager,
        notificationSettingsStore = supervisorNotificationSettingsStore,
        profileSnapshotDao = database.supervisorProfileSnapshotDao(),
        dashboardSummaryDao = database.supervisorDashboardSummaryDao(),
        sessionRefreshCoordinator = sessionRefreshCoordinator,
    )

    private val offlineDataCleanupCoordinator = OfflineDataCleanupCoordinator(
        context = applicationContext,
        database = database,
        patientIdentityStore = patientIdentityStore,
        supervisorNotificationSettingsStore = supervisorNotificationSettingsStore,
        clearInMemoryState = supervisorRepository::clearSessionMemory,
        privacyNoticeReadStore = privacyNoticeReadStore,
    )

    val dataControlRepository = DataControlRepository(
        apiService = apiService,
        sessionManager = sessionManager,
        clearLocalUserData = { uid, role ->
            offlineDataCleanupCoordinator.clearAfterConfirmedLogout(uid, role)
            adaptiveSupportManager.clearOwnerState(uid)
            dataExportFileManager.clearExports()
        },
        sessionRefreshCoordinator = sessionRefreshCoordinator,
    )

    val authRepository = AuthRepository(
        apiService = apiService,
        sessionManager = sessionManager,
        clearLocalUserData = { uid, role ->
            offlineDataCleanupCoordinator.clearAfterConfirmedLogout(uid, role)
            adaptiveSupportManager.clearOwnerState(uid)
            dataExportFileManager.clearExports()
        },
        pendingLocalNotesCount = offlineDataCleanupCoordinator::pendingPatientNotes,
        clearPreviousUserData = { uid, role ->
            offlineDataCleanupCoordinator.clearForUserSwitch(uid, role)
            adaptiveSupportManager.clearOwnerState(uid)
            dataExportFileManager.clearExports()
        },
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
