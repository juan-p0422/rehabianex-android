package com.example.rehabianex.ui.screens.patient

import android.widget.Toast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rehabianex.data.model.SupportContact
import com.example.rehabianex.navigation.Routes
import com.example.rehabianex.ui.components.AvatarCircle
import com.example.rehabianex.ui.components.BlueCard
import com.example.rehabianex.ui.components.ChipSelector
import com.example.rehabianex.ui.components.DangerCard
import com.example.rehabianex.ui.components.InputField
import com.example.rehabianex.ui.components.OfflineStatusCard
import com.example.rehabianex.ui.components.PhoneNumberField
import com.example.rehabianex.ui.components.PatientScreenScaffold
import com.example.rehabianex.ui.components.RehabianexButton
import com.example.rehabianex.ui.components.RehabianexCard
import com.example.rehabianex.ui.components.RehabianexDangerButton
import com.example.rehabianex.ui.components.RehabianexOutlineButton
import com.example.rehabianex.ui.components.RehabianexSecondaryButton
import com.example.rehabianex.ui.components.ScreenHeader
import com.example.rehabianex.ui.components.SoftCard
import com.example.rehabianex.ui.components.TextAreaField
import com.example.rehabianex.ui.theme.RehabianexColors
import com.example.rehabianex.validation.PhoneNumberValidator
import com.example.rehabianex.validation.CONTACT_RELATIONSHIP_OPTIONS
import com.example.rehabianex.validation.CONTACT_RELATIONSHIP_OTHER

