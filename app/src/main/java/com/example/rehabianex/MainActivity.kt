package com.example.rehabianex

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.rehabianex.navigation.RehabianexNavHost
import com.example.rehabianex.notifications.AgendaNotificationHelper
import com.example.rehabianex.notifications.FcmContract
import com.example.rehabianex.notifications.FcmNotificationDeduplicator
import com.example.rehabianex.notifications.FcmNotificationPresenter
import com.example.rehabianex.notifications.NotificationRescheduleWorker
import com.example.rehabianex.notifications.safeEntityIdOrNull
import com.example.rehabianex.notifications.safeFcmRoute
import com.example.rehabianex.notifications.validatedFcmType
import com.example.rehabianex.ui.theme.RehabianexTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val appContainer by lazy {
        AppContainer(applicationContext)
    }
    private var requestedRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FcmNotificationPresenter.ensureChannel(applicationContext)
        requestedRoute = requestedRouteFrom(intent)
        NotificationRescheduleWorker.enqueue(applicationContext)
        lifecycleScope.launch {
            appContainer.fcmTokenManager.ensureRegisteredForActiveSession()
        }
        enableEdgeToEdge()
        setContent {
            RehabianexTheme {
                RehabianexNavHost(
                    adminRepository = appContainer.adminRepository,
                    authRepository = appContainer.authRepository,
                    patientRepository = appContainer.patientRepository,
                    supervisorRepository = appContainer.supervisorRepository,
                    fcmDemoRepository = appContainer.fcmDemoRepository,
                    sessionManager = appContainer.sessionManager,
                    requestedRoute = requestedRoute,
                    onRouteRequestConsumed = { requestedRoute = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedRoute = requestedRouteFrom(intent)
    }

    private fun requestedRouteFrom(intent: Intent): String? {
        intent.getStringExtra(AgendaNotificationHelper.EXTRA_OPEN_ROUTE)?.let { return it }
        val type = validatedFcmType(intent.getStringExtra(FcmContract.DATA_TYPE)) ?: return null
        val entityId = intent.getStringExtra(FcmContract.DATA_ENTITY_ID).safeEntityIdOrNull()
        val notificationId = intent.getStringExtra(FcmContract.DATA_NOTIFICATION_ID)
        FcmNotificationDeduplicator.recordRemote(
            context = applicationContext,
            type = type,
            notificationId = notificationId,
            entityId = entityId,
        )
        if (type == FcmContract.TYPE_APPOINTMENT_REMINDER && entityId != null) {
            AgendaNotificationHelper.cancelEventReminder(applicationContext, entityId)
        }
        return safeFcmRoute(
            role = appContainer.sessionManager.getRole(),
            type = type,
            requestedRoute = intent.getStringExtra(FcmContract.DATA_ROUTE),
            entityId = entityId,
        )
    }
}
