package com.example.rehabianex.ui.screens.supervisor

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rehabianex.data.model.SupervisorPatientSummary
import com.example.rehabianex.data.model.SupervisorPatientAccessState
import com.example.rehabianex.data.model.SupervisorPendingRequest
import com.example.rehabianex.data.model.SupervisorRequestKind
import com.example.rehabianex.navigation.Routes
import com.example.rehabianex.ui.components.RehabianexButton
import com.example.rehabianex.ui.components.RehabianexCard
import com.example.rehabianex.ui.components.RehabianexDangerButton
import com.example.rehabianex.ui.components.RehabianexOutlineButton
import com.example.rehabianex.ui.components.SoftCard
import com.example.rehabianex.ui.components.WarningCard
import com.example.rehabianex.ui.theme.RehabianexColors

@Composable
internal fun SupervisorLoadingCard(message: String = "Cargando datos reales...") {
    RehabianexCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text(message)
        }
    }
}

@Composable
internal fun SupervisorErrorCard(
    message: String,
    onRetry: () -> Unit,
) {
    WarningCard {
        Text(message, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        RehabianexOutlineButton("Reintentar", onClick = onRetry)
    }
}

@Composable
internal fun SupervisorAuthNotice(
    authorized: Boolean,
) {
    if (authorized) {
        return
    }

    WarningCard {
        Text("Autorización pendiente", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Tu cuenta de supervisor está pendiente de autorización. " +
                "Un administrador revisará tu información antes de habilitar el acceso " +
                "a pacientes, solicitudes y Chat IA.",
        )
    }
}

@Composable
@Suppress("DEPRECATION")
internal fun SupervisorCodeCard(
    code: String,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    RehabianexCard(modifier = modifier) {
        Text("Código de vinculación", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            text = code,
            style = MaterialTheme.typography.headlineSmall,
            color = RehabianexColors.GreenDark,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "El paciente puede pegar este código en Vinculación con supervisor.",
            color = RehabianexColors.GrayText,
        )
        Spacer(Modifier.height(10.dp))
        RehabianexButton("Copiar código") {
            clipboardManager.setText(AnnotatedString(code))
            Toast.makeText(context, "Código copiado al portapapeles.", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
internal fun RequestActionMessage(actionState: RequestActionUiState) {
    when (actionState) {
        RequestActionUiState.Idle -> Unit
        RequestActionUiState.Loading -> {
            SoftCard {
                Text("Procesando solicitud...")
            }
        }

        is RequestActionUiState.Success -> {
            SoftCard {
                Text(actionState.message, fontWeight = FontWeight.Bold)
            }
        }

        is RequestActionUiState.Error -> {
            WarningCard {
                Text(actionState.message, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun PendingRequestsSection(
    pendingRequests: List<SupervisorPendingRequest>,
    actionState: RequestActionUiState,
    onRespondRequest: (String, Boolean) -> Unit,
) {
    if (pendingRequests.isEmpty()) return

    Text("Solicitudes pendientes", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    pendingRequests.forEach { request ->
        RehabianexCard {
            Text(
                if (request.kind == SupervisorRequestKind.Unlink) {
                    "Solicitud de desvinculación"
                } else {
                    "Solicitud de vinculación"
                },
                color = RehabianexColors.GreenDark,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(request.patientName, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(request.message, color = RehabianexColors.GrayText)
            if (request.requestedAt.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text("Solicitud: ${request.requestedAt}", color = RehabianexColors.GrayText)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RehabianexOutlineButton(
                    text = if (request.kind == SupervisorRequestKind.Unlink) "Mantener vínculo" else "Rechazar",
                    modifier = Modifier.weight(1f),
                    enabled = actionState !is RequestActionUiState.Loading,
                    onClick = { onRespondRequest(request.id, false) },
                )
                RehabianexButton(
                    text = if (request.kind == SupervisorRequestKind.Unlink) "Aceptar salida" else "Aceptar",
                    modifier = Modifier.weight(1f),
                    enabled = actionState !is RequestActionUiState.Loading,
                    onClick = { onRespondRequest(request.id, true) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
internal fun SupervisorPatientCard(
    patient: SupervisorPatientSummary,
    onNavigate: (String) -> Unit,
    buttonText: String = "Abrir",
    onUnlink: (() -> Unit)? = null,
    unlinkEnabled: Boolean = true,
) {
    RehabianexCard {
        Text(patient.name, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        when (patient.accessState) {
            SupervisorPatientAccessState.Active -> {
                Text(
                    "Supervisión activa",
                    color = RehabianexColors.GreenDark,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Último registro: ${patient.latestDateText} · ${patient.latestStatus}",
                    color = RehabianexColors.GrayText,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Ansiedad ${patient.anxietyText} · Craving ${patient.cravingText} · Notas ${patient.notesCount}",
                    color = RehabianexColors.GrayText,
                )
            }
            SupervisorPatientAccessState.Paused -> RestrictedAccessText(
                title = "Acceso pausado",
                description = "El paciente conserva la vinculación, pero sus datos están privados.",
            )
            SupervisorPatientAccessState.NoAccess -> RestrictedAccessText(
                title = "Sin acceso",
                description = "El consentimiento fue revocado. No se muestran datos ni métricas.",
            )
            SupervisorPatientAccessState.Unlinked -> RestrictedAccessText(
                title = "Desvinculado",
                description = "La relación terminó y el acceso a la información fue retirado.",
            )
        }
        Spacer(Modifier.height(10.dp))
        RehabianexButton(
            text = if (patient.canOpenDetails) buttonText else patient.accessState.buttonLabel(),
            enabled = patient.canOpenDetails,
        ) {
            if (patient.canOpenDetails) {
                onNavigate(Routes.patientDetail(patient.uid))
            }
        }
        if (onUnlink != null) {
            Spacer(Modifier.height(8.dp))
            RehabianexDangerButton(
                text = "Desvincular",
                enabled = unlinkEnabled &&
                    patient.accessState != SupervisorPatientAccessState.Unlinked,
                onClick = onUnlink,
            )
        }
    }
}

@Composable
private fun RestrictedAccessText(
    title: String,
    description: String,
) {
    Text(
        title,
        color = RehabianexColors.GreenDark,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(4.dp))
    Text(description, color = RehabianexColors.GrayText)
}

private fun SupervisorPatientAccessState.buttonLabel(): String {
    return when (this) {
        SupervisorPatientAccessState.Active -> "Abrir"
        SupervisorPatientAccessState.Paused -> "Acceso pausado"
        SupervisorPatientAccessState.NoAccess -> "Sin acceso"
        SupervisorPatientAccessState.Unlinked -> "Desvinculado"
    }
}

@Composable
internal fun EmptySupervisorCard(
    title: String,
    description: String,
) {
    RehabianexCard {
        Text(title, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text(description, color = RehabianexColors.GrayText)
    }
}

@Composable
internal fun DetailValueRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = RehabianexColors.GrayText)
        Text(value, fontWeight = FontWeight.Bold)
    }
}
