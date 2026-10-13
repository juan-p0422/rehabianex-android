package com.example.rehabianex.ui.screens.supervisor

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rehabianex.R
import com.example.rehabianex.data.model.SupervisorHomeData
import com.example.rehabianex.navigation.Routes
import com.example.rehabianex.ui.components.HomeMenuItem
import com.example.rehabianex.ui.components.HomeOverflowMenu
import com.example.rehabianex.ui.components.MetricCard
import com.example.rehabianex.ui.components.RehabianexButton
import com.example.rehabianex.ui.components.RehabianexCard
import com.example.rehabianex.ui.components.RehabianexDangerButton
import com.example.rehabianex.ui.components.RehabianexOutlineButton
import com.example.rehabianex.ui.components.ScreenHeader
import com.example.rehabianex.ui.components.SoftCard
import com.example.rehabianex.ui.components.SupervisorScreenScaffold
import com.example.rehabianex.ui.components.WarningCard
import com.example.rehabianex.ui.theme.RehabianexColors

@Composable
fun SupervisorHomeScreen(
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    uiState: SupervisorHomeUiState,
    actionState: RequestActionUiState,
    onRetry: () -> Unit,
    onRespondRequest: (String, Boolean) -> Unit,
) {
    val limitedAccess = (uiState as? SupervisorHomeUiState.Content)
        ?.data
        ?.authorized == false

    SupervisorScreenScaffold(
        currentRoute = Routes.SupervisorHome,
        onNavigate = onNavigate,
        limitedAccess = limitedAccess,
    ) {
        when (uiState) {
            SupervisorHomeUiState.Loading -> SupervisorLoadingCard()
            is SupervisorHomeUiState.Error -> SupervisorErrorCard(
                message = uiState.message,
                onRetry = onRetry,
            )

            is SupervisorHomeUiState.Content -> {
                val data = uiState.data
                if (!data.authorized) {
                    SupervisorLimitedHome(
                        data = data,
                        onNavigate = onNavigate,
                        onLogout = onLogout,
                        onRetry = onRetry,
                    )
                    return@SupervisorScreenScaffold
                }

                HomeOverflowMenu(
                    items = listOf(
                        HomeMenuItem("Perfil", Routes.SupervisorProfile, R.drawable.ic_nav_patients),
                        HomeMenuItem("Notificaciones", Routes.SupervisorNotifications, R.drawable.ic_nav_alerts),
                    ),
                    onNavigate = onNavigate,
                )
                Spacer(Modifier.height(4.dp))
                ScreenHeader(
                    title = "Supervisor autorizado",
                    subtitle = data.supervisorType,
                )
                SafeCacheStatus(data.isRefreshing, data.lastSyncedAt, data.refreshMessage)
                Spacer(Modifier.height(12.dp))
                SupervisorCodeCard(data.supervisorCode)
                Spacer(Modifier.height(12.dp))
                MetricCard("Pacientes vinculados", data.patientsCount.toString())
                Spacer(Modifier.height(12.dp))
                if (data.isFromCache) {
                    SoftCard {
                        Text("Resumen guardado", fontWeight = FontWeight.Bold)
                        Text("Los datos de pacientes se consultan únicamente al servidor.")
                    }
                } else if (data.highAnxietyCount > 0) {
                    WarningCard {
                        Text(
                            "${data.highAnxietyCount} paciente(s) con ansiedad alta.",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    SoftCard {
                        Text("Sin alertas altas por ahora.", fontWeight = FontWeight.Bold)
                        Text("La lista se actualizará cuando haya notas autorizadas.")
                    }
                }
                Spacer(Modifier.height(12.dp))
                RequestActionMessage(actionState)
                Spacer(Modifier.height(8.dp))
                if (!data.isFromCache) {
                    PendingRequestsSection(
                        pendingRequests = data.pendingRequests,
                        actionState = actionState,
                        onRespondRequest = onRespondRequest,
                    )
                }
                if (data.isFromCache) {
                    data.refreshMessage?.let {
                        SoftCard { Text(it, color = RehabianexColors.GrayText) }
                    }
                } else if (data.patients.isEmpty()) {
                    EmptySupervisorCard(
                        title = "Aún no tienes pacientes vinculados.",
                        description = "Comparte tu código de supervisor con un paciente. " +
                            "El paciente enviará la solicitud y tú podrás aceptarla desde tu cuenta.",
                    )
                } else {
                    data.patients.forEach { patient ->
                        SupervisorPatientCard(
                            patient = patient,
                            onNavigate = onNavigate,
                            buttonText = "Ver detalle",
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    RehabianexOutlineButton("Ver todos los pacientes") {
                        onNavigate(Routes.PatientsList)
                    }
                }
            }
        }
    }
}

@Composable
private fun SupervisorLimitedHome(
    data: SupervisorHomeData,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    onRetry: () -> Unit,
) {
    ScreenHeader(
        title = "Supervisor en espera",
        subtitle = data.supervisorType,
    )
    SafeCacheStatus(data.isRefreshing, data.lastSyncedAt, data.refreshMessage)
    Spacer(Modifier.height(12.dp))
    SupervisorAuthNotice(authorized = false)
    Spacer(Modifier.height(12.dp))
    RehabianexCard {
        Text(data.fullName, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(data.supervisorType, color = RehabianexColors.GrayText)
        Text("Acceso limitado", color = RehabianexColors.GrayText)
    }
    if (data.supervisorCode.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        SupervisorCodeCard(data.supervisorCode)
    }
    Spacer(Modifier.height(14.dp))
    RehabianexOutlineButton(
        text = "Revisar estado de autorización",
        onClick = onRetry,
    )
    Spacer(Modifier.height(10.dp))
    RehabianexButton("Ver datos del perfil") {
        onNavigate(Routes.SupervisorProfile)
    }
    Spacer(Modifier.height(10.dp))
    RehabianexDangerButton(
        text = "Cerrar sesión",
        onClick = onLogout,
    )
}
