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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rehabianex.data.model.MetricsData
import com.example.rehabianex.data.model.MetricsPeriodData
import com.example.rehabianex.navigation.Routes
import com.example.rehabianex.ui.components.DangerCard
import com.example.rehabianex.ui.components.ChipSelector
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
    var selectedPeriod by rememberSaveable { mutableStateOf("7 días") }
    val period: MetricsPeriodData = if (selectedPeriod == "Mes") data.monthly else data.weekly
    MetricCard("Días consecutivos sobrio", data.soberDays)
    Spacer(Modifier.height(12.dp))
    RehabianexCard {
        Text("Periodo", fontWeight = FontWeight.Bold)
        ChipSelector(
            options = listOf("7 días", "Mes"),
            selected = selectedPeriod,
            onSelected = { selectedPeriod = it },
        )
        Text("${period.label}: ${period.dateRange}", color = RehabianexColors.GrayText)
    }
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LabelValueCard(
            "Ansiedad autorreportada",
            period.anxiety,
            Modifier.weight(1f),
            RehabianexColors.WarningBg,
        )
        LabelValueCard(
            "Craving autorreportado",
            period.craving,
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
            period.averageEnergy,
            Modifier.weight(1f),
            RehabianexColors.GreenSoft,
        )
        LabelValueCard(
            "Sueño promedio",
            period.averageSleep,
            Modifier.weight(1f),
            RehabianexColors.GreenSoft,
        )
    }
    Spacer(Modifier.height(12.dp))
    SoftCard {
        Text("Ánimo predominante", fontWeight = FontWeight.Bold)
        Text(period.predominantMood)
        Spacer(Modifier.height(6.dp))
        Text("Registros del periodo: ${period.recordsCount}")
        Text("Recaídas autorreportadas: ${period.relapseCount}")
        Text(period.riskSummary, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(12.dp))
    RehabianexCard {
        Text("Tendencia del periodo", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (period.chartValues.isEmpty()) {
            Text("Aún no hay registros para graficar.", color = RehabianexColors.GrayText)
        } else {
            SimpleBarChart(period.chartValues)
            Spacer(Modifier.height(10.dp))
            ChartLegend()
        }
    }
    Spacer(Modifier.height(12.dp))
    if (period.insight == null) {
        SoftCard {
            Text("Aún no hay registros emocionales.")
        }
    } else {
        WarningCard {
            Text(period.insight)
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "Estas métricas resumen tus autorreportes personales; no miden eficacia clínica ni sustituyen una evaluación profesional.",
        color = RehabianexColors.GrayText,
    )
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
