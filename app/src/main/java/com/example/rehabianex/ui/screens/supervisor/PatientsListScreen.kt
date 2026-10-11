package com.example.rehabianex.ui.screens.supervisor

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rehabianex.data.model.SupervisorPatientSummary
import com.example.rehabianex.data.model.SupervisorPatientAccessState
import com.example.rehabianex.navigation.Routes
import com.example.rehabianex.ui.components.ChipSelector
import com.example.rehabianex.ui.components.InputField
import com.example.rehabianex.ui.components.RehabianexButton
import com.example.rehabianex.ui.components.ScreenHeader
import com.example.rehabianex.ui.components.SupervisorScreenScaffold
import com.example.rehabianex.ui.theme.RehabianexColors

@Composable
fun PatientsListScreen(
    onNavigate: (String) -> Unit,
    uiState: SupervisorPatientsUiState,
    actionState: RequestActionUiState,
    onRetry: () -> Unit,
    onRespondRequest: (String, Boolean) -> Unit,
    onUnlinkPatient: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Todos") }
    var patientToUnlinkUid by rememberSaveable { mutableStateOf<String?>(null) }
    var patientToUnlinkName by rememberSaveable { mutableStateOf("") }

    val limitedAccess = (uiState as? SupervisorPatientsUiState.Content)
        ?.data
        ?.authorized == false
    SupervisorScreenScaffold(
        currentRoute = Routes.PatientsList,
        onNavigate = onNavigate,
        limitedAccess = limitedAccess,
    ) {
        when (uiState) {
            SupervisorPatientsUiState.Loading -> SupervisorLoadingCard()
            is SupervisorPatientsUiState.Error -> SupervisorErrorCard(
                message = uiState.message,
                onRetry = onRetry,
            )

            is SupervisorPatientsUiState.Content -> {
                val data = uiState.data
                if (!data.authorized) {
                    ScreenHeader(
                        "Acceso limitado",
                        "La gestión de pacientes no está disponible todavía.",
                    )
                    SafeCacheStatus(uiState.isRefreshing, uiState.lastSyncedAt, uiState.errorMessage)
                    Spacer(Modifier.height(12.dp))
                    SupervisorAuthNotice(authorized = false)
                    Spacer(Modifier.height(14.dp))
                    RehabianexButton("Volver al inicio") {
                        onNavigate(Routes.SupervisorHome)
                    }
                    return@SupervisorScreenScaffold
                }
                val visiblePatients = data.patients.filteredBy(query, filter)

                ScreenHeader(
                    "Pacientes",
                    if (data.authorized) {
                        "Usuarios vinculados o en pausa"
                    } else {
                        "Acceso pendiente de autorización"
                    },
                )
                SafeCacheStatus(uiState.isRefreshing, uiState.lastSyncedAt, uiState.errorMessage)
                Spacer(Modifier.height(12.dp))
                SupervisorAuthNotice(
                    authorized = data.authorized,
                )
                Spacer(Modifier.height(12.dp))
                RequestActionMessage(actionState)
                Spacer(Modifier.height(8.dp))
                PendingRequestsSection(
                    pendingRequests = data.pendingRequests,
                    actionState = actionState,
                    onRespondRequest = onRespondRequest,
                )
                InputField(
                    value = query,
                    onValueChange = { query = it.take(100) },
                    label = "Buscar paciente",
                )
                Spacer(Modifier.height(8.dp))
                ChipSelector(
                    options = listOf(
                        "Todos",
                        "Activos",
                        "En pausa",
                        "Sin acceso",
                        "Desvinculados",
                    ),
                    selected = filter,
                    onSelected = { filter = it },
                )
                Spacer(Modifier.height(12.dp))
                if (visiblePatients.isEmpty()) {
                    EmptySupervisorCard(
                        title = "No hay pacientes para mostrar.",
                        description = if (!data.authorized) {
                            "La gestión de pacientes estará disponible cuando tu cuenta sea autorizada."
                        } else if (data.patients.isEmpty()) {
                            "Cuando aceptes solicitudes con consentimiento vigente aparecerán aquí."
                        } else {
                            "No hay coincidencias con el filtro actual."
                        },
                    )
                } else {
                    visiblePatients.forEach { patient ->
                        SupervisorPatientCard(
                            patient = patient,
                            onNavigate = onNavigate,
                            buttonText = "Abrir",
                            onUnlink = {
                                patientToUnlinkUid = patient.uid
                                patientToUnlinkName = patient.name
                            },
                            unlinkEnabled = actionState !is RequestActionUiState.Loading,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }

        patientToUnlinkUid?.let { patientUid ->
            AlertDialog(
                onDismissRequest = { patientToUnlinkUid = null },
                title = { Text("Desvincular paciente") },
                text = {
                    Text(
                        "$patientToUnlinkName dejará de aparecer en tu seguimiento. " +
                            "Puedes volver a vincularlo si envía una nueva solicitud.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            patientToUnlinkUid = null
                            onUnlinkPatient(patientUid)
                        },
                    ) {
                        Text("Desvincular", color = RehabianexColors.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { patientToUnlinkUid = null }) {
                        Text("Cancelar")
                    }
                },
            )
        }
    }
}

private fun List<SupervisorPatientSummary>.filteredBy(
    query: String,
    filter: String,
): List<SupervisorPatientSummary> {
    val cleanQuery = query
        .filterNot { it.isISOControl() }
        .replace("<", "")
        .replace(">", "")
        .trim()
        .lowercase()

    return filter { patient ->
        val matchesQuery = cleanQuery.isBlank() ||
            patient.name.lowercase().contains(cleanQuery)

        val matchesFilter = when (filter) {
            "Activos" -> patient.accessState == SupervisorPatientAccessState.Active
            "En pausa" -> patient.accessState == SupervisorPatientAccessState.Paused
            "Sin acceso" -> patient.accessState == SupervisorPatientAccessState.NoAccess
            "Desvinculados" -> patient.accessState == SupervisorPatientAccessState.Unlinked
            else -> true
        }

        matchesQuery && matchesFilter
    }
}
