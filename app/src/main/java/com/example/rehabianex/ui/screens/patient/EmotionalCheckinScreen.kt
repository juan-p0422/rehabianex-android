package com.example.rehabianex.ui.screens.patient

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rehabianex.navigation.Routes
import com.example.rehabianex.ui.components.ChipSelector
import com.example.rehabianex.ui.components.DangerCard
import com.example.rehabianex.ui.components.MultiChipSelector
import com.example.rehabianex.ui.components.PatientScreenScaffold
import com.example.rehabianex.ui.components.RehabianexButton
import com.example.rehabianex.ui.components.RehabianexCard
import com.example.rehabianex.ui.components.RehabianexOutlineButton
import com.example.rehabianex.ui.components.ScreenHeader
import com.example.rehabianex.ui.components.SoftCard
import com.example.rehabianex.ui.components.TextAreaField
import com.example.rehabianex.ui.theme.RehabianexColors

@Composable
fun EmotionalCheckinScreen(
    onNavigate: (String) -> Unit,
    saveState: DailyNoteSaveUiState,
    onSave: (DailyNoteFormInput) -> Unit,
    onInputChanged: () -> Unit,
) {
    var mood by rememberSaveable { mutableStateOf("Neutral") }
    var anxiety by rememberSaveable { mutableStateOf("3") }
    var craving by rememberSaveable { mutableStateOf("2") }
    var energy by rememberSaveable { mutableStateOf("3") }
    var sleepQuality by rememberSaveable { mutableStateOf("3") }
    var sober by rememberSaveable { mutableStateOf("Sí") }
    var triggers by rememberSaveable { mutableStateOf(setOf<String>()) }
    var copingActions by rememberSaveable { mutableStateOf(setOf<String>()) }
    var note by rememberSaveable { mutableStateOf("") }
    val isSaving = saveState is DailyNoteSaveUiState.Loading

    fun updateForm(change: () -> Unit) {
        if (!isSaving) {
            change()
            onInputChanged()
        }
    }

    PatientScreenScaffold(
        currentRoute = Routes.EmotionalCheckin,
        onNavigate = onNavigate,
    ) {
        ScreenHeader("Registro emocional", "Chequeo diario de recuperación")
        Spacer(Modifier.height(12.dp))
        RehabianexCard {
            Text("Estado de ánimo", fontWeight = FontWeight.Bold)
            ChipSelector(
                listOf("Muy bajo", "Bajo", "Neutral", "Bueno"),
                mood,
                { selected -> updateForm { mood = selected } },
            )
        }
        Spacer(Modifier.height(10.dp))
        RehabianexCard {
            Text("Ansiedad", fontWeight = FontWeight.Bold)
            ChipSelector(
                options = (0..5).map(Int::toString),
                selected = anxiety,
                onSelected = { selected -> updateForm { anxiety = selected } },
            )
        }
        Spacer(Modifier.height(10.dp))
        RehabianexCard {
            Text("Craving", fontWeight = FontWeight.Bold)
            ChipSelector(
                options = (0..5).map(Int::toString),
                selected = craving,
                onSelected = { selected -> updateForm { craving = selected } },
            )
        }
        Spacer(Modifier.height(10.dp))
        RehabianexCard {
            Text("Energía", fontWeight = FontWeight.Bold)
            ChipSelector(
                options = (0..5).map(Int::toString),
                selected = energy,
                onSelected = { selected -> updateForm { energy = selected } },
            )
        }
        Spacer(Modifier.height(10.dp))
        RehabianexCard {
            Text("¿Cómo dormiste?", fontWeight = FontWeight.Bold)
            ChipSelector(
                options = (0..5).map(Int::toString),
                selected = sleepQuality,
                onSelected = { selected -> updateForm { sleepQuality = selected } },
            )
        }
        Spacer(Modifier.height(10.dp))
        RehabianexCard {
            Text("¿Mantuviste tu sobriedad hoy?", fontWeight = FontWeight.Bold)
            ChipSelector(
                options = listOf("Sí", "No"),
                selected = sober,
                onSelected = { selected -> updateForm { sober = selected } },
            )
        }
        Spacer(Modifier.height(10.dp))
        RehabianexCard {
            Text("Detonantes", fontWeight = FontWeight.Bold)
            MultiChipSelector(
                options = listOf("Estrés", "Soledad", "Presión social", "Tristeza"),
                selected = triggers,
                onToggle = { selected ->
                    updateForm {
                        triggers = if (selected in triggers) {
                            triggers - selected
                        } else {
                            triggers + selected
                        }
                    }
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        RehabianexCard {
            Text("Acciones de afrontamiento", fontWeight = FontWeight.Bold)
            MultiChipSelector(
                options = listOf("Respiración", "Llamar apoyo", "Caminar", "Reunión"),
                selected = copingActions,
                onToggle = { selected ->
                    updateForm {
                        copingActions = if (selected in copingActions) {
                            copingActions - selected
                        } else {
                            copingActions + selected
                        }
                    }
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        TextAreaField(
            value = note,
            onValueChange = { value ->
                updateForm { note = value.take(1000) }
            },
            label = "Nota libre del día...",
        )
        Spacer(Modifier.height(12.dp))
        SaveStatusCard(saveState)
        Spacer(Modifier.height(14.dp))
        RehabianexButton(
            text = if (isSaving) "Guardando..." else "Guardar registro",
            enabled = !isSaving,
        ) {
            onSave(
                DailyNoteFormInput(
                    mood = mood,
                    anxiety = anxiety,
                    craving = craving,
                    energy = energy,
                    sleepQuality = sleepQuality,
                    sober = sober,
                    triggers = triggers,
                    copingActions = copingActions,
                    note = note,
                ),
            )
        }
        if (saveState is DailyNoteSaveUiState.Success) {
            Spacer(Modifier.height(8.dp))
            RehabianexOutlineButton("Volver al inicio") {
                onNavigate(Routes.PatientHome)
            }
        }
    }
}

@Composable
private fun SaveStatusCard(saveState: DailyNoteSaveUiState) {
    when (saveState) {
        DailyNoteSaveUiState.Idle -> Unit
        DailyNoteSaveUiState.Loading -> RehabianexCard {
            CircularProgressIndicator(color = RehabianexColors.GreenMain)
            Spacer(Modifier.height(8.dp))
            Text("Subiendo tu registro...")
        }
        is DailyNoteSaveUiState.Success -> SoftCard {
            Text("Registro guardado", fontWeight = FontWeight.Bold)
            Text(saveState.message)
        }
        is DailyNoteSaveUiState.Error -> DangerCard {
            Text("No se pudo guardar", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(saveState.message)
            saveState.fieldErrors.values
                .distinct()
                .filter { it != saveState.message }
                .forEach { fieldMessage ->
                    Spacer(Modifier.height(2.dp))
                    Text("• $fieldMessage")
                }
        }
    }
}
