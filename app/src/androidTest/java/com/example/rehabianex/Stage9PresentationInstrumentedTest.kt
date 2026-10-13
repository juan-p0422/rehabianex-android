package com.example.rehabianex

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.rehabianex.adaptive.AdaptiveSuggestion
import com.example.rehabianex.data.model.NotificationSettingsData
import com.example.rehabianex.navigation.Routes
import com.example.rehabianex.ui.screens.patient.DailyNoteSaveUiState
import com.example.rehabianex.ui.screens.patient.EmotionalCheckinScreen
import com.example.rehabianex.ui.screens.patient.FormSaveUiState
import com.example.rehabianex.ui.screens.patient.NotificationSettingsUiState
import com.example.rehabianex.ui.screens.patient.NotificationsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class Stage9PresentationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsExplainExperimentalOptInAndDoNotInferRelapseFromInactivity() {
        composeRule.setContent {
            NotificationsScreen(
                onNavigate = {},
                uiState = NotificationSettingsUiState.Content(
                    settings = NotificationSettingsData.defaults().copy(
                        adaptiveRulesAvailable = true,
                        adaptiveRulesVersion = "experimental-v1",
                    ),
                    events = emptyList(),
                ),
                saveState = FormSaveUiState.Idle,
                onRetry = {},
                onSave = {},
                onInputChanged = {},
            )
        }

        composeRule.onNodeWithText("Apoyo adaptativo experimental").performScrollTo().assertExists()
        composeRule.onNodeWithText("Sugerencia tras un registro").performScrollTo().assertExists()
        composeRule.onNodeWithText("Recordatorio por inactividad").performScrollTo().assertExists()
        composeRule.onNodeWithText(
            "No se enviarán recordatorios por falta de registros.",
        ).assertExists()
    }

    @Test
    fun immediateSuggestionExplainsReasonAndOpensExistingResourceLibrary() {
        var destination = ""
        composeRule.setContent {
            EmotionalCheckinScreen(
                onNavigate = { destination = it },
                saveState = DailyNoteSaveUiState.Success(
                    message = "Guardado",
                    suggestion = AdaptiveSuggestion(
                        resourceId = "grounding_54321",
                        title = "Grounding 5-4-3-2-1",
                        reason = "Basado en tu autorreporte. No es un diagnóstico ni una predicción de recaída.",
                    ),
                ),
                onSave = {},
                onInputChanged = {},
            )
        }

        composeRule.onNodeWithText("Sugerencia opcional").performScrollTo().assertExists()
        composeRule.onNodeWithText("Abrir recursos breves").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(Routes.Interventions, destination) }
    }
}
