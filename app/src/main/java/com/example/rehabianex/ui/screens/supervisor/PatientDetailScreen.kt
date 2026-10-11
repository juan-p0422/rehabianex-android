package com.example.rehabianex.ui.screens.supervisor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rehabianex.navigation.Routes
import com.example.rehabianex.ui.components.LabelValueCard
import com.example.rehabianex.ui.components.MetricCard
import com.example.rehabianex.ui.components.RehabianexButton
import com.example.rehabianex.ui.components.RehabianexCard
import com.example.rehabianex.ui.components.ScreenHeader
import com.example.rehabianex.ui.components.SoftCard
import com.example.rehabianex.ui.components.SupervisorScreenScaffold
import com.example.rehabianex.ui.theme.RehabianexColors

@Composable
fun PatientDetailScreen(
    onNavigate: (String) -> Unit,
    uiState: SupervisorPatientDetailUiState?,
    onRetry: () -> Unit,
) {
    SupervisorScreenScaffold(Routes.PatientDetail, onNavigate) {
        when (uiState) {
            null -> {
                ScreenHeader("Detalle de paciente", "Selecciona un paciente")
                Spacer(Modifier.height(12.dp))
                EmptySupervisorCard(
                    title = "No hay paciente seleccionado.",
                    description = "Abre un paciente desde la lista para consultar su información autorizada.",
                )
                Spacer(Modifier.height(12.dp))
                RehabianexButton("Ir a pacientes") {
                    onNavigate(Routes.PatientsList)
                }
            }

            SupervisorPatientDetailUiState.Loading -> SupervisorLoadingCard()
            is SupervisorPatientDetailUiState.Error -> SupervisorErrorCard(
                message = uiState.message,
                onRetry = onRetry,
            )

            is SupervisorPatientDetailUiState.Content -> {
                val detail = uiState.data
                val patient = detail.patient

                ScreenHeader(patient.name, "Seguimiento autorizado")
                SafeCacheStatus(uiState.isRefreshing, uiState.lastSyncedAt, uiState.errorMessage)
                Spacer(Modifier.height(12.dp))
                MetricCard("Días sobria", patient.soberDays)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LabelValueCard(
                        label = "Ansiedad",
                        value = patient.anxietyText,
                        modifier = Modifier.weight(1f),
                        background = RehabianexColors.WarningBg,
                    )
                    LabelValueCard(
                        label = "Craving",
                        value = patient.cravingText,
                        modifier = Modifier.weight(1f),
                        background = RehabianexColors.WarningBg,
                    )
                }
                Spacer(Modifier.height(10.dp))
                SoftCard {
                    Text("Estado reciente", fontWeight = FontWeight.Bold)
                    Text("${patient.latestStatus} · ${patient.latestDateText}")
                }
                Spacer(Modifier.height(10.dp))
                RehabianexCard {
                    Text("Resumen autorizado", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    DetailValueRow("Notas disponibles", patient.notesCount.toString())
                    Spacer(Modifier.height(6.dp))
                    DetailValueRow("Recaídas registradas", detail.relapseCount.toString())
                }
                Spacer(Modifier.height(10.dp))
                if (detail.latestNotes.isEmpty()) {
                    EmptySupervisorCard(
                        title = "Sin notas autorizadas.",
                        description = "El paciente aún no tiene registros o el consentimiento no incluye notas clínicas.",
                    )
                } else {
                    Text("Últimas notas emocionales", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    detail.latestNotes.forEach { note ->
                        RehabianexCard {
                            Text(note.title, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(note.detail, color = RehabianexColors.GrayText)
                            Spacer(Modifier.height(4.dp))
                            Text(note.dateText, color = RehabianexColors.GrayText)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}
