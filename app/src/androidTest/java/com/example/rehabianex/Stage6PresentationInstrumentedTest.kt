package com.example.rehabianex

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.rehabianex.data.model.LocalResource
import com.example.rehabianex.ui.screens.patient.FormSaveUiState
import com.example.rehabianex.ui.screens.patient.LocalDirectoryScreen
import com.example.rehabianex.ui.screens.patient.LocalResourcesUiState
import com.example.rehabianex.ui.screens.patient.NewEventScreen
import com.example.rehabianex.ui.screens.patient.SupportContactsUiState
import com.example.rehabianex.ui.screens.patient.SupportNetworkScreen
import org.junit.Rule
import org.junit.Test

class Stage6PresentationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun agendaUsesCalendarInsteadOfManualDateInput() {
        composeRule.setContent {
            NewEventScreen(
                saveState = FormSaveUiState.Idle,
                onSave = {},
                onInputChanged = {},
                onBackToAgenda = {},
                onCancel = {},
            )
        }

        composeRule.onNodeWithText("Seleccionar fecha en calendario").performClick()
        composeRule.onNodeWithText("Selecciona la fecha del evento").assertExists()
    }

    @Test
    fun urgentSupportIsUnavailableWithoutRecipientAndOffersConfiguration() {
        composeRule.setContent {
            SupportNetworkScreen(
                onNavigate = {},
                uiState = SupportContactsUiState.Content(emptyList()),
                saveState = FormSaveUiState.Idle,
                onRetry = {},
                onSaveContact = {},
                onInputChanged = {},
            )
        }

        composeRule.onNodeWithText("Necesito apoyo ahora")
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithText("Configurar contacto de apoyo").assertExists()
    }

    @Test
    fun directoryLabelsMissingMetadataAndExplainsInformativeReview() {
        composeRule.setContent {
            LocalDirectoryScreen(
                onNavigate = {},
                uiState = LocalResourcesUiState.Content(
                    resources = listOf(
                        LocalResource(
                            id = "synthetic",
                            name = "Recurso sintético",
                            type = "resource",
                            typeLabel = "Recurso",
                            city = "",
                            detail = "",
                            phone = "",
                            sourceName = "",
                            sourceUrl = "",
                            reviewedAt = "",
                            updatedAt = "",
                        ),
                    ),
                ),
                onRetry = {},
            )
        }

        composeRule.onNodeWithText("Fuente: No disponible").performScrollTo().assertExists()
        composeRule.onNodeWithText("Fecha de revisión: No disponible").assertExists()
        composeRule.onNodeWithText("Revisión informativa").performScrollTo().assertExists()
    }
}
