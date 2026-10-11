package com.example.rehabianex.ui.screens.supervisor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.rehabianex.data.model.SupervisorChatMessage
import com.example.rehabianex.core.validation.MAX_AI_CHAT_QUESTION_LENGTH
import com.example.rehabianex.navigation.Routes
import com.example.rehabianex.ui.components.SupervisorBottomNavigationBar
import com.example.rehabianex.ui.theme.RehabianexColors

@Composable
fun AiChatScreen(
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    uiState: SupervisorChatUiState,
    onRetry: () -> Unit,
    onInputChanged: (String) -> Unit,
    onModeChanged: (String) -> Unit,
    onSend: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val data = uiState.data
    val canChat = data?.authorized == true && data.patientsCount > 0
    val messages = data?.messages.orEmpty()
    val assistantIntro = when {
        uiState.loading -> "Validando tus pacientes autorizados..."
        data == null && uiState.errorMessage != null -> uiState.errorMessage
        data?.authorized == false -> "Tu cuenta de supervisor está pendiente de autorización. Un administrador revisará tu información antes de habilitar el Chat IA."
        data?.patientsCount == 0 -> "Aún no tienes pacientes con permiso activo. El chat estará disponible cuando tengas al menos un paciente vinculado."
        else -> "Hola, soy el asistente de RehabiAnex. ¿Qué deseas analizar hoy?"
    }

    Scaffold(
        containerColor = RehabianexColors.GreenVerySoft,
        topBar = {
            ChatHeader(
                onBack = onBack,
                onProfile = { onNavigate(Routes.SupervisorProfile) },
            )
        },
        bottomBar = {
            Column {
                ChatInputBar(
                    value = uiState.question,
                    enabled = canChat && !uiState.sending,
                    isSending = uiState.sending,
                    onValueChange = onInputChanged,
                    onSend = onSend,
                )
                SupervisorBottomNavigationBar(
                    currentRoute = Routes.AiChat,
                    onNavigate = onNavigate,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            if (uiState.isRefreshing) {
                Text("Actualizando permisos...", color = RehabianexColors.GrayText)
                Spacer(Modifier.height(8.dp))
            }
            if (uiState.loading) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(color = RehabianexColors.GreenMain)
                    AssistantBubble(assistantIntro.orEmpty())
                }
            } else {
                AssistantBubble(assistantIntro.orEmpty())
            }
            Spacer(Modifier.height(10.dp))
            MedicalDisclaimer()
            Spacer(Modifier.height(10.dp))
            if (canChat) {
                ChatModeSelector(
                    selectedMode = uiState.mode,
                    enabled = !uiState.sending,
                    onModeChanged = onModeChanged,
                )
                Spacer(Modifier.height(10.dp))
            }
            if (canChat && messages.isEmpty()) {
                SuggestionRow(onInputChanged)
            }
            messages.forEach { message ->
                Spacer(Modifier.height(8.dp))
                ChatMessageBubble(message)
            }
            uiState.errorMessage
                ?.takeIf { it.isNotBlank() && it != assistantIntro }
                ?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    AssistantBubble(error)
                }
            if (uiState.sending) {
                Spacer(Modifier.height(8.dp))
                AssistantBubble("Analizando registros autorizados...")
            }
            if (data == null && uiState.errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                RetryChip(onRetry)
            }
        }
    }
}

