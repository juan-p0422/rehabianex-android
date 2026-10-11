package com.example.rehabianex.ui.screens.patient

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.rehabianex.ui.components.BackLink
import com.example.rehabianex.ui.components.ChipSelector
import com.example.rehabianex.ui.components.DangerCard
import com.example.rehabianex.ui.components.InputField
import com.example.rehabianex.ui.components.RehabianexButton
import com.example.rehabianex.ui.components.RehabianexCard
import com.example.rehabianex.ui.components.RehabianexOutlineButton
import com.example.rehabianex.ui.components.ScreenHeader
import com.example.rehabianex.ui.components.ScreenScaffold
import com.example.rehabianex.ui.components.SoftCard
import com.example.rehabianex.ui.components.TextAreaField
import com.example.rehabianex.ui.components.TimeSelector12h
import com.example.rehabianex.ui.components.formatTime12h
import com.example.rehabianex.ui.theme.RehabianexColors
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun NewEventScreen(
    saveState: FormSaveUiState,
    onSave: (EventFormInput) -> Unit,
    onInputChanged: () -> Unit,
    onBackToAgenda: () -> Unit,
    onCancel: () -> Unit,
) {
    val suggestedStart = LocalDateTime.now().plusHours(1)
    var title by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("Reunión") }
    var date by rememberSaveable { mutableStateOf(suggestedStart.toLocalDate().toString()) }
    var startTime by rememberSaveable {
        mutableStateOf(suggestedStart.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")))
    }
    var duration by rememberSaveable { mutableStateOf("60") }
    var location by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    val isSaving = saveState is FormSaveUiState.Loading
    val fieldErrors = (saveState as? FormSaveUiState.Error)?.fieldErrors.orEmpty()

    fun update(change: () -> Unit) {
        if (!isSaving) {
            change()
            onInputChanged()
        }
    }

    LaunchedEffect(saveState) {
        if (saveState is FormSaveUiState.Success) {
            val nextSuggestedStart = LocalDateTime.now().plusHours(1)
            title = ""
            type = "Reunión"
            date = nextSuggestedStart.toLocalDate().toString()
            startTime = nextSuggestedStart.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            duration = "60"
            location = ""
            notes = ""
        }
    }

    ScreenScaffold {
        BackLink(onCancel)
        ScreenHeader("Nuevo evento", "Agrega una actividad de recuperación")
        Spacer(Modifier.height(12.dp))
        EventSaveStatus(saveState)
        if (saveState !is FormSaveUiState.Idle) {
            Spacer(Modifier.height(10.dp))
        }
        InputField(
            value = title,
            onValueChange = { value -> update { title = value.take(120) } },
            label = "Nombre del evento",
            enabled = !isSaving,
            errorMessage = fieldErrors["title"],
        )
        Spacer(Modifier.height(10.dp))
        RehabianexCard {
            Text("Tipo de evento", fontWeight = FontWeight.Bold)
            ChipSelector(
                options = listOf("Reunión", "Cita", "Grupo", "Llamada"),
                selected = type,
                onSelected = { selected -> update { type = selected } },
            )
            fieldErrors["type"]?.let { message ->
                Spacer(Modifier.height(4.dp))
                Text(message, color = RehabianexColors.Red)
            }
        }
        Spacer(Modifier.height(10.dp))
        InputField(
            value = date,
            onValueChange = { value -> update { date = value.take(10) } },
            label = "Fecha (AAAA-MM-DD)",
            enabled = !isSaving,
            errorMessage = fieldErrors["date"],
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
        Spacer(Modifier.height(10.dp))
        TimeSelector12h(
            value = startTime,
            onValueChange = { value -> update { startTime = value } },
            label = "Hora del evento",
            enabled = !isSaving,
            errorMessage = fieldErrors["startTime"],
        )
        Spacer(Modifier.height(10.dp))
        RehabianexCard {
            Text("Aviso y vigencia", fontWeight = FontWeight.Bold)
            ChipSelector(
                options = listOf("30", "60", "90", "120"),
                selected = duration,
                onSelected = { selected -> update { duration = selected } },
            )
            Text("Minutos antes y después del evento", color = RehabianexColors.GrayText)
            fieldErrors["duration"]?.let { message ->
                Spacer(Modifier.height(4.dp))
                Text(message, color = RehabianexColors.Red)
            }
        }
        Spacer(Modifier.height(10.dp))
        InputField(
            value = location,
            onValueChange = { value -> update { location = value.take(120) } },
            label = "Ubicación o medio",
            enabled = !isSaving,
            errorMessage = fieldErrors["location"],
        )
        Spacer(Modifier.height(10.dp))
        TextAreaField(
            value = notes,
            onValueChange = { value -> update { notes = value.take(240) } },
            label = "Notas opcionales",
        )
        Spacer(Modifier.height(10.dp))
        SoftCard {
            Text("Resumen", fontWeight = FontWeight.Bold)
            Text("$type · $date · ${formatTime12h(startTime)}")
            Text("Avisar $duration min antes y limpiar $duration min después")
            Text(
                location.ifBlank { "Agrega una ubicación o medio de contacto." },
                color = RehabianexColors.GrayText,
            )
        }
        Spacer(Modifier.height(14.dp))
        RehabianexButton(
            text = if (isSaving) "Guardando..." else "Guardar evento",
            enabled = !isSaving,
        ) {
            onSave(
                EventFormInput(
                    title = title,
                    type = type,
                    date = date,
                    startTime = startTime,
                    durationMinutes = duration,
                    location = location,
                    notes = notes,
                ),
            )
        }
        if (saveState is FormSaveUiState.Success) {
            Spacer(Modifier.height(8.dp))
            RehabianexOutlineButton("Ver agenda actualizada", onClick = onBackToAgenda)
        }
        Spacer(Modifier.height(8.dp))
        RehabianexOutlineButton("Cancelar", enabled = !isSaving, onClick = onCancel)
    }
}

@Composable
private fun EventSaveStatus(saveState: FormSaveUiState) {
    when (saveState) {
        FormSaveUiState.Idle -> Unit
        FormSaveUiState.Loading -> RehabianexCard {
            CircularProgressIndicator(color = RehabianexColors.GreenMain)
            Spacer(Modifier.height(8.dp))
            Text("Guardando evento...")
        }
        is FormSaveUiState.Success -> SoftCard {
            Text("Evento guardado", fontWeight = FontWeight.Bold)
            Text(saveState.message)
        }
        is FormSaveUiState.Error -> DangerCard {
            Text("No se pudo guardar", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(saveState.message)
        }
    }
}
