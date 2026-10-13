package com.example.rehabianex.ui.screens.data

import android.content.Intent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.rehabianex.data.export.DataExportFileManager
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
    fileManager: DataExportFileManager,
    onRetry: () -> Unit,
    onExport: () -> Unit,
    onExportHandled: (String?) -> Unit,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onRequestAccountDeletion: (String, String, Boolean) -> Unit,
    onClearDeletionError: () -> Unit,
    onDeletionCompleted: (String) -> Unit,
) {
    val context = LocalContext.current
    val summary = uiState.summary
    var showDeletionDialog by rememberSaveable { mutableStateOf(false) }

    val exportReady = uiState.exportState as? DataExportUiState.Ready
    LaunchedEffect(exportReady?.json) {
        val json = exportReady?.json ?: return@LaunchedEffect
        runCatching {
            val sendIntent = fileManager.createPrivateExport(json)
            context.startActivity(
                Intent.createChooser(sendIntent, "Compartir exportación sensible"),
            )
        }.onSuccess {
            onExportHandled(null)
        }.onFailure {
            onExportHandled("No fue posible abrir una aplicación segura para compartir el archivo.")
        }
    }

    ScreenScaffold {
        Column {
            ScreenHeader(
                title = "Control de datos",
                subtitle = "Consulta, exportación, rectificación y solicitudes de eliminación",
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
                    ExportSection(
                        exportState = uiState.exportState,
                        onExport = onExport,
                    )
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
            title = { Text("Solicitud registrada") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Folio: ${success.receipt.requestId}", fontWeight = FontWeight.Bold)
                    Text("La cuenta y las nuevas sincronizaciones quedaron bloqueadas.")
                    Text(success.receipt.retentionNotice)
                    if (success.receipt.manualAttentionRequired) {
                        Text("La solicitud requiere seguimiento manual.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onDeletionCompleted(success.receipt.requestId) }) {
                    Text("Entendido y cerrar sesión")
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
                        if (category.exported) "Incluido en exportación" else "No incluido por retención operativa",
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
private fun ExportSection(
    exportState: DataExportUiState,
    onExport: () -> Unit,
) {
    RehabianexCard {
        Text("Exportar una copia", fontWeight = FontWeight.Bold)
        Text("Se generará un JSON estructurado con información del titular autenticado.")
        Text(
            "El archivo puede contener datos sensibles. Se guarda temporalmente en el espacio privado de la app y tú eliges con qué aplicación compartirlo.",
            color = RehabianexColors.GrayText,
        )
        Spacer(Modifier.height(8.dp))
        RehabianexButton(
            text = if (exportState is DataExportUiState.Loading) "Preparando exportación..." else "Generar exportación",
            enabled = exportState !is DataExportUiState.Loading,
            onClick = onExport,
        )
        (exportState as? DataExportUiState.Error)?.let {
            Spacer(Modifier.height(6.dp))
            Text(it.message, color = RehabianexColors.Red)
        }
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
        Text("Solicitar eliminación de cuenta", fontWeight = FontWeight.Bold)
        Text(
            "La solicitud bloquea inmediatamente el acceso y nuevas sincronizaciones. No equivale a una purga física instantánea.",
        )
        Text(retentionNotice, color = RehabianexColors.GrayText)
        Spacer(Modifier.height(8.dp))
        if (automated) {
            RehabianexDangerButton("Iniciar solicitud", onClick = onRequest)
        } else {
            Text(manualNotice)
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
        title = { Text("Confirmación reforzada") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Primero confirmaremos nuevamente tu identidad con Firebase.")
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
                    Text("Comprendo que pueden existir límites de conservación y revisión manual.")
                }
                when (state) {
                    AccountDeletionUiState.Reauthenticating -> Text("Confirmando identidad...")
                    AccountDeletionUiState.Requesting -> Text("Bloqueando cuenta y sincronizaciones...")
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
                Text("Solicitar eliminación")
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
