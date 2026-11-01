package com.example.rehabianex.ui.screens.data

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rehabianex.data.model.DataControlCategory
import com.example.rehabianex.navigation.Routes
import com.example.rehabianex.ui.components.InputField
import com.example.rehabianex.ui.components.RehabianexButton
import com.example.rehabianex.ui.components.RehabianexCard
import com.example.rehabianex.ui.components.RehabianexDangerButton
import com.example.rehabianex.ui.components.RehabianexOutlineButton
import com.example.rehabianex.ui.components.ScreenHeader
import com.example.rehabianex.ui.components.ScreenScaffold
import com.example.rehabianex.ui.components.SoftCard
import com.example.rehabianex.ui.components.WarningCard
import com.example.rehabianex.ui.theme.RehabianexColors

@Composable
fun DataControlScreen(
    uiState: DataControlUiState,
    onRetry: () -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onRequestAccountDeletion: (String, String, Boolean) -> Unit,
    onClearDeletionError: () -> Unit,
    onDeletionCompleted: (String) -> Unit,
) {
    val summary = uiState.summary
    var showDeletionDialog by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold {
        Column {
            ScreenHeader(
                title = "Control de datos",
                subtitle = "Consulta, rectificación y solicitudes de eliminación",
            )
            Spacer(Modifier.height(12.dp))
            RehabianexOutlineButton("Volver", onClick = onBack)
            Spacer(Modifier.height(12.dp))

            when {
                uiState.loading && summary == null -> RehabianexCard {
                    CircularProgressIndicator(color = RehabianexColors.GreenMain)
                    Spacer(Modifier.height(8.dp))
                    Text("Consultando el alcance de tus datos...")
                }
                summary == null -> WarningCard {
                    Text(uiState.errorMessage ?: "No fue posible consultar el control de datos.")
                    Spacer(Modifier.height(8.dp))
                    RehabianexButton("Reintentar", onClick = onRetry)
                }
                else -> {
                    DataInventory(summary.categories)
                    Spacer(Modifier.height(12.dp))
                    RectificationSection(
                        role = summary.role,
                        editableFields = summary.editableFields,
                        requiresConnection = summary.rectificationRequiresConnection,
                        onNavigate = onNavigate,
                    )
                    Spacer(Modifier.height(12.dp))
                    RecordDeletionSection(summary.manualRequestNotice)
                    Spacer(Modifier.height(12.dp))
                    AccountDeletionSection(
                        automated = summary.automatedAccountDeletion,
                        retentionNotice = summary.retentionNotice,
                        manualNotice = summary.manualRequestNotice,
                        onRequest = { showDeletionDialog = true },
                    )
                    Spacer(Modifier.height(12.dp))
                    DefinitiveDeletionSection(
                        email = summary.permanentDeletionEmail,
                        subject = summary.permanentDeletionSubject,
                    )
                }
            }
        }
    }

    if (showDeletionDialog && summary != null) {
        AccountDeletionDialog(
            confirmationPhrase = summary.confirmationPhrase,
            state = uiState.deletionState,
            onDismiss = {
                showDeletionDialog = false
                onClearDeletionError()
            },
            onConfirm = onRequestAccountDeletion,
        )
    }

    (uiState.deletionState as? AccountDeletionUiState.Success)?.let { success ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Cuenta desactivada") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Folio: ${success.receipt.requestId}", fontWeight = FontWeight.Bold)
                    Text("El servidor confirmó el bloqueo de acceso y de nuevas sincronizaciones.")
                    Text(success.receipt.retentionNotice)
                    if (success.receipt.manualAttentionRequired) {
                        Text("La solicitud requiere seguimiento manual.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onDeletionCompleted(success.receipt.requestId) }) {
                    Text("Entendido")
                }
            },
        )
    }
}

