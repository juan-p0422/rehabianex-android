package com.example.rehabianex.ui.screens.supervisor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rehabianex.navigation.Routes
import com.example.rehabianex.ui.components.AvatarCircle
import com.example.rehabianex.ui.components.MetricCard
import com.example.rehabianex.ui.components.RehabianexButton
import com.example.rehabianex.ui.components.RehabianexCard
import com.example.rehabianex.ui.components.RehabianexDangerButton
import com.example.rehabianex.ui.components.RehabianexOutlineButton
import com.example.rehabianex.ui.components.InputField
import com.example.rehabianex.ui.components.PhoneNumberField
import com.example.rehabianex.ui.components.ChipSelector
import com.example.rehabianex.ui.components.ScreenHeader
import com.example.rehabianex.ui.components.SoftCard
import com.example.rehabianex.ui.components.SupervisorScreenScaffold
import com.example.rehabianex.ui.components.WarningCard
import com.example.rehabianex.ui.theme.RehabianexColors
import com.example.rehabianex.validation.EditableProfileFormInput
import com.example.rehabianex.validation.PhoneNumberValidator
import com.example.rehabianex.validation.SUPERVISOR_TYPE_OPTIONS
import com.example.rehabianex.validation.SUPERVISOR_TYPE_OTHER

@Composable
fun SupervisorProfileScreen(
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    uiState: SupervisorProfileUiState,
    saveState: SupervisorProfileSaveUiState,
    onRetry: () -> Unit,
    onSaveProfile: (EditableProfileFormInput) -> Unit,
    onClearSaveMessage: () -> Unit,
) {
    val limitedAccess = (uiState as? SupervisorProfileUiState.Content)
        ?.data
        ?.authorized == false
    SupervisorScreenScaffold(
        currentRoute = Routes.SupervisorProfile,
        onNavigate = onNavigate,
        limitedAccess = limitedAccess,
    ) {
        when (uiState) {
            SupervisorProfileUiState.Loading -> SupervisorLoadingCard()
            is SupervisorProfileUiState.Error -> SupervisorErrorCard(
                message = uiState.message,
                onRetry = onRetry,
            )

            is SupervisorProfileUiState.Content -> {
                val profile = uiState.data
                var showEditor by rememberSaveable { mutableStateOf(false) }
                ScreenHeader("Perfil supervisor", "Configuración de cuenta y permisos")
                SafeCacheStatus(profile.isRefreshing, profile.lastSyncedAt, profile.refreshMessage)
                Spacer(Modifier.height(12.dp))
                RehabianexCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        AvatarCircle(profile.initials)
                        androidx.compose.foundation.layout.Column {
                            Text(profile.fullName, fontWeight = FontWeight.Bold)
                            Text(profile.email, color = RehabianexColors.GrayText)
                            Text(profile.phone, color = RehabianexColors.GrayText)
                            Text(profile.supervisorType, color = RehabianexColors.GrayText)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    RehabianexOutlineButton(
                        if (showEditor) "Cerrar edición" else "Editar nombre y teléfono",
                    ) {
                        showEditor = !showEditor
                        onClearSaveMessage()
                    }
                }
                if (showEditor) {
                    Spacer(Modifier.height(10.dp))
                    SupervisorProfileEditCard(
                        profile = profile,
                        saveState = saveState,
                        onSaveProfile = onSaveProfile,
                    )
                }
                Spacer(Modifier.height(12.dp))
                SupervisorAuthNotice(
                    authorized = profile.authorized,
                )
                Spacer(Modifier.height(12.dp))
                if (profile.supervisorCode.isNotBlank()) {
                    SupervisorCodeCard(profile.supervisorCode)
                }
                if (profile.authorized) {
                    Spacer(Modifier.height(12.dp))
                    MetricCard("Pacientes vinculados", profile.linkedPatientsCount.toString())
                    Spacer(Modifier.height(12.dp))
                    SoftCard {
                        Text("Solicitudes pendientes", fontWeight = FontWeight.Bold)
                        Text("${profile.pendingRequestsCount} solicitud(es) por revisar")
                    }
                }
                Spacer(Modifier.height(10.dp))
                WarningCard {
                    Text(
                        if (profile.authorized) {
                            "El acceso depende del consentimiento activo del paciente."
                        } else {
                            "Tu cuenta no está disponible para seguimiento en este momento."
                        },
                    )
                }
                Spacer(Modifier.height(14.dp))
                if (profile.authorized) {
                    RehabianexButton("Notificaciones") {
                        onNavigate(Routes.SupervisorNotifications)
                    }
                    Spacer(Modifier.height(8.dp))
                    RehabianexButton("Administrar pacientes y solicitudes") {
                        onNavigate(Routes.PatientsList)
                    }
                }
                Spacer(Modifier.height(8.dp))
                RehabianexOutlineButton("Control de datos") {
                    onNavigate(Routes.DataControl)
                }
                Spacer(Modifier.height(16.dp))
                RehabianexDangerButton("Cerrar sesión", onClick = onLogout)
            }
        }
    }
}

