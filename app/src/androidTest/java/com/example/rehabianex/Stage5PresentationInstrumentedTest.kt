package com.example.rehabianex

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.rehabianex.data.model.MetricsData
import com.example.rehabianex.data.model.MetricsPeriodData
import com.example.rehabianex.ui.screens.patient.DailyNoteSaveUiState
import com.example.rehabianex.ui.screens.patient.EmotionalCheckinScreen
import com.example.rehabianex.ui.screens.patient.MetricsScreen
import com.example.rehabianex.ui.screens.patient.MetricsUiState
import org.junit.Rule
import org.junit.Test

class Stage5PresentationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun scalesStartUnanswered_preserveOnError_andClearAfterConfirmedSave() {
        var saveState by mutableStateOf<DailyNoteSaveUiState>(DailyNoteSaveUiState.Idle)
        composeRule.setContent {
            EmotionalCheckinScreen(
                onNavigate = {},
                saveState = saveState,
                onSave = { saveState = DailyNoteSaveUiState.Error("Error de red") },
                onInputChanged = {},
            )
        }

        composeRule.onAllNodesWithText("5").assertCountEquals(4)
        composeRule.onAllNodesWithText("5")[0].assertIsNotSelected().performClick().assertIsSelected()
        composeRule.onNodeWithText("Guardar registro").performScrollTo().performClick()
        composeRule.onAllNodesWithText("5")[0].assertIsSelected()

        composeRule.runOnIdle { saveState = DailyNoteSaveUiState.Success("Guardado") }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("5")[0].assertIsNotSelected()
    }

    @Test
    fun metricsExposePeriodRangeAndSelfReportDisclaimer() {
        composeRule.setContent {
            MetricsScreen(
                onNavigate = {},
                uiState = MetricsUiState.Content(
                    MetricsData(
                        soberDays = "4 días",
                        weekly = period("Últimos 7 días", "06/10/2026 – 12/10/2026", 8),
                        monthly = period("Mes actual", "01/10/2026 – 12/10/2026", 13),
                    ),
                ),
                onRetry = {},
            )
        }

        composeRule.onNodeWithText("Últimos 7 días: 06/10/2026 – 12/10/2026")
            .assertExists()
        composeRule.onNodeWithText("Mes").performClick()
        composeRule.onNodeWithText("Registros del periodo: 13").assertExists()
        composeRule.onNodeWithText(
            "Estas métricas resumen tus autorreportes personales; no miden eficacia clínica ni sustituyen una evaluación profesional.",
        ).performScrollTo()
    }

    private fun period(label: String, range: String, records: Int) = MetricsPeriodData(
        label = label,
        dateRange = range,
        anxiety = "2/5",
        craving = "1/5",
        averageEnergy = "3/5",
        averageSleep = "4/5",
        predominantMood = "Neutral",
        chartValues = listOf(0.2f),
        insight = null,
        recordsCount = records,
        relapseCount = 0,
        riskSummary = "Riesgo bajo",
    )
}
