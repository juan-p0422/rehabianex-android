package com.example.rehabianex

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.createComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.rehabianex.ui.screens.auth.RegisterScreen
import com.example.rehabianex.ui.theme.RehabianexTheme
import org.junit.Rule
import org.junit.Test

class SensitiveConsentPresentationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun patientRequiresTwoIndependentUncheckedDecisions() {
        composeRule.setContent {
            RehabianexTheme {
                RegisterScreen(
                    onRegisterClick = {},
                    onPrivacyNoticeClick = {},
                )
            }
        }

        val unchecked = SemanticsMatcher.expectValue(
            SemanticsProperties.ToggleableState,
            ToggleableState.Off,
        )
        composeRule.onAllNodes(unchecked).assertCountEquals(2)
        composeRule.onNodeWithText(
            "Acepto de forma separada el tratamiento de mis datos sensibles de seguimiento (versión 2026-10-27).",
        ).assertExists()
        composeRule.onNodeWithText("Registrar").assertIsNotEnabled()

        composeRule.onAllNodes(unchecked)[0].performClick()
        // Privacy gates submission; the separate sensitive decision remains
        // unchecked so the validator can display its focused error on submit.
        composeRule.onNodeWithText("Registrar").assertIsEnabled()
        composeRule.onAllNodes(unchecked)[0].performClick()
        composeRule.onNodeWithText("Registrar").assertIsEnabled()
    }

    @Test
    fun supervisorDoesNotReceiveSensitiveTrackingConsentField() {
        composeRule.setContent {
            RehabianexTheme {
                RegisterScreen(
                    onRegisterClick = {},
                    onPrivacyNoticeClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Supervisor").performClick()
        composeRule.onNodeWithText(
            "Acepto de forma separada el tratamiento de mis datos sensibles de seguimiento (versión 2026-10-27).",
        ).assertDoesNotExist()
    }
}