@Composable
private fun DataInventory(categories: List<DataControlCategory>) {
    RehabianexCard {
        Text("Inventario de tus datos", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        if (categories.isEmpty()) {
            Text("El servidor no informó categorías.", color = RehabianexColors.GrayText)
        } else {
            categories.forEach { category ->
                Text(category.label, fontWeight = FontWeight.Bold)
                Text(
                    "Ubicación: ${category.location.locationLabel()} · " +
                        "Tratamiento: ${category.deletionMode.deletionModeLabel()}",
                    color = RehabianexColors.GrayText,
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        Text(
            "La caché local es distinta de los datos remotos. Limpiarla no elimina la cuenta ni la información del servidor.",
            color = RehabianexColors.GrayText,
        )
    }
}

@Composable
private fun RectificationSection(
    role: String,
    editableFields: List<String>,
    requiresConnection: Boolean,
    onNavigate: (String) -> Unit,
) {
    RehabianexCard {
        Text("Rectificar información", fontWeight = FontWeight.Bold)
        val fields = editableFields.map(::editableFieldLabel).distinct()
        Text(
            if (fields.isEmpty()) "No hay campos editables desde la aplicación para este rol."
            else "Campos disponibles: ${fields.joinToString()}.",
        )
        Text(
            if (requiresConnection) {
                "Los cambios requieren conexión y solo se confirman después de guardarse en el servidor. No se encolan credenciales ni correo."
            } else {
                "El servidor informará cuándo el cambio remoto quede confirmado."
            },
            color = RehabianexColors.GrayText,
        )
        Spacer(Modifier.height(8.dp))
        when (role) {
            "patient" -> RehabianexOutlineButton("Ir a editar perfil") {
                onNavigate(Routes.PatientProfile)
            }
            "supervisor" -> RehabianexOutlineButton("Ir a editar perfil") {
                onNavigate(Routes.SupervisorProfile)
            }
            else -> Text("La rectificación de esta cuenta requiere atención manual.")
        }
    }
}

@Composable
private fun RecordDeletionSection(manualNotice: String) {
    SoftCard {
        Text("Eliminar registros específicos", fontWeight = FontWeight.Bold)
        Text(
            "Esta acción es distinta de eliminar la cuenta. Usa únicamente la opción de eliminar disponible en cada registro; otros casos requieren atención manual.",
        )
        Text(manualNotice, color = RehabianexColors.GrayText)
    }
}

@Composable
private fun AccountDeletionSection(
    automated: Boolean,
    retentionNotice: String,
    manualNotice: String,
    onRequest: () -> Unit,
) {
    WarningCard {
        Text("Desactivar mi cuenta y solicitar eliminación", fontWeight = FontWeight.Bold)
        Text(
            "Perderás el acceso normal a la cuenta. Tras la confirmación del servidor se bloquearán nuevas sincronizaciones y se limpiarán los datos locales asociados a esta sesión.",
        )
        Text(
            "Esta opción realiza una desactivación y borrado suave; no equivale a una eliminación física inmediata de respaldos, logs o proveedores.",
            color = RehabianexColors.GrayText,
        )
        Text(retentionNotice, color = RehabianexColors.GrayText)
        Spacer(Modifier.height(8.dp))
        if (automated) {
            RehabianexDangerButton("Desactivar mi cuenta", onClick = onRequest)
        } else {
            Text(manualNotice)
        }
    }
}

@Composable
private fun DefinitiveDeletionSection(
    email: String,
    subject: String,
) {
    val context = LocalContext.current
    var launchError by rememberSaveable { mutableStateOf(false) }

    SoftCard {
        Text("Solicitar eliminación definitiva", fontWeight = FontWeight.Bold)
        Text("Esta solicitud se atiende por correo y requiere verificación de identidad por el responsable.")
        Text("1. Escribe a $email.")
        Text("2. Usa el asunto: “$subject”.")
        Text("3. Indica el correo asociado a la cuenta y que solicitas su eliminación definitiva.")
        Text("4. Sigue la verificación de identidad que te indique el responsable.")
        Text("5. Espera la confirmación de que el procedimiento terminó.")
        Text(
            "No envíes contraseñas, tokens, notas emocionales, historial de consumo ni datos de terceras personas. Abrir el correo no significa que la solicitud haya sido enviada.",
            color = RehabianexColors.GrayText,
        )
        Spacer(Modifier.height(8.dp))
        RehabianexOutlineButton("Abrir aplicación de correo") {
            launchError = false
            val intent = Intent(
                Intent.ACTION_SENDTO,
                Uri.fromParts("mailto", email, null),
            ).apply {
                putExtra(Intent.EXTRA_SUBJECT, subject)
            }
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                launchError = true
            } catch (_: SecurityException) {
                launchError = true
            }
        }
        if (launchError) {
            Spacer(Modifier.height(6.dp))
            Text(
                "No se encontró una aplicación de correo. Escribe manualmente a $email.",
                color = RehabianexColors.Red,
            )
        }
    }
}

@Composable
private fun AccountDeletionDialog(
    confirmationPhrase: String,
    state: AccountDeletionUiState,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Boolean) -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    val loading = state is AccountDeletionUiState.Reauthenticating ||
        state is AccountDeletionUiState.Requesting

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        title = { Text("Desactivar mi cuenta y solicitar eliminación") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Perderás el acceso normal. Primero confirmaremos nuevamente tu identidad.")
                InputField(
                    value = password,
                    onValueChange = { if (!loading) password = it.take(4_096) },
                    label = "Contraseña actual",
                    isPassword = true,
                    enabled = !loading,
                    errorMessage = (state as? AccountDeletionUiState.Error)
                        ?.fieldErrors
                        ?.get("password"),
                )
                Text("Escribe exactamente: $confirmationPhrase")
                InputField(
                    value = confirmation,
                    onValueChange = { if (!loading) confirmation = it.take(80) },
                    label = "Frase de confirmación",
                    enabled = !loading,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = acknowledged,
                        enabled = !loading,
                        onCheckedChange = { acknowledged = it },
                    )
                    Text("Comprendo que esta acción desactiva la cuenta y que la eliminación definitiva puede requerir revisión manual.")
                }
                when (state) {
                    AccountDeletionUiState.Reauthenticating -> Text("Confirmando identidad...")
                    AccountDeletionUiState.Requesting -> Text("Desactivando cuenta y bloqueando sincronizaciones...")
                    is AccountDeletionUiState.Error -> Text(state.message, color = RehabianexColors.Red)
                    else -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading && password.isNotBlank() &&
                    confirmation == confirmationPhrase && acknowledged,
                onClick = { onConfirm(password, confirmation, acknowledged) },
            ) {
                Text("Desactivar cuenta")
            }
        },
        dismissButton = {
            TextButton(enabled = !loading, onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

private fun editableFieldLabel(field: String): String = when (field) {
    "full_name" -> "nombre"
    "phone" -> "teléfono"
    "gender" -> "género"
    "age" -> "edad"
    "nickname" -> "apodo"
    "sobriety_start_date" -> "fecha de sobriedad"
    "privacy_mode" -> "privacidad visual"
    "supervisor_type", "supervisor_type_other" -> "tipo de supervisor"
    else -> field.replace('_', ' ')
}

private fun String.locationLabel(): String = when (this) {
    "firebase_authentication" -> "Firebase Authentication"
    "firestore" -> "Firestore"
    "firestore_and_encrypted_room" -> "Firestore y Room cifrado"
    "backend_and_providers" -> "Backend y proveedores"
    else -> this.replace('_', ' ')
}

private fun String.deletionModeLabel(): String = when (this) {
    "record_or_account_request" -> "eliminación del registro o solicitud de cuenta"
    "account_request" -> "solicitud de eliminación de cuenta"
    "manual_review" -> "revisión manual"
    else -> "según el control disponible"
}
