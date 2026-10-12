package com.example.rehabianex

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.rehabianex.data.model.PatientProfileData
import com.example.rehabianex.data.model.PatientSupervisionState
import com.example.rehabianex.ui.screens.patient.FormSaveUiState
import com.example.rehabianex.ui.screens.patient.PatientProfileScreen
import com.example.rehabianex.ui.screens.patient.PatientProfileUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SupervisionConsentPresentationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun activationStartsWithNoScopeAndSubmitsOnlyExplicitSelection() {
        var submittedScopes = emptySet<String>()
        showProfile(
            profile = profile(
                state = PatientSupervisionState.Active,
                consentActive = false,
            ),
            onSetConsent = { enabled, scopes ->
                if (enabled) submittedScopes = scopes
            },
        )

        composeRule.onNodeWithText("Administrar acceso del supervisor").performClick()
        composeRule.onNodeWithContentDescription("Compartir Notas de seguimiento").assertIsOff()
        composeRule.onNodeWithContentDescription("Compartir Teléfono").assertIsOff()
        composeRule.onNodeWithText("Activar consentimiento").assertIsNotEnabled()

        composeRule.onNodeWithContentDescription("Compartir Notas de seguimiento").performClick()
        composeRule.onNodeWithContentDescription("Compartir Notas de seguimiento").assertIsOn()
        composeRule.onNodeWithText("Activar consentimiento")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(setOf("patient_notes"), submittedScopes)
        }
    }

    @Test
    fun pendingLinkCanBeWithdrawnWithConfirmation() {
        var cancelledId: String? = null
        showProfile(
            profile = profile(
                state = PatientSupervisionState.LinkPending,
                supervisorUid = "",
                pendingRequestId = "request-1",
            ),
            onCancelPending = { cancelledId = it },
        )

        composeRule.onNodeWithText("Administrar acceso del supervisor").performClick()
        composeRule.onNodeWithText("Retirar solicitud pendiente").performClick()
        composeRule.onNodeWithText("Retirar solicitud", useUnmergedTree = true).performClick()

        composeRule.runOnIdle { assertEquals("request-1", cancelledId) }
    }

    @Test
    fun revokedConsentDoesNotOfferReactivationAndExplainsHistoricalLimit() {
        showProfile(
            profile = profile(
                state = PatientSupervisionState.Revoked,
                consentActive = false,
                scopes = listOf("patient_notes"),
            ),
        )

        composeRule.onNodeWithText("Administrar acceso del supervisor").performClick()
        composeRule.onNodeWithText(
            "La revocación bloquea consultas posteriores, pero no borra información consultada previamente.",
        ).fetchSemanticsNode()
        composeRule.onNodeWithText("Activar consentimiento").assertDoesNotExist()
        composeRule.onNodeWithText("Reactivar consentimiento").assertDoesNotExist()
    }

    private fun showProfile(
        profile: PatientProfileData,
        onSetConsent: (Boolean, Set<String>) -> Unit = { _, _ -> },
        onCancelPending: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            PatientProfileScreen(
                onNavigate = {},
                onLogout = {},
                uiState = PatientProfileUiState.Content(profile),
                consentActionState = FormSaveUiState.Idle,
                privacyActionState = FormSaveUiState.Idle,
                profileActionState = FormSaveUiState.Idle,
                onRetry = {},
                onSetConsent = onSetConsent,
                onRevokeConsent = {},
                onRequestSupervisorUnlink = {},
                onCancelPendingSupervisorRequest = onCancelPending,
                onClearConsentMessage = {},
                onSavePrivacy = {},
                onClearPrivacyMessage = {},
                onSaveProfile = {},
                onClearProfileMessage = {},
            )
        }
    }

    private fun profile(
        state: PatientSupervisionState,
        supervisorUid: String = "supervisor-1",
        consentActive: Boolean = false,
        scopes: List<String> = emptyList(),
        pendingRequestId: String? = null,
    ) = PatientProfileData(
        initials = "PS",
        fullName = "Paciente Sintético",
        registeredName = "Paciente Sintético",
        nickname = null,
        email = "patient@example.test",
        phone = "+52 5555555555",
        supervisorText = "Supervisora Sintética",
        supervisorType = "Psicología clínica",
        supervisorUid = supervisorUid,
        consentId = "consent-1",
        consentActive = consentActive,
        consentScope = scopes,
        pendingLinkRequestId = pendingRequestId,
        privacyText = "Nombre visible para tu cuenta",
        isAnonymous = false,
        supervisionState = state,
    )
}