@Composable
private fun SupervisorProfileEditCard(
    profile: com.example.rehabianex.data.model.SupervisorProfileData,
    saveState: SupervisorProfileSaveUiState,
    onSaveProfile: (EditableProfileFormInput) -> Unit,
) {
    val initialPhone = PhoneNumberValidator.parseStored(profile.phone)
    val initialType = profile.supervisorTypeCode
        ?.takeIf(SUPERVISOR_TYPE_OPTIONS::containsKey)
        ?: SUPERVISOR_TYPE_OPTIONS.entries
            .firstOrNull { it.value.equals(profile.supervisorType, ignoreCase = true) }
            ?.key
        ?: when (profile.supervisorType.lowercase()) {
            "psychologist", "psicólogo" -> "psychologist"
            "psicólogo clínico" -> "clinical_psychologist"
            "social_worker", "trabajador social" -> "social_worker"
            "padrino" -> "support_sponsor"
            else -> SUPERVISOR_TYPE_OTHER
        }
    var fullName by rememberSaveable(profile.fullName) { mutableStateOf(profile.fullName) }
    var phone by rememberSaveable(profile.phone) { mutableStateOf(initialPhone.number) }
    var phoneRegion by rememberSaveable(profile.phone) { mutableStateOf(initialPhone.region) }
    var supervisorType by rememberSaveable(profile.supervisorType) { mutableStateOf(initialType) }
    var supervisorTypeOther by rememberSaveable(profile.supervisorType) {
        mutableStateOf(
            if (initialType == SUPERVISOR_TYPE_OTHER) {
                profile.supervisorTypeOther ?: profile.supervisorType
            } else {
                ""
            },
        )
    }
    val isSaving = saveState is SupervisorProfileSaveUiState.Loading
    val fieldErrors = (saveState as? SupervisorProfileSaveUiState.Error)?.fieldErrors.orEmpty()

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
        Text("Tipo de supervisor", fontWeight = FontWeight.Bold)
        ChipSelector(
            options = SUPERVISOR_TYPE_OPTIONS.values.toList(),
            selected = SUPERVISOR_TYPE_OPTIONS[supervisorType] ?: "Otro",
            onSelected = { label ->
                if (!isSaving) {
                    supervisorType = SUPERVISOR_TYPE_OPTIONS.entries
                        .first { it.value == label }
                        .key
                }
            },
        )
        fieldErrors["supervisorType"]?.let { Text(it, color = RehabianexColors.Red) }
        if (supervisorType == SUPERVISOR_TYPE_OTHER) {
            Spacer(Modifier.height(8.dp))
            InputField(
                value = supervisorTypeOther,
                onValueChange = { if (!isSaving) supervisorTypeOther = it.take(80) },
                label = "Describe el tipo de supervisor",
                errorMessage = fieldErrors["supervisorTypeOther"],
                enabled = !isSaving,
            )
        }
        Spacer(Modifier.height(8.dp))
        SoftCard {
            Text("Correo de acceso", fontWeight = FontWeight.Bold)
            Text(profile.email)
            Text(
                "El correo requiere reautenticación y verificación en Firebase; no se guarda como un cambio simple de perfil.",
                color = RehabianexColors.GrayText,
            )
        }
        when (saveState) {
            SupervisorProfileSaveUiState.Idle -> Unit
            SupervisorProfileSaveUiState.Loading -> Text("Guardando perfil...")
            is SupervisorProfileSaveUiState.Success -> Text(
                saveState.message,
                color = RehabianexColors.GreenDark,
            )
            is SupervisorProfileSaveUiState.Error -> Text(
                saveState.message,
                color = RehabianexColors.Red,
            )
        }
        Spacer(Modifier.height(8.dp))
        RehabianexButton(
            text = if (isSaving) "Guardando..." else "Guardar datos",
            enabled = !isSaving,
        ) {
            onSaveProfile(
                EditableProfileFormInput(
                    fullName = fullName,
                    phone = phone,
                    phoneRegion = phoneRegion,
                    supervisorType = supervisorType,
                    supervisorTypeOther = supervisorTypeOther,
                ),
            )
        }
    }
}
