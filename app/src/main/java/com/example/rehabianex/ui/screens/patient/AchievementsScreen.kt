package com.example.rehabianex.ui.screens.patient

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rehabianex.data.model.AchievementItem
import com.example.rehabianex.data.model.AchievementsData
import com.example.rehabianex.navigation.Routes
import com.example.rehabianex.ui.components.DangerCard
import com.example.rehabianex.ui.components.MetricCard
import com.example.rehabianex.ui.components.OfflineStatusCard
import com.example.rehabianex.ui.components.PatientScreenScaffold
import com.example.rehabianex.ui.components.RehabianexCard
import com.example.rehabianex.ui.components.RehabianexOutlineButton
import com.example.rehabianex.ui.components.ScreenHeader
import com.example.rehabianex.ui.components.SoftCard
import com.example.rehabianex.ui.theme.RehabianexColors

@Composable
fun AchievementsScreen(
    onNavigate: (String) -> Unit,
    uiState: AchievementsUiState,
    onRetry: () -> Unit,
) {
    PatientScreenScaffold(Routes.Achievements, onNavigate) {
        ScreenHeader("Mis Logros", "Celebra tu progreso")
        Spacer(Modifier.height(12.dp))
        when (uiState) {
            AchievementsUiState.Loading -> AchievementsLoading()
            is AchievementsUiState.Error -> AchievementsError(uiState.message, onRetry)
            is AchievementsUiState.Content -> {
                OfflineStatusCard(uiState.offlineStatus)
                AchievementsContent(uiState.data)
            }
        }
    }
}

@Composable
private fun AchievementsContent(data: AchievementsData) {
    MetricCard("Días consecutivos sobrio", data.soberDays)
    Spacer(Modifier.height(16.dp))
    Text("Logros de sobriedad", fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))

    data.achievements.forEach { achievement ->
        AchievementCard(achievement = achievement)
        Spacer(Modifier.height(10.dp))
    }

    Spacer(Modifier.height(6.dp))
    SoftCard {
        Text(data.nextHint)
    }
}

@Composable
private fun AchievementCard(
    achievement: AchievementItem,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (achievement.isUnlocked) {
            RehabianexColors.Yellow.copy(alpha = 0.24f)
        } else {
            RehabianexColors.GrayCard
        },
        border = BorderStroke(
            width = if (achievement.isUnlocked) 2.dp else 1.dp,
            color = if (achievement.isUnlocked) {
                RehabianexColors.Yellow
            } else {
                RehabianexColors.GrayBorder
            },
        ),
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (achievement.isUnlocked) "★ Logro desbloqueado" else "Pendiente",
                color = if (achievement.isUnlocked) {
                    RehabianexColors.GreenDark
                } else {
                    RehabianexColors.GrayText
                },
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(achievement.title, fontWeight = FontWeight.Bold)
            Text(achievement.description, color = RehabianexColors.GrayText)
            if (achievement.progressText.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(achievement.progressText)
            }
        }
    }
}

@Composable
private fun AchievementsLoading() {
    RehabianexCard {
        CircularProgressIndicator(color = RehabianexColors.GreenMain)
        Spacer(Modifier.height(8.dp))
        Text("Cargando logros...")
    }
}

@Composable
private fun AchievementsError(
    message: String,
    onRetry: () -> Unit,
) {
    DangerCard {
        Text(message)
    }
    Spacer(Modifier.height(10.dp))
    RehabianexOutlineButton("Reintentar", onClick = onRetry)
}
