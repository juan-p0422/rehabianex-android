package com.example.rehabianex

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.rehabianex.data.model.DataControlCategory
import com.example.rehabianex.data.model.DataControlSummary
import com.example.rehabianex.ui.screens.data.DataControlScreen
import com.example.rehabianex.ui.screens.data.DataControlUiState
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class Stage8DataControlPresentationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun inventoryExplainsLocalRemoteDifferenceAndDoesNotOfferSelfServiceExport() {
        composeRule.setContent { screen() }

        composeRule.onNodeWithText("Inventario de tus datos").assertExists()
        composeRule.onNodeWithText(
            "La caché local es distinta de los datos remotos. Limpiarla no elimina la cuenta ni la información del servidor.",
        ).assertExists()
        composeRule.onNodeWithText("Exportar una copia").assertDoesNotExist()
        composeRule.onNodeWithText("Generar exportación").assertDoesNotExist()
        composeRule.onNodeWithText(
            "Los cambios requieren conexión y solo se confirman después de guardarse en el servidor. No se encolan credenciales ni correo.",
        ).performScrollTo().assertExists()
    }

    @Test
    fun reinforcedDeletionRequiresPasswordExactPhraseAndRetentionAcknowledgement() {
        var requested = false
        composeRule.setContent {
            screen(onRequestAccountDeletion = { _, _, _ -> requested = true })
        }

        composeRule.onNodeWithText("Desactivar mi cuenta").performScrollTo().performClick()
        composeRule.onNodeWithText("Desactivar cuenta").assertIsNotEnabled()
        composeRule.onNodeWithText("Contraseña actual").performTextInput("synthetic-password")
        composeRule.onNodeWithText("Frase de confirmación").performTextInput("FRASE INCORRECTA")
        composeRule.onNodeWithText("Desactivar cuenta").assertIsNotEnabled()

        composeRule.runOnIdle { assertFalse(requested) }
    }

    @Test
    fun definitiveDeletionExplainsManualEmailFlowWithoutClaimingItWasSent() {
        composeRule.setContent { screen() }

        composeRule.onNodeWithText("Solicitar eliminación definitiva")
            .performScrollTo()
            .assertExists()
        composeRule.onNodeWithText("1. Escribe a rehabianex@gmail.com.").assertExists()
        composeRule.onNodeWithText("Abrir aplicación de correo").assertExists()
        composeRule.onNodeWithText(
            "No envíes contraseñas, tokens, notas emocionales, historial de consumo ni datos de terceras personas. Abrir el correo no significa que la solicitud haya sido enviada.",
        ).assertExists()
    }

    @Composable
    private fun screen(
        onRequestAccountDeletion: (String, String, Boolean) -> Unit = { _, _, _ -> },
    ) {
        DataControlScreen(
            uiState = DataControlUiState(
                loading = false,
                summary = DataControlSummary(
                    role = "patient",
                    deletionStatus = "none",
                    categories = listOf(
                        DataControlCategory(
                            code = "patient_notes",
                            label = "Registros autorreportados",
                            location = "firestore_and_encrypted_room",
                            deletionMode = "record_or_account_request",
                        ),
                    ),
                    editableFields = listOf("full_name", "phone"),
                    rectificationRequiresConnection = true,
                    automatedAccountDeletion = true,
                    recentAuthMaxAgeSeconds = 300,
                    confirmationPhrase = "DESACTIVAR MI CUENTA",
                    retentionNotice = "Algunos datos requieren revisión operativa.",
                    manualRequestNotice = "Solicita atención al 33 2535 6657 para otros registros.",
                    permanentDeletionEmail = "rehabianex@gmail.com",
                    permanentDeletionSubject = "Solicitud de eliminación de cuenta RehabiAnex",
                ),
            ),
            onRetry = {},
            onNavigate = {},
            onBack = {},
            onRequestAccountDeletion = onRequestAccountDeletion,
            onClearDeletionError = {},
            onDeletionCompleted = {},
        )
    }
}
