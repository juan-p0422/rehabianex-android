package com.example.rehabianex.ui.screens.patient

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rehabianex.data.model.PatientProfileData
import com.example.rehabianex.data.model.PatientSupervisionState
import com.example.rehabianex.navigation.Routes
import com.example.rehabianex.ui.components.AvatarCircle
import com.example.rehabianex.ui.components.ChipSelector
import com.example.rehabianex.ui.components.DangerCard
import com.example.rehabianex.ui.components.InputField
import com.example.rehabianex.ui.components.PatientScreenScaffold
import com.example.rehabianex.ui.components.RehabianexButton
import com.example.rehabianex.ui.components.RehabianexCard
import com.example.rehabianex.ui.components.RehabianexDangerButton
import com.example.rehabianex.ui.components.RehabianexOutlineButton
import com.example.rehabianex.ui.components.RehabianexSecondaryButton
import com.example.rehabianex.ui.components.ScreenHeader
import com.example.rehabianex.ui.components.SoftCard
import com.example.rehabianex.ui.theme.RehabianexColors

@Composable
fun PatientProfileScreen(
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    uiState: PatientProfileUiState,
    consentActionState: FormSaveUiState,
    privacyActionState: FormSaveUiState,
    onRetry: () -> Unit,
    onSetConsent: (Boolean) -> Unit,
    onRevokeConsent: () -> Unit,
    onRequestSupervisorUnlink: () -> Unit,
    onClearConsentMessage: () -> Unit,
    onSavePrivacy: (PrivacyFormInput) -> Unit,
    onClearPrivacyMessage: () -> Unit,
) {
    PatientScreenScaffold(Routes.PatientProfile, onNavigate) {
        ScreenHeader("Perfil", "Privacidad y configuración")
        Spacer(Modifier.height(12.dp))
        when (uiState) {
            PatientProfileUiState.Loading -> ProfileLoading()
            is PatientProfileUiState.Error -> ProfileError(uiState.message, onRetry)
            is PatientProfileUiState.Content -> ProfileContent(
                profile = uiState.profile,
                isRefreshing = uiState.isRefreshing,
                warning = uiState.warning,
                consentActionState = consentActionState,
                privacyActionState = privacyActionState,
                onNavigate = onNavigate,
                onLogout = onLogout,
                onSetConsent = onSetConsent,
                onRevokeConsent = onRevokeConsent,
                onRequestSupervisorUnlink = onRequestSupervisorUnlink,
                onClearConsentMessage = onClearConsentMessage,
                onSavePrivacy = onSavePrivacy,
                onClearPrivacyMessage = onClearPrivacyMessage,
            )
        }
    }
}

