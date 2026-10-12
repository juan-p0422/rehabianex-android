package com.example.rehabianex

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.rehabianex.data.model.PatientProfileData
import com.example.rehabianex.ui.screens.patient.FormSaveUiState
import com.example.rehabianex.ui.screens.patient.PatientProfileScreen
import com.example.rehabianex.ui.screens.patient.PatientProfileUiState
import org.junit.Rule
import org.junit.Test

class PatientPrivacyPresentationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hiddenIdentityIsExplainedAsVisualPrivacyNotGuaranteedAnonymity() {
        composeRule.setContent {
            PatientProfileScreen(
                onNavigate = {},
                onLogout = {},
                uiState = PatientProfileUiState.Content(
                    profile = PatientProfileData(
                        initials = "PA",
                        fullName = "Paciente anónimo",
                        registeredName = "Nombre sintético",
                        nickname = null,
                        email = "sintetico@example.test",
                        phone = "+52 5555555555",
                        supervisorText = "Sin supervisor vinculado",
                        supervisorUid = "",
                        consentId = null,
                        consentActive = false,
                        privacyText = "Identidad oculta en vistas compatibles",
                        isAnonymous = true,
                        privacyMode = true,
                    ),
                ),
                consentActionState = FormSaveUiState.Idle,
                privacyActionState = FormSaveUiState.Idle,
                profileActionState = FormSaveUiState.Idle,
                onRetry = {},
                onSetConsent = { _, _ -> },
                onRevokeConsent = {},
                onRequestSupervisorUnlink = {},
                onCancelPendingSupervisorRequest = {},
                onClearConsentMessage = {},
                onSavePrivacy = {},
                onClearPrivacyMessage = {},
                onSaveProfile = {},
                onClearProfileMessage = {},
            )
        }

        composeRule.onNodeWithText("Identidad oculta en vistas compatibles").fetchSemanticsNode()
        composeRule.onNodeWithText("Configurar privacidad").performClick()
        composeRule.onNodeWithText("Ocultar identidad").fetchSemanticsNode()
        composeRule.onNodeWithText(
            "Tu cuenta y tus datos siguen vinculados a tu identidad técnica; esta opción no garantiza anonimato.",
        ).fetchSemanticsNode()
    }
}
