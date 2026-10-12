package com.example.rehabianex.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.rehabianex.BuildConfig
import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.core.session.SessionInvalidationBus
import com.example.rehabianex.data.repository.AdminRepository
import com.example.rehabianex.data.repository.AuthRepository
import com.example.rehabianex.data.repository.PatientRepository
import com.example.rehabianex.data.repository.SupervisorRepository
import com.example.rehabianex.data.repository.FcmDemoRepository
import com.example.rehabianex.notifications.NotificationRescheduleWorker
import com.example.rehabianex.ui.screens.admin.AdminHomeScreen
import com.example.rehabianex.ui.screens.admin.AdminHomeViewModel
import com.example.rehabianex.ui.screens.admin.AdminPanelUnavailableScreen
import com.example.rehabianex.ui.screens.auth.AuthUiState
import com.example.rehabianex.ui.screens.auth.AuthViewModel
import com.example.rehabianex.ui.screens.auth.ConsentScreen
import com.example.rehabianex.ui.screens.auth.LoginScreen
import com.example.rehabianex.ui.screens.auth.RegisterScreen
import com.example.rehabianex.ui.screens.auth.RoleSelectionScreen
import com.example.rehabianex.ui.screens.auth.SplashScreen
import com.example.rehabianex.ui.screens.notifications.FcmDemoScreen
import com.example.rehabianex.ui.screens.notifications.FcmDemoViewModel
import com.example.rehabianex.validation.LoginFormInput
import kotlinx.coroutines.launch

