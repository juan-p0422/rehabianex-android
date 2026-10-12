package com.example.rehabianex.ui.screens.patient

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rehabianex.data.model.PatientProfileData
import com.example.rehabianex.data.model.PatientSupervisionState
import com.example.rehabianex.data.model.SupervisionPolicy
import com.example.rehabianex.navigation.Routes
import com.example.rehabianex.ui.components.AvatarCircle
import com.example.rehabianex.ui.components.ChipSelector
import com.example.rehabianex.ui.components.DangerCard
import com.example.rehabianex.ui.components.InputField
import com.example.rehabianex.ui.components.PatientScreenScaffold
import com.example.rehabianex.ui.components.PhoneNumberField
import com.example.rehabianex.ui.components.RehabianexButton
import com.example.rehabianex.ui.components.RehabianexCard
import com.example.rehabianex.ui.components.RehabianexDangerButton
import com.example.rehabianex.ui.components.RehabianexOutlineButton
import com.example.rehabianex.ui.components.RehabianexSecondaryButton
import com.example.rehabianex.ui.components.ScreenHeader
import com.example.rehabianex.ui.components.SoftCard
import com.example.rehabianex.ui.theme.RehabianexColors
import com.example.rehabianex.validation.EditableProfileFormInput
import com.example.rehabianex.validation.PhoneNumberValidator

