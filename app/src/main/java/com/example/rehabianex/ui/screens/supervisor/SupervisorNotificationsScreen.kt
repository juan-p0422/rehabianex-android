package com.example.rehabianex.ui.screens.supervisor

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import com.example.rehabianex.data.model.NotificationSettingsData
import com.example.rehabianex.navigation.Routes
import com.example.rehabianex.notifications.AgendaNotificationHelper
import com.example.rehabianex.notifications.NotificationScheduler
import com.example.rehabianex.ui.components.RehabianexButton
import com.example.rehabianex.ui.components.NotificationPermissionBanner
import com.example.rehabianex.ui.components.RehabianexCard
import com.example.rehabianex.ui.components.RehabianexOutlineButton
import com.example.rehabianex.ui.components.ScreenHeader
import com.example.rehabianex.ui.components.SettingSwitchCard
import com.example.rehabianex.ui.components.SoftCard
import com.example.rehabianex.ui.components.SupervisorScreenScaffold
import com.example.rehabianex.ui.components.TimeSelector12h
import com.example.rehabianex.ui.components.WarningCard
import com.example.rehabianex.ui.components.formatTime12h
import com.example.rehabianex.ui.theme.RehabianexColors

@Composable
fun SupervisorNotificationsScreen(
    onNavigate: (String) -> Unit,
    uiState: SupervisorNotificationsUiState,
    saveState: RequestActionUiState,
    onRetry: () -> Unit,
    onSave: (SupervisorNotificationSettingsFormInput) -> Unit,
    onInputChanged: () -> Unit,
) {
    SupervisorScreenScaffold(Routes.SupervisorNotifications, onNavigate) {
        ScreenHeader("Notificaciones", "Avisos de seguimiento")
        Spacer(Modifier.height(12.dp))
        NotificationPermissionBanner()
        (uiState as? SupervisorNotificationsUiState.Content)?.let {
            SafeCacheStatus(it.isRefreshing, it.lastSyncedAt, it.errorMessage)
        }
        when (uiState) {
            SupervisorNotificationsUiState.Loading -> SupervisorLoadingCard("Cargando preferencias...")
            is SupervisorNotificationsUiState.Error -> SupervisorErrorCard(uiState.message, onRetry)
            is SupervisorNotificationsUiState.Content -> SupervisorNotificationsContent(
                data = uiState.data,
                saveState = saveState,
                onSave = onSave,
                onInputChanged = onInputChanged,
            )
        }
    }
}

