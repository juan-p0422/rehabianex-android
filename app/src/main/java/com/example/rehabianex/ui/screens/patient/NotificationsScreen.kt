package com.example.rehabianex.ui.screens.patient

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rehabianex.data.model.AgendaEvent
import com.example.rehabianex.data.model.NotificationSettingsData
import com.example.rehabianex.navigation.Routes
import com.example.rehabianex.notifications.AgendaNotificationHelper
import com.example.rehabianex.notifications.NotificationScheduler
import com.example.rehabianex.ui.components.DangerCard
import com.example.rehabianex.ui.components.OfflineStatusCard
import com.example.rehabianex.ui.components.NotificationPermissionBanner
import com.example.rehabianex.ui.components.PatientScreenScaffold
import com.example.rehabianex.ui.components.RehabianexButton
import com.example.rehabianex.ui.components.RehabianexCard
import com.example.rehabianex.ui.components.RehabianexOutlineButton
import com.example.rehabianex.ui.components.ScreenHeader
import com.example.rehabianex.ui.components.SettingSwitchCard
import com.example.rehabianex.ui.components.SoftCard
import com.example.rehabianex.ui.components.TimeSelector12h
import com.example.rehabianex.ui.components.formatTime12h
import com.example.rehabianex.ui.theme.RehabianexColors
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

@Composable
fun NotificationsScreen(
    onNavigate: (String) -> Unit,
    uiState: NotificationSettingsUiState,
    saveState: FormSaveUiState,
    onRetry: () -> Unit,
    onSave: (NotificationSettingsFormInput) -> Unit,
    onInputChanged: () -> Unit,
) {
    PatientScreenScaffold(Routes.Notifications, onNavigate) {
        ScreenHeader("Notificaciones", "Configuración discreta")
        Spacer(Modifier.height(12.dp))
        NotificationPermissionBanner()
        SoftCard {
            Text("Tipos de avisos", fontWeight = FontWeight.Bold)
            Text(
                "Los recordatorios configurados aquí son locales en este dispositivo. " +
                    "Los avisos remotos FCM dependen del servidor y siempre usan contenido discreto.",
                color = RehabianexColors.GrayText,
            )
        }
        Spacer(Modifier.height(12.dp))
        when (uiState) {
            NotificationSettingsUiState.Loading -> NotificationsLoading()
            is NotificationSettingsUiState.Error -> NotificationsError(uiState.message, onRetry)
            is NotificationSettingsUiState.Content -> {
                OfflineStatusCard(uiState.offlineStatus)
                NotificationsContent(
                    settings = uiState.settings,
                    events = uiState.events,
                    saveState = saveState,
                    onSave = onSave,
                    onInputChanged = onInputChanged,
                )
            }
        }
    }
}

