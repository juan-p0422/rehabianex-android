package com.example.rehabianex

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.example.rehabianex.data.export.DataExportFileManager
import com.example.rehabianex.data.model.DataControlCategory
import com.example.rehabianex.data.model.DataControlSummary
import com.example.rehabianex.ui.screens.data.DataControlScreen
import com.example.rehabianex.ui.screens.data.DataControlUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.runBlocking

class Stage8DataControlPresentationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun inventoryExplainsLocalRemoteDifferenceAndSensitiveExport() {
        composeRule.setContent { screen() }

        composeRule.onNodeWithText("Inventario de tus datos").assertExists()
        composeRule.onNodeWithText(
            "La caché local es distinta de los datos remotos. Limpiarla no elimina la cuenta ni la información del servidor.",
        ).assertExists()
        composeRule.onNodeWithText(
            "El archivo puede contener datos sensibles. Se guarda temporalmente en el espacio privado de la app y tú eliges con qué aplicación compartirlo.",
        ).performScrollTo().assertExists()
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

        composeRule.onNodeWithText("Iniciar solicitud").performScrollTo().performClick()
        composeRule.onNodeWithText("Solicitar eliminación").assertIsNotEnabled()
        composeRule.onNodeWithText("Contraseña actual").performTextInput("synthetic-password")
        composeRule.onNodeWithText("Frase de confirmación").performTextInput("FRASE INCORRECTA")
        composeRule.onNodeWithText("Solicitar eliminación").assertIsNotEnabled()

        composeRule.runOnIdle { assertFalse(requested) }
    }

    @Test
    fun exportUsesPrivateCacheContentUriWithTemporaryReadGrant() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = DataExportFileManager(context)

        val intent = runBlocking { manager.createPrivateExport("{\"synthetic\":true}") }
        val uri = intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)

        assertEquals("content", uri?.scheme)
        assertTrue(uri?.authority?.endsWith(".fileprovider") == true)
        assertTrue(
            intent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
        )
        manager.clearExports()
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
                            exported = true,
                            deletionMode = "record_or_account_request",
                        ),
                    ),
                    editableFields = listOf("full_name", "phone"),
                    rectificationRequiresConnection = true,
                    automatedAccountDeletion = true,
                    recentAuthMaxAgeSeconds = 300,
                    confirmationPhrase = "ELIMINAR MI CUENTA",
                    retentionNotice = "Algunos datos requieren revisión operativa.",
                    manualRequestNotice = "Contacta soporte para otros registros.",
                ),
            ),
            fileManager = DataExportFileManager(
                InstrumentationRegistry.getInstrumentation().targetContext,
            ),
            onRetry = {},
            onExport = {},
            onExportHandled = {},
            onNavigate = {},
            onBack = {},
            onRequestAccountDeletion = onRequestAccountDeletion,
            onClearDeletionError = {},
            onDeletionCompleted = {},
        )
    }
}