@Composable
fun PatientProfileScreen(
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    uiState: PatientProfileUiState,
    consentActionState: FormSaveUiState,
    privacyActionState: FormSaveUiState,
    profileActionState: FormSaveUiState,
    onRetry: () -> Unit,
    onSetConsent: (Boolean, Set<String>) -> Unit,
    onRevokeConsent: () -> Unit,
    onRequestSupervisorUnlink: () -> Unit,
    onCancelPendingSupervisorRequest: (String) -> Unit,
    onClearConsentMessage: () -> Unit,
    onSavePrivacy: (PrivacyFormInput) -> Unit,
    onClearPrivacyMessage: () -> Unit,
    onSaveProfile: (EditableProfileFormInput) -> Unit,
    onClearProfileMessage: () -> Unit,
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
                profileActionState = profileActionState,
                onNavigate = onNavigate,
                onLogout = onLogout,
                onSetConsent = onSetConsent,
                onRevokeConsent = onRevokeConsent,
                onRequestSupervisorUnlink = onRequestSupervisorUnlink,
                onCancelPendingSupervisorRequest = onCancelPendingSupervisorRequest,
                onClearConsentMessage = onClearConsentMessage,
                onSavePrivacy = onSavePrivacy,
                onClearPrivacyMessage = onClearPrivacyMessage,
                onSaveProfile = onSaveProfile,
                onClearProfileMessage = onClearProfileMessage,
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
    profileActionState: FormSaveUiState,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    onSetConsent: (Boolean, Set<String>) -> Unit,
    onRevokeConsent: () -> Unit,
    onRequestSupervisorUnlink: () -> Unit,
    onCancelPendingSupervisorRequest: (String) -> Unit,
    onClearConsentMessage: () -> Unit,
    onSavePrivacy: (PrivacyFormInput) -> Unit,
    onClearPrivacyMessage: () -> Unit,
    onSaveProfile: (EditableProfileFormInput) -> Unit,
    onClearProfileMessage: () -> Unit,
) {
    var showConsentOptions by rememberSaveable { mutableStateOf(false) }
    var showPrivacyOptions by rememberSaveable { mutableStateOf(false) }
    var confirmPrivateMode by rememberSaveable { mutableStateOf(false) }
    var confirmRevokeConsent by rememberSaveable { mutableStateOf(false) }
    var confirmUnlinkRequest by rememberSaveable { mutableStateOf(false) }
    var confirmCancelPendingRequest by rememberSaveable { mutableStateOf(false) }
    var showProfileEditor by rememberSaveable { mutableStateOf(false) }

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
                Text(profile.phone, color = RehabianexColors.GrayText)
            }
        }
        Spacer(Modifier.height(8.dp))
        RehabianexOutlineButton(
            if (showProfileEditor) "Cerrar edición" else "Editar nombre y teléfono",
        ) {
            showProfileEditor = !showProfileEditor
            onClearProfileMessage()
        }
    }
    if (showProfileEditor) {
        Spacer(Modifier.height(8.dp))
        PatientProfileEditCard(
            profile = profile,
            actionState = profileActionState,
            onSave = onSaveProfile,
        )
    } else {
        ProfileActionMessage(profileActionState)
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
        profile.supervisorType?.let { type ->
            Text("Tipo: $type", color = RehabianexColors.GrayText)
        }
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
                onActivate = { scopes -> onSetConsent(true, scopes) },
                onAskMakePrivate = { confirmPrivateMode = true },
                onAskRevoke = { confirmRevokeConsent = true },
                onAskCancelPending = { confirmCancelPendingRequest = true },
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
                        onSetConsent(false, emptySet())
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

    if (confirmCancelPendingRequest) {
        AlertDialog(
            onDismissRequest = {
                if (consentActionState !is FormSaveUiState.Loading) {
                    confirmCancelPendingRequest = false
                }
            },
            title = { Text("Retirar solicitud pendiente") },
            text = {
                Text("La solicitud se marcará como cancelada. Esto no elimina el registro ni crea una relación de supervisión.")
            },
            confirmButton = {
                TextButton(
                    enabled = consentActionState !is FormSaveUiState.Loading,
                    onClick = {
                        profile.pendingLinkRequestId?.let(onCancelPendingSupervisorRequest)
                        confirmCancelPendingRequest = false
                    },
                ) { Text("Retirar solicitud") }
            },
            dismissButton = {
                TextButton(
                    enabled = consentActionState !is FormSaveUiState.Loading,
                    onClick = { confirmCancelPendingRequest = false },
                ) { Text("Conservar") }
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
private fun PatientProfileEditCard(
    profile: PatientProfileData,
    actionState: FormSaveUiState,
    onSave: (EditableProfileFormInput) -> Unit,
) {
    val initialPhone = PhoneNumberValidator.parseStored(profile.phone)
    var fullName by rememberSaveable(profile.registeredName) {
        mutableStateOf(profile.registeredName)
    }
    var phone by rememberSaveable(profile.phone) { mutableStateOf(initialPhone.number) }
    var phoneRegion by rememberSaveable(profile.phone) { mutableStateOf(initialPhone.region) }
    var sobrietyStartDate by rememberSaveable(profile.sobrietyStartDate) {
        mutableStateOf(profile.sobrietyStartDate.orEmpty().take(10))
    }
    var confirmSobrietyDateChange by rememberSaveable { mutableStateOf(false) }
    val isSaving = actionState is FormSaveUiState.Loading
    val fieldErrors = (actionState as? FormSaveUiState.Error)?.fieldErrors.orEmpty()

    RehabianexCard {
        Text("Datos personales", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        InputField(
            value = fullName,
            onValueChange = { if (!isSaving) fullName = it.take(120) },
            label = "Nombre completo",
            errorMessage = fieldErrors["fullName"],
            enabled = !isSaving,
        )
        Spacer(Modifier.height(8.dp))
        PhoneNumberField(
            value = phone,
            region = phoneRegion,
            onValueChange = { if (!isSaving) phone = it },
            onRegionChange = { region ->
                if (!isSaving) {
                    phoneRegion = region
                    phone = ""
                }
            },
            errorMessage = fieldErrors["phone"],
            enabled = !isSaving,
            allowLegacyInternational = true,
        )
        Spacer(Modifier.height(8.dp))
        InputField(
            value = sobrietyStartDate,
            onValueChange = { if (!isSaving) sobrietyStartDate = it.take(10) },
            label = "Fecha de inicio de sobriedad (AAAA-MM-DD)",
            errorMessage = fieldErrors["sobrietyStartDate"],
            enabled = !isSaving,
        )
        Text(
            "Cambiar esta fecha recalcula los días y el progreso de logros; no elimina tus registros anteriores.",
            color = RehabianexColors.GrayText,
        )
        Spacer(Modifier.height(8.dp))
        SoftCard {
            Text("Correo de acceso", fontWeight = FontWeight.Bold)
            Text(profile.email)
            Text(
                "El correo no se cambia como un dato de perfil: requiere reautenticación y verificación en Firebase.",
                color = RehabianexColors.GrayText,
            )
        }
        when (actionState) {
            FormSaveUiState.Idle -> Unit
            FormSaveUiState.Loading -> Text("Guardando perfil...")
            is FormSaveUiState.Success -> Text(
                actionState.message,
                color = RehabianexColors.GreenDark,
            )
            is FormSaveUiState.Error -> {
                Text(actionState.message, color = RehabianexColors.Red)
                ValidationFieldMessages(actionState)
            }
        }
        Spacer(Modifier.height(8.dp))
        RehabianexButton(
            text = if (isSaving) "Guardando..." else "Guardar datos",
            enabled = !isSaving,
        ) {
            if (sobrietyStartDate != profile.sobrietyStartDate.orEmpty().take(10)) {
                confirmSobrietyDateChange = true
            } else {
                onSave(
                    EditableProfileFormInput(
                        fullName = fullName,
                        phone = phone,
                        phoneRegion = phoneRegion,
                        sobrietyStartDate = sobrietyStartDate,
                    ),
                )
            }
        }
    }
    if (confirmSobrietyDateChange) {
        AlertDialog(
            onDismissRequest = { confirmSobrietyDateChange = false },
            title = { Text("Confirmar fecha de sobriedad") },
            text = {
                Text("Se recalcularán tus días y el avance hacia logros. Tus registros emocionales no se borrarán.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSobrietyDateChange = false
                        onSave(
                            EditableProfileFormInput(
                                fullName = fullName,
                                phone = phone,
                                phoneRegion = phoneRegion,
                                sobrietyStartDate = sobrietyStartDate,
                            ),
                        )
                    },
                ) { Text("Confirmar cambio") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSobrietyDateChange = false }) { Text("Cancelar") }
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
    val options = listOf("Nombre visible", "Nickname", "Ocultar identidad")
    var mode by rememberSaveable(profile.fullName, profile.nickname, profile.isAnonymous) {
        mutableStateOf(
            when {
                profile.isAnonymous -> "Ocultar identidad"
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
                    } else if (selected == "Ocultar identidad") {
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
                Text("Privacidad visual", fontWeight = FontWeight.Bold)
                Text("Tu nombre se sustituirá por “Paciente anónimo” en las vistas compatibles.")
                Text(
                    "Tu cuenta y tus datos siguen vinculados a tu identidad técnica; esta opción no garantiza anonimato.",
                    color = RehabianexColors.GrayText,
                )
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
    onActivate: (Set<String>) -> Unit,
    onAskMakePrivate: () -> Unit,
    onAskRevoke: () -> Unit,
    onAskCancelPending: () -> Unit,
    onLinkSupervisor: () -> Unit,
) {
    var selectedScopes by rememberSaveable(profile.consentId, profile.consentScope) {
        mutableStateOf(profile.consentScope)
    }
    var editingScopes by rememberSaveable(profile.consentId, profile.consentActive) {
        mutableStateOf(!profile.consentActive)
    }
    val isLoading = consentActionState is FormSaveUiState.Loading
    val canSelectScopes = profile.supervisionState == PatientSupervisionState.Active ||
        profile.supervisionState == PatientSupervisionState.Paused

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
                    "Consentimiento activo para ${profile.consentScope.size} alcance(s) seleccionado(s)."
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
            if (profile.supervisionState == PatientSupervisionState.LinkPending &&
                !profile.pendingLinkRequestId.isNullOrBlank()
            ) {
                Spacer(Modifier.height(8.dp))
                RehabianexOutlineButton(
                    text = "Retirar solicitud pendiente",
                    enabled = !isLoading,
                    onClick = onAskCancelPending,
                )
            }
        } else if (profile.supervisorUid.isBlank()) {
            RehabianexOutlineButton("Vincular supervisor", onClick = onLinkSupervisor)
        } else if (profile.supervisionState == PatientSupervisionState.Revoked) {
            Text(
                "La revocación bloquea consultas posteriores, pero no borra información consultada previamente.",
                color = RehabianexColors.GrayText,
            )
        } else if (canSelectScopes) {
            if (profile.consentActive && !editingScopes) {
                profile.consentScope.forEach { scope ->
                    Text("• ${scopeLabel(scope)}", color = RehabianexColors.GrayText)
                }
                Spacer(Modifier.height(8.dp))
                RehabianexOutlineButton(
                    text = "Editar datos compartidos",
                    enabled = !isLoading,
                    onClick = { editingScopes = true },
                )
            } else {
                Text(
                    "Selecciona únicamente los datos que autorizas. Ninguna opción se activa automáticamente.",
                    color = RehabianexColors.GrayText,
                )
                Spacer(Modifier.height(6.dp))
                SupervisionPolicy.consentScopes.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            modifier = Modifier.semantics {
                                contentDescription = "Compartir ${option.label}"
                            },
                            checked = option.code in selectedScopes,
                            enabled = !isLoading,
                            onCheckedChange = { checked ->
                                selectedScopes = if (checked) {
                                    selectedScopes + option.code
                                } else {
                                    selectedScopes - option.code
                                }
                            },
                        )
                        Column {
                            Text(option.label, fontWeight = FontWeight.Bold)
                            Text(option.description, color = RehabianexColors.GrayText)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                RehabianexButton(
                    text = when {
                        profile.consentActive -> "Guardar permisos"
                        profile.supervisionState == PatientSupervisionState.Paused ->
                            "Reactivar consentimiento"
                        else -> "Activar consentimiento"
                    },
                    enabled = selectedScopes.isNotEmpty() && !isLoading,
                    onClick = {
                        onActivate(selectedScopes.toSet())
                        editingScopes = false
                    },
                )
                if (profile.consentActive) {
                    Spacer(Modifier.height(8.dp))
                    RehabianexOutlineButton(
                        text = "Cancelar edición",
                        enabled = !isLoading,
                        onClick = {
                            selectedScopes = profile.consentScope
                            editingScopes = false
                        },
                    )
                }
            }

            if (profile.consentActive) {
                Spacer(Modifier.height(8.dp))
                RehabianexDangerButton(
                    text = "Poner seguimiento en privado",
                    enabled = !isLoading,
                    onClick = onAskMakePrivate,
                )
                Spacer(Modifier.height(8.dp))
                RehabianexDangerButton(
                    text = "Revocar consentimiento",
                    enabled = consentActionState !is FormSaveUiState.Loading,
                    onClick = onAskRevoke,
                )
            } else if (profile.supervisionState == PatientSupervisionState.Paused) {
                Spacer(Modifier.height(8.dp))
                RehabianexDangerButton(
                    text = "Revocar consentimiento",
                    enabled = !isLoading,
                    onClick = onAskRevoke,
                )
            }
        }
    }
}

private fun scopeLabel(scope: String): String = SupervisionPolicy.consentScopes
    .firstOrNull { it.code == scope }
    ?.label
    ?: scope

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