@Composable
fun RehabianexNavHost(
    adminRepository: AdminRepository,
    authRepository: AuthRepository,
    patientRepository: PatientRepository,
    supervisorRepository: SupervisorRepository,
    fcmDemoRepository: FcmDemoRepository,
    sessionManager: SessionManager,
    navController: NavHostController = rememberNavController(),
    requestedRoute: String? = null,
    onRouteRequestConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val startDestination = remember(sessionManager) {
        val hasSession = sessionManager.hasActiveSession()

        activeSessionHomeRoute(hasSession, sessionManager.getRole()) ?: Routes.Splash
    }
    var authInfoMessage by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    val coroutineScope = rememberCoroutineScope()
    var pendingLogoutNotesCount by rememberSaveable { mutableStateOf<Int?>(null) }
    var logoutInProgress by rememberSaveable { mutableStateOf(false) }

    fun beginLogout(confirmPendingNotesDeletion: Boolean) {
        if (logoutInProgress) return
        logoutInProgress = true
        coroutineScope.launch {
            try {
                val result = authRepository.logout(confirmPendingNotesDeletion)
                if (result.requiresPendingNotesConfirmation) {
                    pendingLogoutNotesCount = result.pendingNotesCount
                } else {
                    authInfoMessage = result.message
                    navController.returnToLogin()
                }
            } finally {
                logoutInProgress = false
            }
        }
    }
    val logout: () -> Unit = { beginLogout(confirmPendingNotesDeletion = false) }

    LaunchedEffect(sessionManager, navController) {
        SessionInvalidationBus.events.collect { message ->
            authInfoMessage = message
            navController.returnToLogin()
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(Routes.Splash) {
            SplashScreen(
                onStartClick = {
                    navController.navigate(Routes.Login)
                },
            )
        }
        composable(Routes.Login) {
            val authViewModel: AuthViewModel = viewModel(
                factory = AuthViewModel.Factory(authRepository),
            )
            val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
            val success = uiState as? AuthUiState.Success

            LaunchedEffect(success) {
                success?.let {
                    authViewModel.clearState()
                    NotificationRescheduleWorker.enqueue(context)
                    val destination = homeRouteForRole(it.session.role)
                        ?: return@let
                    navController.navigate(destination) {
                        popUpTo(Routes.Login) {
                            inclusive = true
                        }
                    }
                }
            }

            LoginScreen(
                onLoginClick = { email, password ->
                    authViewModel.login(
                        LoginFormInput(
                            email = email,
                            password = password,
                        ),
                    )
                },
                onRegisterClick = {
                    navController.navigate(Routes.Register)
                },
                uiState = uiState,
                onInputChanged = authViewModel::clearState,
                infoMessage = authInfoMessage,
            )
        }
        composable(Routes.Register) {
            val authViewModel: AuthViewModel = viewModel(
                factory = AuthViewModel.Factory(authRepository),
            )
            val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
            val success = uiState as? AuthUiState.Success
            val accountCreated = uiState as? AuthUiState.AccountCreated

            LaunchedEffect(success) {
                success?.let {
                    authViewModel.clearState()
                    val destination = postRegistrationRoute(it.session.role) ?: Routes.Login
                    if (destination != Routes.Consent) {
                        NotificationRescheduleWorker.enqueue(context)
                    }
                    navController.navigate(destination) {
                        popUpTo(Routes.Register) {
                            inclusive = true
                        }
                    }
                }
            }

            LaunchedEffect(accountCreated) {
                accountCreated?.let {
                    authInfoMessage = it.message
                    authViewModel.clearState()
                    navController.navigate(Routes.Login) {
                        popUpTo(Routes.Register) {
                            inclusive = true
                        }
                    }
                }
            }

            RegisterScreen(
                onRegisterClick = { form ->
                    authViewModel.register(form)
                },
                uiState = uiState,
                onInputChanged = authViewModel::clearState,
            )
        }
        composable(Routes.Consent) {
            val continueTo: (String) -> Unit = { destination ->
                NotificationRescheduleWorker.enqueue(context)
                navController.navigate(destination) {
                    popUpTo(Routes.Consent) {
                        inclusive = true
                    }
                }
            }
            ConsentScreen(
                onConfigureSupervisionClick = {
                    continueTo(patientSupervisionSetupRoute(configureNow = true))
                },
                onContinueWithoutSupervisionClick = {
                    continueTo(patientSupervisionSetupRoute(configureNow = false))
                },
            )
        }
        composable(Routes.RoleSelection) {
            val activeDestination = activeSessionHomeRoute(
                hasActiveSession = sessionManager.hasActiveSession(),
                role = sessionManager.getRole(),
            )
            LaunchedEffect(activeDestination) {
                activeDestination?.let { destination ->
                    navController.navigate(destination) {
                        popUpTo(Routes.RoleSelection) {
                            inclusive = true
                        }
                    }
                }
            }
            if (activeDestination == null) {
                RoleSelectionScreen(
                    onContinueAsPatient = {
                        navController.navigate(Routes.PatientHome)
                    },
                    onContinueAsSupervisor = {
                        navController.navigate(Routes.SupervisorHome)
                    },
                )
            }
        }

        patientGraph(
            navController = navController,
            patientRepository = patientRepository,
            onLogout = logout,
        )
        supervisorGraph(
            navController = navController,
            supervisorRepository = supervisorRepository,
            onLogout = logout,
        )
        composable(Routes.AdminHome) {
            if (!BuildConfig.ADMIN_PANEL_ENABLED) {
                AdminPanelUnavailableScreen(
                    onLogout = logout,
                    onNavigate = navController::navigateSingleTop,
                )
                return@composable
            }
            val viewModel: AdminHomeViewModel = viewModel(
                factory = AdminHomeViewModel.Factory(adminRepository),
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val actionState by viewModel.actionState.collectAsStateWithLifecycle()
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                viewModel.refreshIfStale()
            }
            AdminHomeScreen(
                uiState = uiState,
                actionState = actionState,
                onRetry = viewModel::load,
                onSelectFilter = viewModel::selectFilter,
                onSearch = viewModel::search,
                onPreviousPage = viewModel::previousPage,
                onNextPage = viewModel::nextPage,
                onAuthorize = viewModel::authorize,
                onReject = viewModel::reject,
                onSuspend = viewModel::suspendSupervisor,
                onReactivate = viewModel::reactivate,
                onNavigate = navController::navigateSingleTop,
                onLogout = logout,
            )
        }
        composable(Routes.FcmDemo) {
            val role = sessionManager.getRole()
            val viewModel: FcmDemoViewModel = viewModel(
                factory = FcmDemoViewModel.Factory(fcmDemoRepository, role),
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            FcmDemoScreen(
                role = role,
                uiState = uiState,
                onSend = viewModel::send,
                onBack = {
                    if (!navController.popBackStack()) {
                        homeRouteForRole(role)?.let { navController.navigateSingleTop(it) }
                    }
                },
            )
        }
    }

    LaunchedEffect(requestedRoute) {
        requestedRoute?.let { route ->
            val role = sessionManager.getRole()
            if (sessionManager.hasActiveSession() && notificationRouteAllowed(role, route)) {
                navController.navigate(route) {
                    launchSingleTop = true
                }
            }
            onRouteRequestConsumed()
        }
    }

    pendingLogoutNotesCount?.let { count ->
        AlertDialog(
            onDismissRequest = {
                if (!logoutInProgress) pendingLogoutNotesCount = null
            },
            title = { Text("Registros sin sincronizar") },
            text = {
                Text(
                    if (count < 0) {
                        "No fue posible comprobar si hay registros pendientes. Si cierras sesión, los datos locales de esta cuenta se eliminarán."
                    } else if (count == 1) {
                        "Hay 1 registro emocional pendiente. Si cierras sesión ahora, se eliminará de este dispositivo."
                    } else {
                        "Hay $count registros emocionales pendientes. Si cierras sesión ahora, se eliminarán de este dispositivo."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingLogoutNotesCount = null
                        beginLogout(confirmPendingNotesDeletion = true)
                    },
                    enabled = !logoutInProgress,
                ) {
                    Text("Cerrar sesión y eliminar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingLogoutNotesCount = null },
                    enabled = !logoutInProgress,
                ) {
                    Text("Cancelar")
                }
            },
        )
    }

    if (logoutInProgress) {
        AlertDialog(
            onDismissRequest = {},
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
            },
            title = { Text("Cerrando sesión") },
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text("Espera mientras protegemos y cerramos tu sesión.")
                }
            },
            confirmButton = {},
        )
    }
}

