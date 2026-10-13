package com.example.rehabianex

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var appContainer by mutableStateOf<AppContainer?>(null)
    private var requestedRoute by mutableStateOf<String?>(null)
    private var startupFailed by mutableStateOf(false)
    private var pendingRouteIntent: Intent? = null
    private var initializationJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingRouteIntent = intent
        enableEdgeToEdge()
        setContent {
            RehabianexTheme {
                val container = appContainer
                if (container == null) {
                    AppStartupScreen(
                        failed = startupFailed,
                        onRetry = ::initializeApplication,
                    )
                } else {
                    RehabianexNavHost(
                        adminRepository = container.adminRepository,
                        authRepository = container.authRepository,
                        dataControlRepository = container.dataControlRepository,
                        dataExportFileManager = container.dataExportFileManager,
                        patientRepository = container.patientRepository,
                        supervisorRepository = container.supervisorRepository,
                        fcmDemoRepository = container.fcmDemoRepository,
                        sessionManager = container.sessionManager,
                        requestedRoute = requestedRoute,
                        onRouteRequestConsumed = { requestedRoute = null },
                    )
                }
            }
        }
        initializeApplication()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val container = appContainer
        if (container == null) {
            pendingRouteIntent = intent
            return
        }
        lifecycleScope.launch {
            requestedRoute = withContext(Dispatchers.IO) {
                requestedRouteFrom(intent, container.sessionManager.getRole())
            }
        }
    }

    private fun initializeApplication() {
        if (initializationJob?.isActive == true || appContainer != null) return
        startupFailed = false
        initializationJob = lifecycleScope.launch {
            try {
                val container = withContext(Dispatchers.IO) {
                    FcmNotificationPresenter.ensureChannel(applicationContext)
                    AppContainer(applicationContext)
                }
                val routeIntent = pendingRouteIntent ?: intent
                val route = withContext(Dispatchers.IO) {
                    requestedRouteFrom(routeIntent, container.sessionManager.getRole())
                }
                pendingRouteIntent = null
                requestedRoute = route
                appContainer = container

                launch(Dispatchers.IO) {
                    NotificationRescheduleWorker.enqueue(applicationContext)
                    container.fcmTokenManager.ensureRegisteredForActiveSession()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                startupFailed = true
            }
        }
    }

    private fun requestedRouteFrom(intent: Intent, role: String?): String? {
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
            role = role,
            type = type,
            requestedRoute = intent.getStringExtra(FcmContract.DATA_ROUTE),
            entityId = entityId,
        )
    }
}

@Composable
private fun AppStartupScreen(
    failed: Boolean,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (failed) {
                Text("No fue posible preparar RehabiAnex.")
                Button(onClick = onRetry) {
                    Text("Reintentar")
                }
            } else {
                CircularProgressIndicator()
                Text("Preparando RehabiAnex")
            }
        }
    }
}