@Composable
private fun ProfileContent(
    profile: PatientProfileData,
    isRefreshing: Boolean,
    warning: String?,
    consentActionState: FormSaveUiState,
    privacyActionState: FormSaveUiState,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    onSetConsent: (Boolean) -> Unit,
    onRevokeConsent: () -> Unit,
    onRequestSupervisorUnlink: () -> Unit,
    onClearConsentMessage: () -> Unit,
    onSavePrivacy: (PrivacyFormInput) -> Unit,
    onClearPrivacyMessage: () -> Unit,
) {
    var showConsentOptions by rememberSaveable { mutableStateOf(false) }
    var showPrivacyOptions by rememberSaveable { mutableStateOf(false) }
    var confirmPrivateMode by rememberSaveable { mutableStateOf(false) }
    var confirmRevokeConsent by rememberSaveable { mutableStateOf(false) }
    var confirmUnlinkRequest by rememberSaveable { mutableStateOf(false) }

    if (isRefreshing) {
        Text("Actualizando...", color = RehabianexColors.GrayText)
        Spacer(Modifier.height(6.dp))
    } else if (profile.isStale) {
        Text("Mostrando datos guardados", color = RehabianexColors.GrayText)
        Spacer(Modifier.height(6.dp))
    }
    if (!warning.isNullOrBlank()) {
        SoftCard {
            Text(warning, color = RehabianexColors.GrayText)
        }
        Spacer(Modifier.height(8.dp))
    }

    RehabianexCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AvatarCircle(profile.initials)
            Column {
                Text(profile.fullName, fontWeight = FontWeight.Bold)
                Text(profile.email, color = RehabianexColors.GrayText)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    SoftCard {
        Text(
            text = if (profile.isStale) {
                "Estado de supervisión por verificar"
            } else {
                when (profile.supervisionState) {
                    PatientSupervisionState.NoSupervisor -> "Sin supervisor"
                    PatientSupervisionState.LinkPending -> "Solicitud pendiente"
                    PatientSupervisionState.LinkRejected -> "Solicitud rechazada"
                    PatientSupervisionState.Active -> "Supervisión activa"
                    PatientSupervisionState.Paused -> "Acceso pausado"
                    PatientSupervisionState.Revoked -> "Consentimiento revocado"
                    PatientSupervisionState.UnlinkPending -> "Desvinculación pendiente"
                    PatientSupervisionState.Unlinked -> "Desvinculado"
                }
            },
            fontWeight = FontWeight.Bold,
        )
        Text(profile.supervisorText)
    }
    Spacer(Modifier.height(10.dp))
    RehabianexCard {
        Text("Modo de privacidad", fontWeight = FontWeight.Bold)
        Text(profile.privacyText)
        Spacer(Modifier.height(8.dp))
        RehabianexOutlineButton("Configurar privacidad") {
            showPrivacyOptions = !showPrivacyOptions
            onClearPrivacyMessage()
        }
    }
    if (showPrivacyOptions) {
        Spacer(Modifier.height(8.dp))
        PrivacyModeCard(
            profile = profile,
            privacyActionState = privacyActionState,
            onSavePrivacy = onSavePrivacy,
        )
    }
    Spacer(Modifier.height(14.dp))
    if (profile.isStale) {
        SoftCard {
            Text(
                "Las acciones de supervisión estarán disponibles al validar el estado con el servidor.",
                color = RehabianexColors.GrayText,
            )
        }
    } else {
        RehabianexButton("Administrar acceso del supervisor") {
            showConsentOptions = !showConsentOptions
            onClearConsentMessage()
        }
        if (showConsentOptions) {
            Spacer(Modifier.height(8.dp))
            ConsentManagementCard(
                profile = profile,
                consentActionState = consentActionState,
                onActivate = { onSetConsent(true) },
                onAskMakePrivate = { confirmPrivateMode = true },
                onAskRevoke = { confirmRevokeConsent = true },
                onLinkSupervisor = { onNavigate(Routes.SupervisorLink) },
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    RehabianexSecondaryButton("Notificaciones") {
        onNavigate(Routes.Notifications)
    }
    Spacer(Modifier.height(8.dp))
    if (!profile.isStale) {
        RehabianexOutlineButton("Vinculación con supervisor") {
            onNavigate(Routes.SupervisorLink)
        }
        if (profile.supervisorUid.isNotBlank() &&
            profile.supervisionState != PatientSupervisionState.UnlinkPending
        ) {
            Spacer(Modifier.height(8.dp))
            RehabianexDangerButton("Solicitar desvinculación") {
                confirmUnlinkRequest = true
            }
        }
    }
    if (!showConsentOptions) {
        ProfileActionMessage(consentActionState)
    }
    Spacer(Modifier.height(16.dp))
    RehabianexDangerButton("Cerrar sesión", onClick = onLogout)

    if (confirmPrivateMode) {
        AlertDialog(
            onDismissRequest = { confirmPrivateMode = false },
            title = { Text("Poner seguimiento en privado") },
            text = {
                Text("Tu supervisor seguirá vinculado, pero verá tu seguimiento en pausa y no podrá consultar tus registros mientras mantengas esta opción.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmPrivateMode = false
                        onSetConsent(false)
                    },
                ) {
                    Text("Poner en privado", color = RehabianexColors.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmPrivateMode = false }) {
                    Text("Cancelar")
                }
            },
        )
    }

    if (confirmRevokeConsent) {
        AlertDialog(
            onDismissRequest = { confirmRevokeConsent = false },
            title = { Text("Revocar consentimiento") },
            text = {
                Text("El supervisor seguirá vinculado, pero perderá el acceso a todos los datos compartidos hasta que vuelvas a activar el consentimiento.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRevokeConsent = false
                        onRevokeConsent()
                    },
                ) {
                    Text("Revocar acceso", color = RehabianexColors.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRevokeConsent = false }) {
                    Text("Cancelar")
                }
            },
        )
    }

    if (confirmUnlinkRequest) {
        AlertDialog(
            onDismissRequest = { confirmUnlinkRequest = false },
            title = { Text("Solicitar desvinculación") },
            text = {
                Text("Tu supervisor verá esta solicitud y podrá aceptarla o rechazarla. Si la acepta, dejará de aparecer en tu seguimiento.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmUnlinkRequest = false
                        onRequestSupervisorUnlink()
                    },
                ) {
                    Text("Enviar solicitud", color = RehabianexColors.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnlinkRequest = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun ProfileActionMessage(actionState: FormSaveUiState) {
    when (actionState) {
        FormSaveUiState.Idle -> Unit
        FormSaveUiState.Loading -> {
            Spacer(Modifier.height(8.dp))
            RehabianexCard {
                Text("Enviando solicitud...", fontWeight = FontWeight.Bold)
                Text("Un momento, estamos registrando el cambio.")
            }
        }
        is FormSaveUiState.Success -> {
            Spacer(Modifier.height(8.dp))
            SoftCard {
                Text(actionState.message, fontWeight = FontWeight.Bold)
            }
        }
        is FormSaveUiState.Error -> {
            Spacer(Modifier.height(8.dp))
            DangerCard {
                Text(actionState.message, fontWeight = FontWeight.Bold)
                ValidationFieldMessages(actionState)
            }
        }
    }
}

@Composable
private fun PrivacyModeCard(
    profile: PatientProfileData,
    privacyActionState: FormSaveUiState,
    onSavePrivacy: (PrivacyFormInput) -> Unit,
) {
    val options = listOf("Nombre visible", "Nickname", "Anónimo")
    var mode by rememberSaveable(profile.fullName, profile.nickname, profile.isAnonymous) {
        mutableStateOf(
            when {
                profile.isAnonymous -> "Anónimo"
                !profile.nickname.isNullOrBlank() -> "Nickname"
                else -> "Nombre visible"
            },
        )
    }
    var displayName by rememberSaveable(profile.nickname, profile.registeredName) {
        mutableStateOf(profile.nickname ?: "")
    }
    val isSaving = privacyActionState is FormSaveUiState.Loading
    val fieldErrors = (privacyActionState as? FormSaveUiState.Error)?.fieldErrors.orEmpty()

    RehabianexCard {
        Text("Opciones de nombre", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        ChipSelector(
            options = options,
            selected = mode,
            onSelected = { selected ->
                if (!isSaving) {
                    mode = selected
                    if (selected == "Nombre visible") {
                        displayName = profile.registeredName
                    } else if (selected == "Nickname") {
                        displayName = profile.nickname ?: ""
                    } else if (selected == "Anónimo") {
                        displayName = "Paciente anónimo"
                    }
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        if (mode == "Nombre visible") {
            SoftCard {
                Text("Nombre registrado", fontWeight = FontWeight.Bold)
                Text(profile.registeredName)
            }
            Spacer(Modifier.height(8.dp))
        } else if (mode == "Nickname") {
            InputField(
                value = displayName,
                onValueChange = { value -> if (!isSaving) displayName = value.take(80) },
                label = "Nickname visible",
                enabled = !isSaving,
                errorMessage = fieldErrors["displayName"],
            )
            Spacer(Modifier.height(8.dp))
        } else {
            SoftCard {
                Text("Modo anónimo", fontWeight = FontWeight.Bold)
                Text("Tu perfil se mostrará como Paciente anónimo cuando sea posible.")
            }
            Spacer(Modifier.height(8.dp))
        }
        if (profile.supervisorUid.isBlank()) {
            Text(
                "Aún no tienes supervisor vinculado; esta opción solo cambia cómo se muestra tu perfil.",
                color = RehabianexColors.GrayText,
            )
            Spacer(Modifier.height(8.dp))
        }
        when (privacyActionState) {
            FormSaveUiState.Idle -> Unit
            FormSaveUiState.Loading -> Text("Guardando privacidad...")
            is FormSaveUiState.Success -> Text(
                privacyActionState.message,
                color = RehabianexColors.GreenDark,
            )
            is FormSaveUiState.Error -> Text(
                privacyActionState.message,
                color = RehabianexColors.Red,
            )
        }
        Spacer(Modifier.height(8.dp))
        RehabianexButton(
            text = if (isSaving) "Guardando..." else "Guardar privacidad",
            enabled = !isSaving,
        ) {
            onSavePrivacy(
                PrivacyFormInput(
                    displayName = displayName,
                    mode = mode,
                ),
            )
        }
    }
}

@Composable
private fun ConsentManagementCard(
    profile: PatientProfileData,
    consentActionState: FormSaveUiState,
    onActivate: () -> Unit,
    onAskMakePrivate: () -> Unit,
    onAskRevoke: () -> Unit,
    onLinkSupervisor: () -> Unit,
) {
    RehabianexCard {
        Text("Consentimiento de supervisión", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            when (profile.supervisionState) {
                PatientSupervisionState.NoSupervisor ->
                    "Aún no tienes supervisor vinculado."
                PatientSupervisionState.LinkPending ->
                    "La solicitud está pendiente de respuesta del supervisor."
                PatientSupervisionState.LinkRejected ->
                    "La solicitud fue rechazada. Puedes intentar con otro supervisor."
                PatientSupervisionState.Active -> if (profile.consentActive) {
                    "Activo para notas, agenda, contactos de apoyo y resumen IA autorizado."
                } else {
                    "La supervisión fue aceptada. Activa el consentimiento para compartir los alcances seleccionados."
                }
                PatientSupervisionState.Paused ->
                    "Acceso pausado. El supervisor conserva la vinculación, pero no puede consultar tus registros."
                PatientSupervisionState.Revoked ->
                    "Consentimiento revocado. El supervisor no puede consultar datos compartidos."
                PatientSupervisionState.UnlinkPending ->
                    "La desvinculación está pendiente de respuesta del supervisor."
                PatientSupervisionState.Unlinked ->
                    "La relación de supervisión terminó y el acceso fue revocado."
            },
            color = RehabianexColors.GrayText,
        )
        Spacer(Modifier.height(8.dp))
        when (consentActionState) {
            FormSaveUiState.Idle -> Unit
            FormSaveUiState.Loading -> Text("Actualizando consentimiento...")
            is FormSaveUiState.Success -> Text(
                consentActionState.message,
                color = RehabianexColors.GreenDark,
            )
            is FormSaveUiState.Error -> {
                Text(
                    consentActionState.message,
                    color = RehabianexColors.Red,
                )
                ValidationFieldMessages(consentActionState)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (profile.supervisionState == PatientSupervisionState.LinkPending ||
            profile.supervisionState == PatientSupervisionState.UnlinkPending
        ) {
            Text("Actualiza el estado cuando la otra persona responda.", color = RehabianexColors.GrayText)
        } else if (profile.supervisorUid.isBlank()) {
            RehabianexOutlineButton("Vincular supervisor", onClick = onLinkSupervisor)
            } else if (profile.consentActive) {
                RehabianexDangerButton(
                    text = "Poner seguimiento en privado",
                    onClick = onAskMakePrivate,
                )
                Spacer(Modifier.height(8.dp))
                RehabianexDangerButton(
                    text = "Revocar consentimiento",
                    enabled = consentActionState !is FormSaveUiState.Loading,
                    onClick = onAskRevoke,
                )
            } else {
                RehabianexButton(
                text = "Activar consentimiento",
                enabled = consentActionState !is FormSaveUiState.Loading,
                    onClick = onActivate,
                )
                if (profile.supervisionState == PatientSupervisionState.Paused) {
                    Spacer(Modifier.height(8.dp))
                    RehabianexDangerButton(
                        text = "Revocar consentimiento",
                        enabled = consentActionState !is FormSaveUiState.Loading,
                        onClick = onAskRevoke,
                    )
                }
            }
    }
}

@Composable
private fun ValidationFieldMessages(error: FormSaveUiState.Error) {
    error.fieldErrors.values
        .distinct()
        .filter { it != error.message }
        .forEach { fieldMessage ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = "• $fieldMessage",
                color = RehabianexColors.Red,
            )
        }
}

@Composable
private fun ProfileLoading() {
    RehabianexCard {
        CircularProgressIndicator(color = RehabianexColors.GreenMain)
        Spacer(Modifier.height(8.dp))
        Text("Cargando perfil...")
    }
}

@Composable
private fun ProfileError(
    message: String,
    onRetry: () -> Unit,
) {
    DangerCard {
        Text(message)
    }
    Spacer(Modifier.height(10.dp))
    RehabianexOutlineButton("Reintentar", onClick = onRetry)
}
