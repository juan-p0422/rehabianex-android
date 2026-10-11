package com.example.rehabianex.ui.screens.supervisor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rehabianex.navigation.Routes
import com.example.rehabianex.ui.components.AvatarCircle
import com.example.rehabianex.ui.components.MetricCard
import com.example.rehabianex.ui.components.RehabianexButton
import com.example.rehabianex.ui.components.RehabianexCard
import com.example.rehabianex.ui.components.RehabianexDangerButton
import com.example.rehabianex.ui.components.ScreenHeader
import com.example.rehabianex.ui.components.SoftCard
import com.example.rehabianex.ui.components.SupervisorScreenScaffold
import com.example.rehabianex.ui.components.WarningCard
import com.example.rehabianex.ui.theme.RehabianexColors

@Composable
fun SupervisorProfileScreen(
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    uiState: SupervisorProfileUiState,
    onRetry: () -> Unit,
) {
    val limitedAccess = (uiState as? SupervisorProfileUiState.Content)
        ?.data
        ?.authorized == false
    SupervisorScreenScaffold(
        currentRoute = Routes.SupervisorProfile,
        onNavigate = onNavigate,
        limitedAccess = limitedAccess,
    ) {
        when (uiState) {
            SupervisorProfileUiState.Loading -> SupervisorLoadingCard()
            is SupervisorProfileUiState.Error -> SupervisorErrorCard(
                message = uiState.message,
                onRetry = onRetry,
            )

            is SupervisorProfileUiState.Content -> {
                val profile = uiState.data
                ScreenHeader("Perfil supervisor", "Configuración de cuenta y permisos")
                SafeCacheStatus(profile.isRefreshing, profile.lastSyncedAt, profile.refreshMessage)
                Spacer(Modifier.height(12.dp))
                RehabianexCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        AvatarCircle(profile.initials)
                        androidx.compose.foundation.layout.Column {
                            Text(profile.fullName, fontWeight = FontWeight.Bold)
                            Text(profile.email, color = RehabianexColors.GrayText)
                            Text(profile.supervisorType, color = RehabianexColors.GrayText)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SupervisorAuthNotice(
                    authorized = profile.authorized,
                )
                Spacer(Modifier.height(12.dp))
                if (profile.supervisorCode.isNotBlank()) {
                    SupervisorCodeCard(profile.supervisorCode)
                }
                if (profile.authorized) {
                    Spacer(Modifier.height(12.dp))
                    MetricCard("Pacientes vinculados", profile.linkedPatientsCount.toString())
                    Spacer(Modifier.height(12.dp))
                    SoftCard {
                        Text("Solicitudes pendientes", fontWeight = FontWeight.Bold)
                        Text("${profile.pendingRequestsCount} solicitud(es) por revisar")
                    }
                }
                Spacer(Modifier.height(10.dp))
                WarningCard {
                    Text(
                        if (profile.authorized) {
                            "El acceso depende del consentimiento activo del paciente."
                        } else {
                            "Tu cuenta no está disponible para seguimiento en este momento."
                        },
                    )
                }
                Spacer(Modifier.height(14.dp))
                if (profile.authorized) {
                    RehabianexButton("Notificaciones") {
                        onNavigate(Routes.SupervisorNotifications)
                    }
                    Spacer(Modifier.height(8.dp))
                    RehabianexButton("Administrar pacientes y solicitudes") {
                        onNavigate(Routes.PatientsList)
                    }
                }
                Spacer(Modifier.height(16.dp))
                RehabianexDangerButton("Cerrar sesión", onClick = onLogout)
            }
        }
    }
}
