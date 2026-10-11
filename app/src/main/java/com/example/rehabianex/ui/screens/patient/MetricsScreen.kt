package com.example.rehabianex.ui.screens.patient

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rehabianex.data.model.MetricsData
import com.example.rehabianex.navigation.Routes
import com.example.rehabianex.ui.components.DangerCard
import com.example.rehabianex.ui.components.LabelValueCard
import com.example.rehabianex.ui.components.MetricCard
import com.example.rehabianex.ui.components.OfflineStatusCard
import com.example.rehabianex.ui.components.PatientScreenScaffold
import com.example.rehabianex.ui.components.RehabianexCard
import com.example.rehabianex.ui.components.RehabianexOutlineButton
import com.example.rehabianex.ui.components.ScreenHeader
import com.example.rehabianex.ui.components.SimpleBarChart
import com.example.rehabianex.ui.components.SoftCard
import com.example.rehabianex.ui.components.WarningCard
import com.example.rehabianex.ui.theme.RehabianexColors

@Composable
fun MetricsScreen(
    onNavigate: (String) -> Unit,
    uiState: MetricsUiState,
    onRetry: () -> Unit,
) {
    PatientScreenScaffold(Routes.Metrics, onNavigate) {
        ScreenHeader("Métricas", "Resumen de registros emocionales")
        Spacer(Modifier.height(12.dp))
        when (uiState) {
            MetricsUiState.Loading -> MetricsLoading()
            is MetricsUiState.Error -> MetricsError(uiState.message, onRetry)
            is MetricsUiState.Content -> {
                OfflineStatusCard(uiState.offlineStatus)
                MetricsContent(uiState.data)
            }
        }
    }
}

@Composable
private fun MetricsContent(data: MetricsData) {
    MetricCard("Días consecutivos sobrio", data.soberDays)
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LabelValueCard(
            "Ansiedad semanal",
            data.weeklyAnxiety,
            Modifier.weight(1f),
            RehabianexColors.WarningBg,
        )
        LabelValueCard(
            "Craving semanal",
            data.weeklyCraving,
            Modifier.weight(1f),
            RehabianexColors.WarningBg,
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LabelValueCard(
            "Energía promedio",
            data.averageEnergy,
            Modifier.weight(1f),
            RehabianexColors.GreenSoft,
        )
        LabelValueCard(
            "Sueño promedio",
            data.averageSleep,
            Modifier.weight(1f),
            RehabianexColors.GreenSoft,
        )
    }
    Spacer(Modifier.height(12.dp))
    SoftCard {
        Text("Ánimo predominante", fontWeight = FontWeight.Bold)
        Text(data.predominantMood)
        Spacer(Modifier.height(6.dp))
        Text("Registros analizados: ${data.recordsCount}")
        Text("Recaídas registradas: ${data.relapseCount}")
        Text(data.riskSummary, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(12.dp))
    RehabianexCard {
        Text("Tendencia semanal", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (data.chartValues.isEmpty()) {
            Text("Aún no hay registros para graficar.", color = RehabianexColors.GrayText)
        } else {
            SimpleBarChart(data.chartValues)
            Spacer(Modifier.height(10.dp))
            ChartLegend()
        }
    }
    Spacer(Modifier.height(12.dp))
    if (data.insight == null) {
        SoftCard {
            Text("Aún no hay registros emocionales.")
        }
    } else {
        WarningCard {
            Text(data.insight)
        }
    }
}

@Composable
private fun ChartLegend() {
    Text(
        "Colores de la tendencia",
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(6.dp))
    LegendItem(RehabianexColors.GreenMain, "Verde: ansiedad baja")
    LegendItem(RehabianexColors.BlueSoft, "Azul: ansiedad moderada")
    LegendItem(RehabianexColors.Yellow, "Amarillo: ansiedad alta")
    LegendItem(RehabianexColors.Red, "Rojo: ansiedad crítica")
}

@Composable
private fun LegendItem(
    color: Color,
    text: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, RoundedCornerShape(3.dp)),
        )
        Text(text, color = RehabianexColors.GrayText)
    }
}

@Composable
private fun MetricsLoading() {
    RehabianexCard {
        CircularProgressIndicator(color = RehabianexColors.GreenMain)
        Spacer(Modifier.height(8.dp))
        Text("Cargando métricas...")
    }
}

@Composable
private fun MetricsError(
    message: String,
    onRetry: () -> Unit,
) {
    DangerCard {
        Text(message)
    }
    Spacer(Modifier.height(10.dp))
    RehabianexOutlineButton("Reintentar", onClick = onRetry)
}
