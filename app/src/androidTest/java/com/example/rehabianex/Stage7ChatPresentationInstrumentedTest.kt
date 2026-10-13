package com.example.rehabianex

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.rehabianex.data.model.SupervisorChatData
import com.example.rehabianex.data.model.SupervisorChatMessage
import com.example.rehabianex.ui.screens.supervisor.AiChatScreen
import com.example.rehabianex.ui.screens.supervisor.SupervisorChatUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class Stage7ChatPresentationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun normalAccentedQuestionCanBeEnteredAndSentOnce() {
        var sent = false
        composeRule.setContent {
            var question by remember { mutableStateOf("") }
            AiChatScreen(
                onNavigate = {},
                onBack = {},
                uiState = SupervisorChatUiState(
                    loading = false,
                    question = question,
                    data = SupervisorChatData(
                        supervisorUid = "synthetic-supervisor",
                        patientsCount = 1,
                        authorized = true,
                    ),
                ),
                onRetry = {},
                onInputChanged = { question = it },
                onModeChanged = {},
                onSend = { sent = true },
            )
        }

        composeRule.onNodeWithText("Escribe tu pregunta con el contexto necesario")
            .performTextInput("¿Qué cambió en la última sesión de apoyo?")
        composeRule.onNodeWithText("Enviar").performClick()

        composeRule.runOnIdle { assertTrue(sent) }
    }

    @Test
    fun responseShowsPeriodDataLimitsQuotaAndLocalFallbackOrigin() {
        composeRule.setContent {
            AiChatScreen(
                onNavigate = {},
                onBack = {},
                uiState = SupervisorChatUiState(
                    loading = false,
                    data = SupervisorChatData(
                        supervisorUid = "synthetic-supervisor",
                        patientsCount = 1,
                        authorized = true,
                        messages = listOf(
                            SupervisorChatMessage(
                                text = "Resumen local seguro.",
                                fromUser = false,
                                responseMode = "fallback_local",
                                periodLabel = "06/10/2026 al 12/10/2026",
                                dataLimits = listOf("Solo hay dos registros autorreportados."),
                                quotaRemaining = 49,
                                quotaLimit = 50,
                            ),
                        ),
                    ),
                ),
                onRetry = {},
                onInputChanged = {},
                onModeChanged = {},
                onSend = {},
            )
        }

        composeRule.onNodeWithText("Periodo del resumen: 06/10/2026 al 12/10/2026")
            .performScrollTo()
            .assertExists()
        composeRule.onNodeWithText(
            "Limitaciones de los datos: Solo hay dos registros autorreportados.",
        ).assertExists()
        composeRule.onNodeWithText("Consultas IA disponibles en 24 h: 49 de 50").assertExists()
        composeRule.onNodeWithText(
            "Respuesta local segura por indisponibilidad temporal del proveedor",
        ).assertExists()
    }
}