@Composable
fun SupportNetworkScreen(
    onNavigate: (String) -> Unit,
    uiState: SupportContactsUiState,
    saveState: FormSaveUiState,
    onRetry: () -> Unit,
    onSaveContact: (SupportContactFormInput) -> Unit,
    onInputChanged: () -> Unit,
) {
    var showForm by rememberSaveable { mutableStateOf(false) }
    var editingContactId by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val contacts = (uiState as? SupportContactsUiState.Content)?.contacts.orEmpty()
    val editingContact = contacts.firstOrNull { it.id == editingContactId }

    LaunchedEffect(saveState) {
        if (saveState is FormSaveUiState.Success) {
            showForm = false
            editingContactId = null
        }
    }

    LaunchedEffect(contacts) {
        if (editingContactId != null && editingContact == null) {
            editingContactId = null
            showForm = false
        }
    }

    PatientScreenScaffold(Routes.SupportNetwork, onNavigate) {
        ScreenHeader("Mi Red de Apoyo", "Contactos de confianza")
        Spacer(Modifier.height(12.dp))
        BlueCard {
            Text(
                "Tu red de apoyo puede recibir alertas cuando lo necesites. " +
                    "Tú controlas qué información compartir.",
            )
        }
        Spacer(Modifier.height(12.dp))

        when (uiState) {
            SupportContactsUiState.Loading -> SupportNetworkLoading()
            is SupportContactsUiState.Error -> SupportNetworkError(
                message = uiState.message,
                onRetry = onRetry,
            )
            is SupportContactsUiState.Content -> {
                OfflineStatusCard(uiState.offlineStatus)
                SupportNetworkContent(
                contacts = uiState.contacts,
                onContact = { contact ->
                    dialPhone(
                        context = context,
                        phone = contact.phone,
                        message = "Se abrirá el teléfono para marcar a ${contact.name}.",
                    )
                },
                onEdit = { contact ->
                    editingContactId = contact.id
                    showForm = true
                    onInputChanged()
                },
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        ContactSaveStatus(saveState)
        if (saveState !is FormSaveUiState.Idle) {
            Spacer(Modifier.height(10.dp))
        }
        RehabianexOutlineButton(
            if (showForm) "Cerrar formulario" else "Agregar contacto de apoyo",
        ) {
            showForm = !showForm
            if (!showForm) {
                editingContactId = null
            }
            onInputChanged()
        }
        if (showForm) {
            Spacer(Modifier.height(10.dp))
            SupportContactForm(
                initialContact = editingContact,
                saveState = saveState,
                onSaveContact = onSaveContact,
                onInputChanged = onInputChanged,
            )
        }
        Spacer(Modifier.height(8.dp))
        RehabianexOutlineButton("Buscar recursos locales") {
            onNavigate(Routes.LocalDirectory)
        }
        Spacer(Modifier.height(8.dp))
        RehabianexOutlineButton("Vincular supervisor") {
            onNavigate(Routes.SupervisorLink)
        }
        Spacer(Modifier.height(14.dp))
        RehabianexDangerButton("Necesito apoyo ahora") {
            val priorityContact = contacts.minWithOrNull(
                compareBy<SupportContact> { it.priority }.thenBy { it.name.lowercase() },
            )

            if (priorityContact == null) {
                Toast.makeText(
                    context,
                    "Agrega al menos un contacto de apoyo para usar esta opción.",
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                dialPhone(
                    context = context,
                    phone = priorityContact.phone,
                    message = "Se marcará a ${priorityContact.name}, tu contacto de mayor prioridad.",
                )
            }
        }
    }
}

@Composable
private fun SupportContactForm(
    initialContact: SupportContact? = null,
    saveState: FormSaveUiState,
    onSaveContact: (SupportContactFormInput) -> Unit,
    onInputChanged: () -> Unit,
) {
    val initialRelationship = initialContact?.relationship.orEmpty()
    val initialRelationshipOption = CONTACT_RELATIONSHIP_OPTIONS.entries
        .firstOrNull { (_, label) -> label.equals(initialRelationship, ignoreCase = true) }
        ?.key
        ?: initialRelationship.takeIf { it in CONTACT_RELATIONSHIP_OPTIONS.keys }
        ?: if (initialContact == null) "family" else CONTACT_RELATIONSHIP_OTHER
    val initialPhone = PhoneNumberValidator.parseStored(initialContact?.phone)
    var name by rememberSaveable(initialContact?.id) { mutableStateOf(initialContact?.name.orEmpty()) }
    var relationship by rememberSaveable(initialContact?.id) { mutableStateOf(initialRelationshipOption) }
    var customRelationship by rememberSaveable(initialContact?.id) {
        mutableStateOf(
            if (initialRelationshipOption == CONTACT_RELATIONSHIP_OTHER) initialRelationship else "",
        )
    }
    var phone by rememberSaveable(initialContact?.id) { mutableStateOf(initialPhone.number) }
    var phoneRegion by rememberSaveable(initialContact?.id) { mutableStateOf(initialPhone.region) }
    var priority by rememberSaveable(initialContact?.id) {
        mutableStateOf(initialContact?.priority?.coerceIn(1, 5)?.toString() ?: "1")
    }
    var canReceiveAlerts by rememberSaveable(initialContact?.id) {
        mutableStateOf(initialContact?.canReceiveAlerts ?: true)
    }
    var notes by rememberSaveable(initialContact?.id) { mutableStateOf(initialContact?.notes.orEmpty()) }
    val isSaving = saveState is FormSaveUiState.Loading
    val fieldErrors = (saveState as? FormSaveUiState.Error)?.fieldErrors.orEmpty()

    fun update(change: () -> Unit) {
        if (!isSaving) {
            change()
            onInputChanged()
        }
    }

    RehabianexCard {
        Text(
            if (initialContact == null) "Nuevo contacto de apoyo" else "Editar contacto de apoyo",
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        InputField(
            value = name,
            onValueChange = { value -> update { name = value.take(80) } },
            label = "Nombre completo",
            enabled = !isSaving,
            errorMessage = fieldErrors["name"],
        )
        Spacer(Modifier.height(8.dp))
        Text("Relación", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        ChipSelector(
            options = CONTACT_RELATIONSHIP_OPTIONS.values.toList(),
            selected = CONTACT_RELATIONSHIP_OPTIONS[relationship] ?: "Otro",
            onSelected = { selectedLabel ->
                update {
                    relationship = CONTACT_RELATIONSHIP_OPTIONS.entries
                        .first { it.value == selectedLabel }
                        .key
                    if (relationship != CONTACT_RELATIONSHIP_OTHER) {
                        customRelationship = ""
                    }
                }
            },
        )
        if (relationship == CONTACT_RELATIONSHIP_OTHER) {
            Spacer(Modifier.height(8.dp))
            InputField(
                value = customRelationship,
                onValueChange = { value -> update { customRelationship = value.take(60) } },
                label = "Relación personalizada",
                enabled = !isSaving,
                errorMessage = fieldErrors["relationship"],
            )
        } else {
            fieldErrors["relationship"]?.let { message ->
                Spacer(Modifier.height(4.dp))
                Text(message, color = RehabianexColors.Red)
            }
        }
        Spacer(Modifier.height(8.dp))
        PhoneNumberField(
            value = phone,
            region = phoneRegion,
            onValueChange = { value -> update { phone = value } },
            onRegionChange = { selected ->
                update {
                    phoneRegion = selected
                    phone = ""
                }
            },
            enabled = !isSaving,
            allowLegacyInternational = true,
            errorMessage = fieldErrors["phone"],
        )
        Spacer(Modifier.height(8.dp))
        Text("Prioridad", fontWeight = FontWeight.Bold)
        ChipSelector(
            options = (1..5).map(Int::toString),
            selected = priority,
            onSelected = { selected -> update { priority = selected } },
        )
        fieldErrors["priority"]?.let { message ->
            Spacer(Modifier.height(4.dp))
            Text(message, color = RehabianexColors.Red)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Switch(
                checked = canReceiveAlerts,
                onCheckedChange = { checked -> update { canReceiveAlerts = checked } },
                enabled = !isSaving,
            )
            Text("Puede recibir alertas")
        }
        Spacer(Modifier.height(8.dp))
        TextAreaField(
            value = notes,
            onValueChange = { value -> update { notes = value.take(180) } },
            label = "Notas opcionales",
        )
        Spacer(Modifier.height(10.dp))
        RehabianexButton(
            text = if (isSaving) {
                "Guardando..."
            } else if (initialContact == null) {
                "Guardar contacto"
            } else {
                "Actualizar contacto"
            },
            enabled = !isSaving,
        ) {
            val relationshipToSave = if (relationship == CONTACT_RELATIONSHIP_OTHER) {
                customRelationship
            } else {
                checkNotNull(CONTACT_RELATIONSHIP_OPTIONS[relationship])
            }
            onSaveContact(
                SupportContactFormInput(
                    contactId = initialContact?.id,
                    name = name,
                    relationship = relationshipToSave,
                    phone = phone,
                    phoneRegion = phoneRegion,
                    relationshipCategory = relationship,
                    relationshipOther = customRelationship,
                    priority = priority,
                    canReceiveAlerts = canReceiveAlerts,
                    notes = notes,
                ),
            )
        }
    }
}

@Composable
private fun ContactSaveStatus(saveState: FormSaveUiState) {
    when (saveState) {
        FormSaveUiState.Idle -> Unit
        FormSaveUiState.Loading -> RehabianexCard {
            CircularProgressIndicator(color = RehabianexColors.GreenMain)
            Spacer(Modifier.height(8.dp))
            Text("Guardando contacto...")
        }
        is FormSaveUiState.Success -> SoftCard {
            Text("Cambio guardado", fontWeight = FontWeight.Bold)
            Text(saveState.message)
        }
        is FormSaveUiState.Error -> DangerCard {
            Text("No se pudo guardar", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(saveState.message)
        }
    }
}

@Composable
private fun SupportNetworkContent(
    contacts: List<SupportContact>,
    onContact: (SupportContact) -> Unit,
    onEdit: (SupportContact) -> Unit,
) {
    if (contacts.isEmpty()) {
        RehabianexCard {
            Text("Aún no tienes contactos de apoyo registrados.")
            Spacer(Modifier.height(4.dp))
            Text(
                "Cuando agregues contactos aparecerán aquí.",
                color = RehabianexColors.GrayText,
            )
        }
        return
    }

    contacts.forEachIndexed { index, contact ->
        SupportContactCard(
            contact = contact,
            onContact = onContact,
            onEdit = onEdit,
        )
        if (index != contacts.lastIndex) {
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SupportNetworkLoading() {
    RehabianexCard {
        CircularProgressIndicator(color = RehabianexColors.GreenMain)
        Spacer(Modifier.height(10.dp))
        Text("Cargando contactos...")
    }
}

@Composable
private fun SupportNetworkError(
    message: String,
    onRetry: () -> Unit,
) {
    DangerCard {
        Text(message)
    }
    Spacer(Modifier.height(10.dp))
    RehabianexOutlineButton("Reintentar", onClick = onRetry)
}

@Composable
private fun SupportContactCard(
    contact: SupportContact,
    onContact: (SupportContact) -> Unit,
    onEdit: (SupportContact) -> Unit,
) {
    RehabianexCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AvatarCircle(contact.name.initials())
            Column(Modifier.weight(1f)) {
                Text(contact.name, fontWeight = FontWeight.Bold)
                Text(contact.relationship, color = RehabianexColors.GrayText)
                if (contact.phone.isNotBlank()) {
                    Text(contact.phone, color = RehabianexColors.GrayText)
                }
                Text(
                    "Prioridad ${contact.priority}",
                    color = RehabianexColors.GreenDark,
                )
                Spacer(Modifier.height(8.dp))
                RehabianexSecondaryButton("Contactar") {
                    onContact(contact)
                }
                Spacer(Modifier.height(6.dp))
                RehabianexOutlineButton("Editar") {
                    onEdit(contact)
                }
            }
        }
    }
}

private fun String.initials(): String {
    val parts = trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }

    return parts
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
        .joinToString("")
        .ifBlank { "?" }
}