@Composable
private fun SupervisorNotificationsContent(
    data: SupervisorNotificationsData,
    saveState: RequestActionUiState,
    onSave: (SupervisorNotificationSettingsFormInput) -> Unit,
    onInputChanged: () -> Unit,
) {
    val context = LocalContext.current
    val settings = data.settings
    val patientsCount = data.homeData.patients.size
    val relapseCount = data.homeData.patients.count { it.latestStatus.contains("reca", ignoreCase = true) }
    val pendingRequestsCount = data.homeData.pendingRequests.size
    var pendingSavedSettings by remember { mutableStateOf<NotificationSettingsData?>(null) }

    var dailySummaryEnabled by rememberSaveable(settings.id) { mutableStateOf(settings.dailyNoteEnabled) }
    var dailySummaryTime by rememberSaveable(settings.id) { mutableStateOf(settings.dailyNoteTime) }
    var relapseAlertEnabled by rememberSaveable(settings.id) { mutableStateOf(settings.soberDayEnabled) }
    var relapseAlertTime by rememberSaveable(settings.id) { mutableStateOf(settings.soberDayTime) }
    var pendingRequestsEnabled by rememberSaveable(settings.id) { mutableStateOf(settings.achievementEnabled) }
    var pendingRequestsTime by rememberSaveable(settings.id) { mutableStateOf(settings.achievementTime) }
    var followUpEnabled by rememberSaveable(settings.id) { mutableStateOf(settings.motivationalEnabled) }
    var followUpTime by rememberSaveable(settings.id) { mutableStateOf(settings.motivationalTime) }

    fun currentInput() = SupervisorNotificationSettingsFormInput(
        dailySummaryEnabled = dailySummaryEnabled,
        dailySummaryTime = dailySummaryTime,
        relapseAlertEnabled = relapseAlertEnabled,
        relapseAlertTime = relapseAlertTime,
        pendingRequestsEnabled = pendingRequestsEnabled,
        pendingRequestsTime = pendingRequestsTime,
        followUpEnabled = followUpEnabled,
        followUpTime = followUpTime,
    )

    fun currentSettingsForScheduling() = NotificationSettingsData(
        id = settings.id,
        dailyNoteEnabled = dailySummaryEnabled,
        dailyNoteTime = dailySummaryTime,
        soberDayEnabled = relapseAlertEnabled,
        soberDayTime = relapseAlertTime,
        achievementEnabled = pendingRequestsEnabled,
        achievementTime = pendingRequestsTime,
        eventRemindersEnabled = false,
        motivationalEnabled = followUpEnabled,
        motivationalTime = followUpTime,
    )

    LaunchedEffect(saveState, settings) {
        if (saveState is RequestActionUiState.Success) {
            val settingsForSchedule = pendingSavedSettings ?: settings
            val scheduled = NotificationScheduler.scheduleSupervisor(context, settingsForSchedule)
            if (!AgendaNotificationHelper.canPostNotifications(context)) {
                Toast.makeText(
                    context,
                    "Configuración guardada. Las notificaciones siguen desactivadas.",
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                Toast.makeText(
                    context,
                    "Avisos guardados: $scheduled activos.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
            pendingSavedSettings = null
        }
    }

    SoftCard {
        Text("Avisos pensados para seguimiento.", fontWeight = FontWeight.Bold)
        Text("La app revisará únicamente pacientes vinculados con permiso activo.")
        Text("Estas preferencias y sus avisos son locales en este dispositivo; no se envían por FCM.")
    }
    Spacer(Modifier.height(10.dp))
    SettingSwitchCard(
        title = "Resumen diario",
        description = if (dailySummaryEnabled) {
            "Avisar a las ${formatTime12h(dailySummaryTime)} con el estado de tus pacientes."
        } else {
            "No se enviará resumen diario."
        },
        checked = dailySummaryEnabled,
        onCheckedChange = {
            dailySummaryEnabled = it
            onInputChanged()
        },
    )
    if (dailySummaryEnabled) {
        Spacer(Modifier.height(8.dp))
        TimeSelector12h(
            value = dailySummaryTime,
            onValueChange = {
                dailySummaryTime = it
                onInputChanged()
            },
            label = "Hora del resumen diario",
        )
    }
    Spacer(Modifier.height(10.dp))
    SettingSwitchCard(
        title = "Alertas de recaída",
        description = if (relapseAlertEnabled) {
            "Revisar a las ${formatTime12h(relapseAlertTime)} si algún paciente registró recaída."
        } else {
            "No se revisarán recaídas de forma programada."
        },
        checked = relapseAlertEnabled,
        onCheckedChange = {
            relapseAlertEnabled = it
            onInputChanged()
        },
    )
    if (relapseAlertEnabled) {
        Spacer(Modifier.height(8.dp))
        TimeSelector12h(
            value = relapseAlertTime,
            onValueChange = {
                relapseAlertTime = it
                onInputChanged()
            },
            label = "Hora para revisar recaídas",
        )
    }
    Spacer(Modifier.height(10.dp))
    SettingSwitchCard(
        title = "Solicitudes pendientes",
        description = if (pendingRequestsEnabled) {
            "Avisar a las ${formatTime12h(pendingRequestsTime)} si hay solicitudes por revisar."
        } else {
            "No se avisará sobre solicitudes pendientes."
        },
        checked = pendingRequestsEnabled,
        onCheckedChange = {
            pendingRequestsEnabled = it
            onInputChanged()
        },
    )
    if (pendingRequestsEnabled) {
        Spacer(Modifier.height(8.dp))
        TimeSelector12h(
            value = pendingRequestsTime,
            onValueChange = {
                pendingRequestsTime = it
                onInputChanged()
            },
            label = "Hora para revisar solicitudes",
        )
    }
    Spacer(Modifier.height(10.dp))
    SettingSwitchCard(
        title = "Recordatorio de seguimiento",
        description = if (followUpEnabled) {
            "Avisar a las ${formatTime12h(followUpTime)} para revisar avances importantes."
        } else {
            "Desactivado para evitar avisos que no necesites."
        },
        checked = followUpEnabled,
        onCheckedChange = {
            followUpEnabled = it
            onInputChanged()
        },
    )
    if (followUpEnabled) {
        Spacer(Modifier.height(8.dp))
        TimeSelector12h(
            value = followUpTime,
            onValueChange = {
                followUpTime = it
                onInputChanged()
            },
            label = "Hora de seguimiento",
        )
    }
    Spacer(Modifier.height(10.dp))
    RehabianexCard {
        Text("Estado actual", fontWeight = FontWeight.Bold)
        Text("$patientsCount paciente(s) vinculados.", color = RehabianexColors.GrayText)
        Text("$relapseCount recaída(s) recientes detectadas.", color = RehabianexColors.GrayText)
        Text("$pendingRequestsCount solicitud(es) por revisar.", color = RehabianexColors.GrayText)
    }
    Spacer(Modifier.height(12.dp))
    SupervisorNotificationSaveMessage(saveState)
    Spacer(Modifier.height(12.dp))
    RehabianexButton(
        text = if (saveState is RequestActionUiState.Loading) "Guardando..." else "Guardar notificaciones",
        enabled = saveState !is RequestActionUiState.Loading,
    ) {
        pendingSavedSettings = currentSettingsForScheduling()
        onSave(currentInput())
    }
}

@Composable
private fun SupervisorNotificationSaveMessage(saveState: RequestActionUiState) {
    when (saveState) {
        RequestActionUiState.Idle -> Unit
        RequestActionUiState.Loading -> RehabianexCard {
            Text("Guardando avisos...")
        }
        is RequestActionUiState.Success -> SoftCard {
            Text(saveState.message, color = RehabianexColors.GreenDark)
        }
        is RequestActionUiState.Error -> WarningCard {
            Text(saveState.message)
        }
    }
}
