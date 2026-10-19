package com.example.rehabianex

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.example.rehabianex.data.model.PrivacyNoticeDto
import com.example.rehabianex.ui.screens.privacy.PrivacyNoticeScreen
import com.example.rehabianex.ui.screens.privacy.PrivacyNoticeUiState
import com.example.rehabianex.ui.theme.RehabianexTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class PrivacyNoticePresentationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun requiredNoticeStartsUncheckedAndEnablesContinueOnlyAfterReadingCheck() {
        var confirmations = 0
        composeRule.setContent {
            RehabianexTheme {
                PrivacyNoticeScreen(
                    uiState = PrivacyNoticeUiState.Content(
                        notice = PrivacyNoticeDto(
                            title = "Aviso de Privacidad de RehabiAnex",
                            version = "2026-10-18",
                            publishedOn = "2026-10-18",
                            content = "Contenido completo sintético para la prueba.",
                        ),
                    ),
                    confirmationRequired = true,
                    onConfirm = { confirmations += 1 },
                    onRetry = {},
                    onBack = {},
                    onLogout = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Confirmar lectura del Aviso de Privacidad")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ToggleableState,
                    ToggleableState.Off,
                ),
            )
        composeRule.onNodeWithText("Continuar").performScrollTo().assertIsNotEnabled()
        captureEvidence("privacy-required-unchecked.png")
        composeRule.onNodeWithContentDescription("Confirmar lectura del Aviso de Privacidad")
            .performClick()
        composeRule.onNodeWithText("Continuar").assertIsEnabled()
        captureEvidence("privacy-required-checked.png")
        composeRule.onNodeWithText("Continuar").performClick()
        assertEquals(1, confirmations)
    }

    @Test
    fun profileReadingDoesNotExposeConfirmationControls() {
        composeRule.setContent {
            RehabianexTheme {
                PrivacyNoticeScreen(
                    uiState = PrivacyNoticeUiState.Content(
                        notice = PrivacyNoticeDto(
                            version = "2026-10-18",
                            publishedOn = "2026-10-18",
                            content = "Aviso visible en perfil.",
                        ),
                    ),
                    confirmationRequired = false,
                    onConfirm = {},
                    onRetry = {},
                    onBack = {},
                    onLogout = {},
                )
            }
        }

        composeRule.onNodeWithText("Aviso visible en perfil.").assertExists()
        composeRule.onNodeWithText("He leído el Aviso de Privacidad").assertDoesNotExist()
        composeRule.onNodeWithText("Volver").assertExists()
        captureEvidence("privacy-profile-read-only.png")
    }

    private fun captureEvidence(name: String) {
        composeRule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(
            instrumentation.targetContext.getExternalFilesDir(null),
            "privacy-evidence",
        ).apply { mkdirs() }
        File(directory, name).outputStream().use { output ->
            instrumentation.uiAutomation.takeScreenshot()
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}