internal fun notificationRouteAllowed(role: String?, route: String): Boolean = when (role) {
    "patient" -> route in setOf(
        Routes.PatientHome,
        Routes.EmotionalCheckin,
        Routes.Achievements,
        Routes.Agenda,
        Routes.Interventions,
        Routes.SupervisorLink,
        Routes.Notifications,
    )
    "supervisor" -> route in setOf(
        Routes.SupervisorHome,
        Routes.PatientsList,
        Routes.SupervisorNotifications,
    ) || route.matches(SAFE_PATIENT_DETAIL_ROUTE)
    "admin" -> route == Routes.AdminHome
    else -> false
}

private val SAFE_PATIENT_DETAIL_ROUTE = Regex(
    "^${Routes.PatientDetail}/[A-Za-z0-9_-]{1,128}$",
)

internal fun homeRouteForRole(role: String?): String? = when (role) {
    "patient" -> Routes.PatientHome
    "supervisor" -> Routes.SupervisorHome
    "admin" -> Routes.AdminHome
    else -> null
}

internal fun activeSessionHomeRoute(
    hasActiveSession: Boolean,
    role: String?,
): String? = if (hasActiveSession) homeRouteForRole(role) else null

internal fun postRegistrationRoute(role: String?): String? = when (role) {
    "patient" -> Routes.Consent
    else -> homeRouteForRole(role)
}

internal fun patientSupervisionSetupRoute(configureNow: Boolean): String = if (configureNow) {
    Routes.SupervisorLink
} else {
    Routes.PatientHome
}