@Composable
private fun NotificationsContent(
    settings: NotificationSettingsData,
    events: List<AgendaEvent>,
    saveState: FormSaveUiState,
    onSave: (NotificationSettingsFormInput) -> Unit,
    onInputChanged: () -> Unit,
) {
    val context = LocalContext.current
    var pendingSavedSettings by remember { mutableStateOf<NotificationSettingsData?>(null) }
    var dailyNoteEnabled by rememberSaveable(settings.id) { mutableStateOf(settings.dailyNoteEnabled) }
    var dailyNoteTime by rememberSaveable(settings.id) { mutableStateOf(settings.dailyNoteTime) }
    var soberDayEnabled by rememberSaveable(settings.id) { mutableStateOf(settings.soberDayEnabled) }
    var soberDayTime by rememberSaveable(settings.id) { mutableStateOf(settings.soberDayTime) }
    var achievementEnabled by rememberSaveable(settings.id) { mutableStateOf(settings.achievementEnabled) }
    var achievementTime by rememberSaveable(settings.id) { mutableStateOf(settings.achievementTime) }
    var eventRemindersEnabled by rememberSaveable(settings.id) { mutableStateOf(settings.eventRemindersEnabled) }
    var motivationalEnabled by rememberSaveable(settings.id) { mutableStateOf(settings.motivationalEnabled) }
    var motivationalTime by rememberSaveable(settings.id) { mutableStateOf(settings.motivationalTime) }
    fun currentInput(): NotificationSettingsFormInput {
        return NotificationSettingsFormInput(
            dailyNoteEnabled = dailyNoteEnabled,
            dailyNoteTime = dailyNoteTime,
            soberDayEnabled = soberDayEnabled,
            soberDayTime = soberDayTime,
            achievementEnabled = achievementEnabled,
            achievementTime = achievementTime,
            eventRemindersEnabled = eventRemindersEnabled,
            motivationalEnabled = motivationalEnabled,
            motivationalTime = motivationalTime,
        )
    }

    fun currentSettingsForScheduling(): NotificationSettingsData {
        return NotificationSettingsData(
            id = settings.id,
            dailyNoteEnabled = dailyNoteEnabled,
            dailyNoteTime = dailyNoteTime,
            soberDayEnabled = soberDayEnabled,
            soberDayTime = soberDayTime,
            achievementEnabled = achievementEnabled,
            achievementTime = achievementTime,
            eventRemindersEnabled = eventRemindersEnabled,
            motivationalEnabled = motivationalEnabled,
            motivationalTime = motivationalTime,
        )
    }

    LaunchedEffect(saveState, settings) {
        if (saveState is FormSaveUiState.Success) {
            val settingsForSchedule = pendingSavedSettings ?: settings
            val scheduled = NotificationScheduler.schedulePatient(context, settingsForSchedule, events)
            if (!AgendaNotificationHelper.canPostNotifications(context)) {
                Toast.makeText(
                    context,
                    "Configuración guardada. Las notificaciones siguen desactivadas.",
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                Toast.makeText(
                    context,
                    "Configuración guardada. Avisos activos: $scheduled.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
            pendingSavedSettings = null
        }
    }

    SoftCard {
        Text("Todas las opciones son opcionales.", fontWeight = FontWeight.Bold)
        Text("Tú decides qué avisos recibir y a qué hora.")
        Text("Las preferencias se sincronizan con tu cuenta; los avisos se generan localmente en este dispositivo.")
    }
    Spacer(Modifier.height(10.dp))
    SettingSwitchCard(
        title = "Registro diario",
        description = if (dailyNoteEnabled) {
            "Avisar a las ${formatTime12h(dailyNoteTime)} para subir tu nota diaria."
        } else {
            "No se enviarán recordatorios de nota diaria."
        },
        checked = dailyNoteEnabled,
        onCheckedChange = {
            dailyNoteEnabled = it
            onInputChanged()
        },
    )
    if (dailyNoteEnabled) {
        Spacer(Modifier.height(8.dp))
        TimeSelector12h(
            value = dailyNoteTime,
            onValueChange = {
                dailyNoteTime = it
                onInputChanged()
            },
            label = "Hora de registro diario",
            errorMessage = (saveState as? FormSaveUiState.Error)?.fieldErrors?.get("dailyNoteTime"),
        )
    }
    Spacer(Modifier.height(10.dp))
    SettingSwitchCard(
        title = "Nuevo día sobrio",
        description = if (soberDayEnabled) {
            "Avisar a las ${formatTime12h(soberDayTime)} cuando sumes otro día."
        } else {
            "No se enviarán avisos de progreso diario."
        },
        checked = soberDayEnabled,
        onCheckedChange = {
            soberDayEnabled = it
            onInputChanged()
        },
    )
    if (soberDayEnabled) {
        Spacer(Modifier.height(8.dp))
        TimeSelector12h(
            value = soberDayTime,
            onValueChange = {
                soberDayTime = it
                onInputChanged()
            },
            label = "Hora de nuevo día sobrio",
            errorMessage = (saveState as? FormSaveUiState.Error)?.fieldErrors?.get("soberDayTime"),
        )
    }
    Spacer(Modifier.height(10.dp))
    SettingSwitchCard(
        title = "Logros nuevos",
        description = if (achievementEnabled) {
            "Chequear a las ${formatTime12h(achievementTime)} y avisar solo si hay un logro nuevo."
        } else {
            "No se enviarán avisos de logros."
        },
        checked = achievementEnabled,
        onCheckedChange = {
            achievementEnabled = it
            onInputChanged()
        },
    )
    if (achievementEnabled) {
        Spacer(Modifier.height(8.dp))
        TimeSelector12h(
            value = achievementTime,
            onValueChange = {
                achievementTime = it
                onInputChanged()
            },
            label = "Hora de revisión de logros",
            errorMessage = (saveState as? FormSaveUiState.Error)?.fieldErrors?.get("achievementTime"),
        )
    }
    Spacer(Modifier.height(10.dp))
    SettingSwitchCard(
        title = "Eventos de agenda",
        description = if (eventRemindersEnabled) {
            "${events.countFuture()} evento(s) futuro(s) con aviso según su vigencia."
        } else {
            "No se programarán recordatorios de eventos."
        },
        checked = eventRemindersEnabled,
        onCheckedChange = {
            eventRemindersEnabled = it
            onInputChanged()
        },
    )
    Spacer(Modifier.height(10.dp))
    SettingSwitchCard(
        title = "Mensajes motivacionales",
        description = if (motivationalEnabled) {
            "Frase de apoyo periódica a las ${formatTime12h(motivationalTime)}."
        } else {
            "Desactivado para evitar avisos innecesarios."
        },
        checked = motivationalEnabled,
        onCheckedChange = {
            motivationalEnabled = it
            onInputChanged()
        },
    )
    if (motivationalEnabled) {
        Spacer(Modifier.height(8.dp))
        TimeSelector12h(
            value = motivationalTime,
            onValueChange = {
                motivationalTime = it
                onInputChanged()
            },
            label = "Hora de mensaje motivacional",
            errorMessage = (saveState as? FormSaveUiState.Error)?.fieldErrors?.get("motivationalTime"),
        )
    }
    Spacer(Modifier.height(12.dp))
    NotificationSaveMessage(saveState)
    Spacer(Modifier.height(12.dp))
    RehabianexButton(
        text = if (saveState is FormSaveUiState.Loading) {
            "Guardando..."
        } else {
            "Guardar configuración"
        },
        enabled = saveState !is FormSaveUiState.Loading,
    ) {
        pendingSavedSettings = currentSettingsForScheduling()
        onSave(currentInput())
    }
}

@Composable
private fun NotificationSaveMessage(saveState: FormSaveUiState) {
    when (saveState) {
        FormSaveUiState.Idle -> Unit
        FormSaveUiState.Loading -> RehabianexCard {
            Text("Guardando configuración...")
        }
        is FormSaveUiState.Success -> SoftCard {
            Text(saveState.message, color = RehabianexColors.GreenDark)
        }
        is FormSaveUiState.Error -> DangerCard {
            Text(saveState.message)
        }
    }
}

@Composable
private fun NotificationsLoading() {
    RehabianexCard {
        CircularProgressIndicator(color = RehabianexColors.GreenMain)
        Spacer(Modifier.height(8.dp))
        Text("Cargando preferencias...")
    }
}

@Composable
private fun NotificationsError(
    message: String,
    onRetry: () -> Unit,
) {
    DangerCard { Text(message) }
    Spacer(Modifier.height(10.dp))
    RehabianexOutlineButton("Reintentar", onClick = onRetry)
}

private fun List<AgendaEvent>.countFuture(): Int {
    val now = System.currentTimeMillis()
    return count { (it.startsAtMillis() ?: 0L) > now }
}

private fun AgendaEvent.startsAtMillis(): Long? {
    return runCatching {
        OffsetDateTime.parse(startsAt).toInstant().toEpochMilli()
    }.getOrNull()
        ?: runCatching {
            LocalDateTime.parse(startsAt)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
}