@Composable
private fun ChatHeader(
    onBack: () -> Unit,
    onProfile: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = RehabianexColors.GreenMain,
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(text = "Atrás", color = Color.White)
            }
            Text(
                text = "Rehabianex IA",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
            TextButton(onClick = onProfile) {
                Text(text = "Perfil", color = Color.White)
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    enabled: Boolean,
    isSending: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val canSend = enabled &&
        value.trim().length >= 6 &&
        value.length <= MAX_AI_CHAT_QUESTION_LENGTH

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = RehabianexColors.GreenVerySoft,
    ) {
        Row(
            modifier = Modifier
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Escribe tu pregunta") },
                enabled = enabled,
                singleLine = true,
                shape = RoundedCornerShape(2.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (canSend) onSend()
                    },
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    disabledContainerColor = Color.White,
                    focusedBorderColor = RehabianexColors.GreenDark,
                    unfocusedBorderColor = RehabianexColors.GrayText,
                    disabledBorderColor = RehabianexColors.GrayBorder,
                ),
                supportingText = {
                    Text("${value.length}/$MAX_AI_CHAT_QUESTION_LENGTH")
                },
            )
            Button(
                onClick = onSend,
                enabled = canSend,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RehabianexColors.GreenMain,
                    disabledContainerColor = RehabianexColors.GrayBorder,
                    disabledContentColor = RehabianexColors.GrayText,
                ),
            ) {
                Text(if (isSending) "..." else "Enviar")
            }
        }
    }
}

@Composable
private fun AssistantBubble(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(0.78f),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, RehabianexColors.GrayBorder),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            color = RehabianexColors.Black,
        )
    }
}

@Composable
private fun ChatMessageBubble(message: SupervisorChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.82f),
            shape = RoundedCornerShape(16.dp),
            color = if (message.fromUser) {
                RehabianexColors.GreenMain
            } else {
                Color.White
            },
            border = if (message.fromUser) {
                null
            } else {
                BorderStroke(1.dp, RehabianexColors.GrayBorder)
            },
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (!message.fromUser && message.responseMode in setOf("local", "fallback_local")) {
                    Text(
                        text = if (message.responseMode == "fallback_local") {
                            "Respuesta local segura por indisponibilidad temporal del proveedor"
                        } else {
                            "Respuesta local segura procesada por el servidor"
                        },
                        color = RehabianexColors.GreenDark,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = message.text,
                    color = if (message.fromUser) Color.White else RehabianexColors.Black,
                )
            }
        }
    }
}

@Composable
private fun ChatModeSelector(
    selectedMode: String,
    enabled: Boolean,
    onModeChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedMode == "ai",
                onClick = { onModeChanged("ai") },
                enabled = enabled,
                label = { Text("IA") },
            )
            FilterChip(
                selected = selectedMode == "local",
                onClick = { onModeChanged("local") },
                enabled = enabled,
                label = { Text("Local seguro") },
            )
        }
        Text(
            text = "Ambos modos se procesan en el servidor y requieren conexión.",
            color = RehabianexColors.GrayText,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "No escribas nombres, teléfonos, correos, direcciones ni identificadores de pacientes.",
            color = RehabianexColors.GrayText,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SuggestionRow(onInputChanged: (String) -> Unit) {
    val suggestions = listOf(
        "Ansiedad alta" to "¿Quién tuvo ansiedad alta esta semana y qué seguimiento recomiendas?",
        "Recaídas" to "¿Algún paciente registró recaída o señales de riesgo alto?",
        "Sin actividad" to "¿Quién no ha registrado actividad recientemente?",
    )

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        suggestions.forEach { (label, question) ->
            FilterChip(
                selected = false,
                onClick = { onInputChanged(question) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = RehabianexColors.GreenSoft,
                    labelColor = RehabianexColors.GreenDark,
                ),
            )
        }
    }
}

@Composable
private fun MedicalDisclaimer() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = RehabianexColors.GreenSoft,
        border = BorderStroke(1.dp, RehabianexColors.GreenMain),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Esta herramienta no sustituye atención médica, psicológica ni terapéutica.",
                color = RehabianexColors.GreenDark,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "El historial puede limitarse si cambia el consentimiento del paciente.",
                color = RehabianexColors.GreenDark,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RetryChip(onRetry: () -> Unit) {
    FilterChip(
        selected = false,
        onClick = onRetry,
        label = { Text("Reintentar") },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.White,
            labelColor = RehabianexColors.GreenDark,
        ),
    )
}
