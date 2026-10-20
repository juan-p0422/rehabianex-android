package com.example.rehabianex

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.test.platform.app.InstrumentationRegistry
import com.example.rehabianex.core.validation.preserveAiChatDraft
import com.example.rehabianex.data.model.SupervisorChatData
import com.example.rehabianex.data.model.SupervisorChatMessage
import com.example.rehabianex.ui.screens.supervisor.AiChatScreen
import com.example.rehabianex.ui.screens.supervisor.SupervisorChatUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileInputStream

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
                onInputChanged = { question = preserveAiChatDraft(it) },
                onModeChanged = {},
                onSend = { sent = true },
            )
        }

        val field = composeRule.onNodeWithText("Escribe tu pregunta con el contexto necesario")
        field.performTextInput("¿Qué cambió en la última sesión de apoyo?")
        field.assertEditableTextEquals("¿Qué cambió en la última sesión de apoyo?")
        composeRule.onNodeWithText("Enviar").performClick()

        composeRule.runOnIdle { assertTrue(sent) }
    }

    @Test
    fun physicalKeyboardSpacesRemainVisible() {
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
                onInputChanged = { question = preserveAiChatDraft(it) },
                onModeChanged = {},
                onSend = {},
            )
        }

        val field = composeRule.onNodeWithText("Escribe tu pregunta con el contexto necesario")
        field.performClick()
        executeShellCommandAndWait("input text Resume%s")
        composeRule.waitForIdle()
        field.assertEditableTextEquals("Resume ")
        executeShellCommandAndWait("input text mis%sregistros%sde%sesta%ssemana")
        composeRule.waitForIdle()
        field.assertEditableTextEquals("Resume mis registros de esta semana")
        captureEvidence("supervisor-chat-spaces.png")
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

    private fun captureEvidence(name: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(
            instrumentation.targetContext.getExternalFilesDir(null),
            "chatbot-evidence",
        ).apply { mkdirs() }
        File(directory, name).outputStream().use { output ->
            instrumentation.uiAutomation.takeScreenshot()
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    private fun executeShellCommandAndWait(command: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command)
        FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        descriptor.close()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertEditableTextEquals(
        expected: String,
    ) = assert(
        SemanticsMatcher.expectValue(
            SemanticsProperties.EditableText,
            AnnotatedString(expected),
        ),
    )
}
