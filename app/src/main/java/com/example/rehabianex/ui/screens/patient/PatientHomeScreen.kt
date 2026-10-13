package com.example.rehabianex.ui.screens.patient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rehabianex.R
import com.example.rehabianex.data.model.PatientHomeData
import com.example.rehabianex.navigation.Routes
import com.example.rehabianex.ui.components.DangerCard
import com.example.rehabianex.ui.components.HomeMenuItem
import com.example.rehabianex.ui.components.HomeOverflowMenu
import com.example.rehabianex.ui.components.LabelValueCard
import com.example.rehabianex.ui.components.MetricCard
import com.example.rehabianex.ui.components.OfflineStatusCard
import com.example.rehabianex.ui.components.PatientScreenScaffold
import com.example.rehabianex.ui.components.RehabianexButton
import com.example.rehabianex.ui.components.RehabianexCard
import com.example.rehabianex.ui.components.RehabianexOutlineButton
import com.example.rehabianex.ui.components.ScreenHeader
import com.example.rehabianex.ui.components.SoftCard
import com.example.rehabianex.ui.theme.RehabianexColors

@Composable
fun PatientHomeScreen(
    onNavigate: (String) -> Unit,
    uiState: PatientHomeUiState,
    onRetry: () -> Unit,
) {
    PatientScreenScaffold(
        currentRoute = Routes.PatientHome,
        onNavigate = onNavigate,
    ) {
        when (uiState) {
            PatientHomeUiState.Loading -> PatientHomeLoading()
            is PatientHomeUiState.Error -> PatientHomeError(
                message = uiState.message,
                onRetry = onRetry,
            )
            is PatientHomeUiState.Content -> {
                OfflineStatusCard(uiState.offlineStatus)
                PatientHomeContent(
                    data = uiState.data,
                    onNavigate = onNavigate,
                )
            }
        }
    }
}

@Composable
private fun PatientHomeContent(
    data: PatientHomeData,
    onNavigate: (String) -> Unit,
) {
    HomeOverflowMenu(
        items = listOf(
            HomeMenuItem("Perfil", Routes.PatientProfile, R.drawable.ic_nav_patients),
            HomeMenuItem("Notificaciones", Routes.Notifications, R.drawable.ic_nav_alerts),
            HomeMenuItem("Vinculación", Routes.SupervisorLink, R.drawable.ic_nav_support),
        ),
        onNavigate = onNavigate,
    )
    Spacer(Modifier.height(4.dp))
    ScreenHeader("Hola, ${data.firstName}", "Tu progreso de hoy")
    Spacer(Modifier.height(14.dp))
    MetricCard("Días consecutivos sobrio", data.soberDays)
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LabelValueCard(
            label = "Registro diario",
            value = data.dailyStatus,
            modifier = Modifier.weight(1f),
            background = RehabianexColors.WarningBg,
        )
        LabelValueCard(
            label = "Estado reciente",
            value = data.recentState,
            modifier = Modifier.weight(1f),
            background = RehabianexColors.GreenSoft,
        )
    }
    Spacer(Modifier.height(12.dp))
    RehabianexCard {
        Text("Mensaje de apoyo:")
        Text(
            "Un día a la vez. Tu avance cuenta.",
            fontWeight = FontWeight.Bold,
        )
    }
    Spacer(Modifier.height(12.dp))
    RehabianexButton(
        text = "Registrar estado",
        onClick = { onNavigate(Routes.EmotionalCheckin) },
    )
    Spacer(Modifier.height(16.dp))
    SoftCard {
        Text("Accesos rápidos", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        RehabianexOutlineButton("Ver métricas") {
            onNavigate(Routes.Metrics)
        }
        Spacer(Modifier.height(6.dp))
        RehabianexOutlineButton("Mis logros") {
            onNavigate(Routes.Achievements)
        }
        Spacer(Modifier.height(6.dp))
        RehabianexOutlineButton("Red de apoyo") {
            onNavigate(Routes.SupportNetwork)
        }
    }
}

@Composable
private fun PatientHomeLoading() {
    ScreenHeader("Tu progreso", "Cargando información")
    Spacer(Modifier.height(14.dp))
    RehabianexCard {
        CircularProgressIndicator(color = RehabianexColors.GreenMain)
        Spacer(Modifier.height(10.dp))
        Text("Cargando tus datos...")
    }
}

@Composable
private fun PatientHomeError(
    message: String,
    onRetry: () -> Unit,
) {
    ScreenHeader("Tu progreso", "No se pudo cargar la información")
    Spacer(Modifier.height(14.dp))
    DangerCard {
        Text(message)
    }
    Spacer(Modifier.height(12.dp))
    RehabianexOutlineButton("Reintentar", onClick = onRetry)
}
