package com.example.rehabianex.ui.screens.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.rehabianex.core.performance.StaleRefreshController
import com.example.rehabianex.core.performance.PATIENT_REFRESH_SCOPE
import com.example.rehabianex.core.performance.RefreshInvalidationRegistry
import com.example.rehabianex.data.model.AchievementsData
import com.example.rehabianex.data.model.AgendaEvent
import com.example.rehabianex.data.model.CreateAgendaEventRequest
import com.example.rehabianex.data.model.CreatePatientNoteRequest
import com.example.rehabianex.data.model.CreateSupportContactRequest
import com.example.rehabianex.data.model.LocalResource
import com.example.rehabianex.data.model.MetricsData
import com.example.rehabianex.data.model.NotificationSettingsData
import com.example.rehabianex.data.model.OfflineStatus
import com.example.rehabianex.data.model.PatientHomeData
import com.example.rehabianex.data.model.PatientProfileData
import com.example.rehabianex.data.model.PatientSupervisionState
import com.example.rehabianex.data.model.SupportContact
import com.example.rehabianex.data.model.SupervisorCodePreview
import com.example.rehabianex.data.repository.PatientDataResult
import com.example.rehabianex.data.repository.PatientRepository
import com.example.rehabianex.validation.EditableProfileFormInput
import com.example.rehabianex.validation.PHONE_REGION_MEXICO
import com.example.rehabianex.validation.PhoneNumberValidator
import com.example.rehabianex.validation.ProfileFormValidator
import com.example.rehabianex.validation.CONTACT_RELATIONSHIP_OPTIONS
import com.example.rehabianex.validation.CONTACT_RELATIONSHIP_OTHER
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private fun patientRefreshController() = StaleRefreshController(
    invalidationScope = PATIENT_REFRESH_SCOPE,
)

private fun invalidatePatientRefreshes() {
    RefreshInvalidationRegistry.invalidate(PATIENT_REFRESH_SCOPE)
}

data class DailyNoteFormInput(
    val mood: String,
    val anxiety: String,
    val craving: String,
    val energy: String,
    val sleepQuality: String,
    val sober: String,
    val triggers: Set<String>,
    val copingActions: Set<String>,
    val otherTrigger: String = "",
    val otherCopingAction: String = "",
    val note: String,
)

data class SupportContactFormInput(
    val contactId: String? = null,
    val name: String,
    val relationship: String,
    val phone: String,
    val priority: String,
    val canReceiveAlerts: Boolean,
    val notes: String,
    val phoneRegion: String = PHONE_REGION_MEXICO,
    val relationshipCategory: String = CONTACT_RELATIONSHIP_OTHER,
    val relationshipOther: String = "",
    val originalPriority: Int? = null,
)

internal fun shouldPreserveLegacySupportPriority(
    contactId: String?,
    originalPriority: Int?,
    enteredPriority: String,
): Boolean = contactId != null &&
    originalPriority !in 1..5 &&
    enteredPriority.toIntOrNull() == originalPriority

data class PrivacyFormInput(
    val displayName: String,
    val mode: String,
)

data class EventFormInput(
    val title: String,
    val type: String,
    val date: String,
    val startTime: String,
    val durationMinutes: String,
    val location: String,
    val notes: String,
)

data class NotificationSettingsFormInput(
    val dailyNoteEnabled: Boolean,
    val dailyNoteTime: String,
    val soberDayEnabled: Boolean,
    val soberDayTime: String,
    val achievementEnabled: Boolean,
    val achievementTime: String,
    val eventRemindersEnabled: Boolean,
    val motivationalEnabled: Boolean,
    val motivationalTime: String,
    val adaptiveSuggestionsEnabled: Boolean,
    val inactivityReminderEnabled: Boolean,
    val inactivityDays: String,
    val inactivityReminderTime: String,
)

sealed interface DailyNoteSaveUiState {
    data object Idle : DailyNoteSaveUiState
    data object Loading : DailyNoteSaveUiState
    data class Success(
        val message: String,
        val suggestion: com.example.rehabianex.adaptive.AdaptiveSuggestion? = null,
    ) : DailyNoteSaveUiState
    data class Error(
        val message: String,
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : DailyNoteSaveUiState
}

sealed interface FormSaveUiState {
    data object Idle : FormSaveUiState
    data object Loading : FormSaveUiState
    data class Success(val message: String) : FormSaveUiState
    data class Error(
        val message: String,
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : FormSaveUiState
}

sealed interface PatientHomeUiState {
    data object Loading : PatientHomeUiState
    data class Content(
        val data: PatientHomeData,
        val offlineStatus: OfflineStatus = OfflineStatus(),
    ) : PatientHomeUiState
    data class Error(val message: String) : PatientHomeUiState
}

sealed interface MetricsUiState {
    data object Loading : MetricsUiState
    data class Content(val data: MetricsData, val offlineStatus: OfflineStatus = OfflineStatus()) : MetricsUiState
    data class Error(val message: String) : MetricsUiState
}

sealed interface AchievementsUiState {
    data object Loading : AchievementsUiState
    data class Content(val data: AchievementsData, val offlineStatus: OfflineStatus = OfflineStatus()) : AchievementsUiState
    data class Error(val message: String) : AchievementsUiState
}

sealed interface SupportContactsUiState {
    data object Loading : SupportContactsUiState
    data class Content(
        val contacts: List<SupportContact>,
        val offlineStatus: OfflineStatus = OfflineStatus(),
    ) : SupportContactsUiState
    data class Error(val message: String) : SupportContactsUiState
}

sealed interface AgendaEventsUiState {
    data object Loading : AgendaEventsUiState
    data class Content(val events: List<AgendaEvent>, val offlineStatus: OfflineStatus = OfflineStatus()) : AgendaEventsUiState
    data class Error(val message: String) : AgendaEventsUiState
}

sealed interface LocalResourcesUiState {
    data object Loading : LocalResourcesUiState
    data class Content(
        val resources: List<LocalResource>,
        val offlineStatus: OfflineStatus = OfflineStatus(),
    ) : LocalResourcesUiState
    data class Error(val message: String) : LocalResourcesUiState
}

sealed interface PatientProfileUiState {
    data object Loading : PatientProfileUiState
    data class Content(
        val profile: PatientProfileData,
        val isRefreshing: Boolean = false,
        val warning: String? = null,
    ) : PatientProfileUiState
    data class Error(val message: String) : PatientProfileUiState
}

sealed interface NotificationSettingsUiState {
    data object Loading : NotificationSettingsUiState
    data class Content(
        val settings: NotificationSettingsData,
        val events: List<AgendaEvent>,
        val offlineStatus: OfflineStatus = OfflineStatus(),
    ) : NotificationSettingsUiState
    data class Error(val message: String) : NotificationSettingsUiState
}

sealed interface SupervisorLinkUiState {
    data object Idle : SupervisorLinkUiState
    data object Loading : SupervisorLinkUiState
    data class Status(val supervisionState: PatientSupervisionState) : SupervisorLinkUiState
    data class Preview(val supervisor: SupervisorCodePreview) : SupervisorLinkUiState
    data class Success(val message: String) : SupervisorLinkUiState
    data class Error(
        val message: String,
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : SupervisorLinkUiState
}

class EmotionalCheckinViewModel(
    private val repository: PatientRepository,
) : ViewModel() {
    private val _saveState = MutableStateFlow<DailyNoteSaveUiState>(DailyNoteSaveUiState.Idle)
    val saveState: StateFlow<DailyNoteSaveUiState> = _saveState.asStateFlow()

    fun save(input: DailyNoteFormInput) {
        if (_saveState.value is DailyNoteSaveUiState.Loading) return

        val request = input.toRequestOrError()
        if (request == null) return

        viewModelScope.launch {
            _saveState.value = DailyNoteSaveUiState.Loading
            _saveState.value = when (val result = repository.saveDailyNote(request)) {
                is PatientDataResult.Success -> {
                    invalidatePatientRefreshes()
                    DailyNoteSaveUiState.Success(
                        message = result.data,
                        suggestion = repository.handleAdaptiveNoteSaved(request),
                    )
                }
                is PatientDataResult.Failure -> DailyNoteSaveUiState.Error(
                    result.message,
                    result.fieldErrors,
                )
            }
        }
    }

    fun clearMessage() {
        if (_saveState.value !is DailyNoteSaveUiState.Loading) {
            _saveState.value = DailyNoteSaveUiState.Idle
        }
    }

    private fun DailyNoteFormInput.toRequestOrError(): CreatePatientNoteRequest? {
        val cleanMood = mood.takeIf { it in ALLOWED_MOODS }
        val anxietyValue = anxiety.toIntOrNull()?.takeIf { it in 0..5 }
        val cravingValue = craving.toIntOrNull()?.takeIf { it in 0..5 }
        val energyValue = energy.toIntOrNull()?.takeIf { it in 0..5 }
        val sleepQualityValue = sleepQuality.toIntOrNull()?.takeIf { it in 0..5 }
        val keptSobriety = when (sober) {
            "Sí" -> true
            "No" -> false
            else -> null
        }
        val cleanOtherTrigger = otherTrigger.cleanFreeText(120)
        val cleanOtherCopingAction = otherCopingAction.cleanFreeText(120)
        val safeTriggers = (triggers.filter { it in ALLOWED_TRIGGERS } +
            listOfNotNull(cleanOtherTrigger.takeIf { "Otro" in triggers }))
            .distinctBy { it.lowercase().trim() }
            .take(8)
        val safeCopingActions = (copingActions.filter { it in ALLOWED_COPING_ACTIONS } +
            listOfNotNull(cleanOtherCopingAction.takeIf { "Otro" in copingActions }))
            .distinctBy { it.lowercase().trim() }
            .take(8)
        val safeNote = note
            .filterNot { it.isISOControl() }
            .trim()
            .take(1000)

        val message = when {
            cleanMood == null -> "Selecciona un estado de ánimo válido."
            anxietyValue == null -> "Selecciona un nivel de ansiedad válido."
            cravingValue == null -> "Selecciona un nivel de craving válido."
            energyValue == null -> "Selecciona un nivel de energía válido."
            sleepQualityValue == null -> "Selecciona cómo dormiste."
            keptSobriety == null -> "Indica si mantuviste tu sobriedad."
            "Otro" in triggers && cleanOtherTrigger.isNullOrBlank() ->
                "Escribe el otro detonante (máximo 120 caracteres)."
            "Otro" in copingActions && cleanOtherCopingAction.isNullOrBlank() ->
                "Escribe la otra acción de afrontamiento (máximo 120 caracteres)."
            else -> null
        }

        if (message != null) {
            _saveState.value = DailyNoteSaveUiState.Error(message)
            return null
        }

        return CreatePatientNoteRequest(
            mood = cleanMood!!.lowercase(),
            moodScore = cleanMood.toMoodScore(),
            anxietyLevel = anxietyValue!! * 2,
            cravingLevel = cravingValue!! * 2,
            energyLevel = energyValue!! * 2,
            sleepQuality = sleepQualityValue!! * 2,
            hadRelapse = !keptSobriety!!,
            triggers = safeTriggers.filterNot { it == "Otro" },
            copingActions = safeCopingActions.filterNot { it == "Otro" },
            noteText = safeNote,
        )
    }

    private fun String.cleanFreeText(maxLength: Int): String? =
        filterNot { it.isISOControl() }
            .trim()
            .takeIf { it.isNotBlank() && it.length <= maxLength && !it.equals("Otro", ignoreCase = true) }

    private fun String.toMoodScore(): Int {
        return when (this) {
            "Muy bajo" -> 2
            "Bajo" -> 4
            "Neutral" -> 6
            "Bueno" -> 8
            else -> 5
        }
    }

    class Factory(
        private val repository: PatientRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(EmotionalCheckinViewModel::class.java))
            return EmotionalCheckinViewModel(repository) as T
        }
    }

    private companion object {
        val ALLOWED_MOODS = setOf("Muy bajo", "Bajo", "Neutral", "Bueno")
        val ALLOWED_TRIGGERS = setOf("Estrés", "Soledad", "Presión social", "Tristeza", "Otro")
        val ALLOWED_COPING_ACTIONS = setOf("Respiración", "Llamar apoyo", "Caminar", "Reunión", "Otro")
    }
}

class PatientHomeViewModel(
    private val repository: PatientRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PatientHomeUiState>(PatientHomeUiState.Loading)
    val uiState: StateFlow<PatientHomeUiState> = _uiState.asStateFlow()
    private val refreshController = patientRefreshController()

    init {
        refreshIfStale()
    }

    fun load() = refreshIfStale(force = true)

    fun refreshIfStale(force: Boolean = false) {
        refreshController.refreshIfStale(viewModelScope, force) {
            val previous = _uiState.value as? PatientHomeUiState.Content
            val cached = repository.loadCachedHomeData()
            if (cached != null) {
                _uiState.value = PatientHomeUiState.Content(
                    cached.data,
                    cached.offlineStatus.copy(isRefreshing = true, warning = null),
                )
            } else if (previous == null) {
                _uiState.value = PatientHomeUiState.Loading
            }
            when (val result = repository.loadHomeData()) {
                is PatientDataResult.Success -> {
                    _uiState.value = PatientHomeUiState.Content(result.data, result.offlineStatus)
                    true
                }
                is PatientDataResult.Failure -> {
                    val visible = _uiState.value as? PatientHomeUiState.Content ?: previous
                    _uiState.value = visible?.copy(
                        offlineStatus = visible.offlineStatus.copy(
                            isRefreshing = false,
                            warning = result.message,
                        ),
                    ) ?: PatientHomeUiState.Error(result.message)
                    false
                }
            }
        }
    }

    class Factory(
        private val repository: PatientRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PatientHomeViewModel::class.java))
            return PatientHomeViewModel(repository) as T
        }
    }
}

class MetricsViewModel(
    private val repository: PatientRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MetricsUiState>(MetricsUiState.Loading)
    val uiState: StateFlow<MetricsUiState> = _uiState.asStateFlow()
    private val refreshController = patientRefreshController()

    init {
        refreshIfStale()
    }

    fun load() = refreshIfStale(force = true)

    fun refreshIfStale(force: Boolean = false) {
        refreshController.refreshIfStale(viewModelScope, force) {
            val previous = _uiState.value as? MetricsUiState.Content
            val cached = repository.loadCachedMetricsData()
            if (cached != null) {
                _uiState.value = MetricsUiState.Content(
                    cached.data,
                    cached.offlineStatus.copy(isRefreshing = true, warning = null),
                )
            } else if (previous == null) {
                _uiState.value = MetricsUiState.Loading
            }
            when (val result = repository.loadMetricsData()) {
                is PatientDataResult.Success -> {
                    _uiState.value = MetricsUiState.Content(result.data, result.offlineStatus)
                    true
                }
                is PatientDataResult.Failure -> {
                    val visible = _uiState.value as? MetricsUiState.Content ?: previous
                    _uiState.value = visible?.copy(
                        offlineStatus = visible.offlineStatus.copy(isRefreshing = false, warning = result.message),
                    ) ?: MetricsUiState.Error(result.message)
                    false
                }
            }
        }
    }

    class Factory(
        private val repository: PatientRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MetricsViewModel::class.java))
            return MetricsViewModel(repository) as T
        }
    }
}

class AchievementsViewModel(
    private val repository: PatientRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<AchievementsUiState>(AchievementsUiState.Loading)
    val uiState: StateFlow<AchievementsUiState> = _uiState.asStateFlow()
    private val refreshController = patientRefreshController()

    init {
        refreshIfStale()
    }

    fun load() = refreshIfStale(force = true)

    fun refreshIfStale(force: Boolean = false) {
        refreshController.refreshIfStale(viewModelScope, force) {
            val previous = _uiState.value as? AchievementsUiState.Content
            val cached = repository.loadCachedAchievementsData()
            if (cached != null) {
                _uiState.value = AchievementsUiState.Content(
                    cached.data,
                    cached.offlineStatus.copy(isRefreshing = true, warning = null),
                )
            } else if (previous == null) {
                _uiState.value = AchievementsUiState.Loading
            }
            when (val result = repository.loadAchievementsData()) {
                is PatientDataResult.Success -> {
                    _uiState.value = AchievementsUiState.Content(result.data, result.offlineStatus)
                    true
                }
                is PatientDataResult.Failure -> {
                    val visible = _uiState.value as? AchievementsUiState.Content ?: previous
                    _uiState.value = visible?.copy(
                        offlineStatus = visible.offlineStatus.copy(isRefreshing = false, warning = result.message),
                    ) ?: AchievementsUiState.Error(result.message)
                    false
                }
            }
        }
    }

    class Factory(
        private val repository: PatientRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AchievementsViewModel::class.java))
            return AchievementsViewModel(repository) as T
        }
    }
}

class SupportNetworkViewModel(
    private val repository: PatientRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SupportContactsUiState>(SupportContactsUiState.Loading)
    val uiState: StateFlow<SupportContactsUiState> = _uiState.asStateFlow()
    private val _saveState = MutableStateFlow<FormSaveUiState>(FormSaveUiState.Idle)
    val saveState: StateFlow<FormSaveUiState> = _saveState.asStateFlow()
    private val refreshController = patientRefreshController()

    init {
        refreshIfStale()
    }

    fun load() = refreshIfStale(force = true)

    fun refreshIfStale(force: Boolean = false) {
        refreshController.refreshIfStale(viewModelScope, force) {
            val previous = _uiState.value as? SupportContactsUiState.Content
            val cached = repository.loadCachedSupportContacts()
            if (cached != null) {
                _uiState.value = SupportContactsUiState.Content(
                    cached.data,
                    cached.offlineStatus.copy(isRefreshing = true, warning = null),
                )
            } else if (previous == null) {
                _uiState.value = SupportContactsUiState.Loading
            }
            when (val result = repository.loadSupportContacts()) {
                is PatientDataResult.Success -> {
                    _uiState.value = SupportContactsUiState.Content(result.data, result.offlineStatus)
                    true
                }
                is PatientDataResult.Failure -> {
                    val visible = _uiState.value as? SupportContactsUiState.Content ?: previous
                    _uiState.value = visible?.copy(
                        offlineStatus = visible.offlineStatus.copy(isRefreshing = false, warning = result.message),
                    ) ?: SupportContactsUiState.Error(result.message)
                    false
                }
            }
        }
    }

    fun saveContact(input: SupportContactFormInput) {
        if (_saveState.value is FormSaveUiState.Loading) return

        val request = input.toRequestOrError()
        if (request == null) return

        viewModelScope.launch {
            _saveState.value = FormSaveUiState.Loading
            val preserveLegacyPriority = shouldPreserveLegacySupportPriority(
                contactId = input.contactId,
                originalPriority = input.originalPriority,
                enteredPriority = input.priority,
            )
            _saveState.value = when (
                val result = repository.saveSupportContact(
                    request = request,
                    contactId = input.contactId,
                    preserveLegacyPriority = preserveLegacyPriority,
                )
            ) {
                is PatientDataResult.Success -> {
                    invalidatePatientRefreshes()
                    refreshIfStale(force = true)
                    FormSaveUiState.Success(result.data)
                }

                is PatientDataResult.Failure -> FormSaveUiState.Error(result.message, result.fieldErrors)
            }
        }
    }

    fun clearSaveMessage() {
        if (_saveState.value !is FormSaveUiState.Loading) {
            _saveState.value = FormSaveUiState.Idle
        }
    }

    private fun SupportContactFormInput.toRequestOrError(): CreateSupportContactRequest? {
        val cleanName = name.cleanFormText(maxLength = 80)
        val cleanRelationship = relationship.cleanFormText(maxLength = 60)
        val validatedPhone = PhoneNumberValidator.validate(phoneRegion, phone)
        val parsedPriority = priority.toIntOrNull()
        val preserveLegacyPriority = shouldPreserveLegacySupportPriority(
            contactId = contactId,
            originalPriority = originalPriority,
            enteredPriority = priority,
        )
        val priorityValue = parsedPriority?.takeIf { it in 1..5 }
        val cleanNotes = notes.cleanFormText(maxLength = 180)
        val errors = buildMap {
            if (cleanName == null || !cleanName.isHumanLabel(minLength = 2, maxLength = 80)) {
                put("name", "Escribe un nombre válido, solo letras y espacios.")
            }
            if (cleanRelationship == null || !cleanRelationship.isHumanLabel(minLength = 2, maxLength = 60)) {
                put("relationship", "Indica una relación válida.")
            }
            if (relationshipCategory !in CONTACT_RELATIONSHIP_OPTIONS.keys) {
                put("relationship", "Selecciona una categoría de relación válida.")
            }
            if (
                relationshipCategory == CONTACT_RELATIONSHIP_OTHER &&
                relationshipOther.cleanFormText(maxLength = 60) == null
            ) {
                put("relationship", "Describe la relación con el contacto.")
            }
            validatedPhone.error?.let { put("phone", it) }
            if (priorityValue == null && !preserveLegacyPriority) {
                put("priority", "Selecciona una prioridad válida.")
            }
        }

        if (errors.isNotEmpty()) {
            _saveState.value = FormSaveUiState.Error(
                message = "Revisa los datos del contacto.",
                fieldErrors = errors,
            )
            return null
        }

        return CreateSupportContactRequest(
            name = cleanName!!,
            relationship = cleanRelationship!!,
            phone = checkNotNull(validatedPhone.value),
            // The repository omits this field from a legacy-only PATCH. It is never sent as 1.
            priority = priorityValue ?: 1,
            canReceiveAlerts = canReceiveAlerts,
            notes = cleanNotes,
            relationshipCategory = relationshipCategory,
            relationshipOther = relationshipOther.cleanFormText(maxLength = 60)
                .takeIf { relationshipCategory == CONTACT_RELATIONSHIP_OTHER },
        )
    }

    class Factory(
        private val repository: PatientRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SupportNetworkViewModel::class.java))
            return SupportNetworkViewModel(repository) as T
        }
    }
}

class LocalDirectoryViewModel(
    private val repository: PatientRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<LocalResourcesUiState>(LocalResourcesUiState.Loading)
    val uiState: StateFlow<LocalResourcesUiState> = _uiState.asStateFlow()
    private val refreshController = patientRefreshController()

    init {
        refreshIfStale()
    }

    fun load() = refreshIfStale(force = true)

    fun refreshIfStale(force: Boolean = false) {
        refreshController.refreshIfStale(viewModelScope, force) {
            val previous = _uiState.value as? LocalResourcesUiState.Content
            val cached = repository.loadCachedLocalResources()
            if (cached != null) {
                _uiState.value = LocalResourcesUiState.Content(
                    cached.data,
                    cached.offlineStatus.copy(isRefreshing = true, warning = null),
                )
            } else if (previous == null) {
                _uiState.value = LocalResourcesUiState.Loading
            }
            when (val result = repository.loadLocalResources()) {
                is PatientDataResult.Success -> {
                    _uiState.value = LocalResourcesUiState.Content(result.data, result.offlineStatus)
                    true
                }
                is PatientDataResult.Failure -> {
                    val visible = _uiState.value as? LocalResourcesUiState.Content ?: previous
                    _uiState.value = visible?.copy(
                        offlineStatus = visible.offlineStatus.copy(isRefreshing = false, warning = result.message),
                    ) ?: LocalResourcesUiState.Error(result.message)
                    false
                }
            }
        }
    }

    class Factory(
        private val repository: PatientRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(LocalDirectoryViewModel::class.java))
            return LocalDirectoryViewModel(repository) as T
        }
    }
}

class AgendaViewModel(
    private val repository: PatientRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<AgendaEventsUiState>(AgendaEventsUiState.Loading)
    val uiState: StateFlow<AgendaEventsUiState> = _uiState.asStateFlow()
    private val refreshController = patientRefreshController()

    init {
        refreshIfStale()
    }

    fun load() = refreshIfStale(force = true)

    fun refreshIfStale(force: Boolean = false) {
        refreshController.refreshIfStale(viewModelScope, force) {
            val previous = _uiState.value as? AgendaEventsUiState.Content
            val cached = repository.loadCachedAgendaEvents()
            if (cached != null) {
                _uiState.value = AgendaEventsUiState.Content(
                    cached.data,
                    cached.offlineStatus.copy(isRefreshing = true, warning = null),
                )
            } else if (previous == null) {
                _uiState.value = AgendaEventsUiState.Loading
            }
            when (val result = repository.loadAgendaEvents()) {
                is PatientDataResult.Success -> {
                    _uiState.value = AgendaEventsUiState.Content(result.data, result.offlineStatus)
                    true
                }
                is PatientDataResult.Failure -> {
                    val visible = _uiState.value as? AgendaEventsUiState.Content ?: previous
                    _uiState.value = visible?.copy(
                        offlineStatus = visible.offlineStatus.copy(isRefreshing = false, warning = result.message),
                    ) ?: AgendaEventsUiState.Error(result.message)
                    false
                }
            }
        }
    }

    fun completeEvent(eventId: String) {
        updateStatus(eventId, "completed")
    }

    fun cancelEvent(eventId: String) {
        updateStatus(eventId, "cancelled")
    }

    fun reportAttendance(eventId: String, attended: Boolean) {
        viewModelScope.launch {
            val previousState = _uiState.value
            when (val result = repository.updateAgendaEventAttendance(eventId, attended)) {
                is PatientDataResult.Success -> {
                    invalidatePatientRefreshes()
                    refreshIfStale(force = true)
                }
                is PatientDataResult.Failure -> {
                    if (previousState is AgendaEventsUiState.Content) {
                        _uiState.value = previousState.copy(
                            offlineStatus = previousState.offlineStatus.copy(warning = result.message),
                        )
                    } else {
                        _uiState.value = AgendaEventsUiState.Error(result.message)
                    }
                }
            }
        }
    }

    private fun updateStatus(
        eventId: String,
        status: String,
    ) {
        viewModelScope.launch {
            val previousState = _uiState.value
            when (val result = repository.updateAgendaEventStatus(eventId, status)) {
                is PatientDataResult.Success -> {
                    invalidatePatientRefreshes()
                    refreshIfStale(force = true)
                }
                is PatientDataResult.Failure -> {
                    if (previousState is AgendaEventsUiState.Content) {
                        _uiState.value = previousState.copy(
                            offlineStatus = previousState.offlineStatus.copy(warning = result.message),
                        )
                    } else {
                        _uiState.value = AgendaEventsUiState.Error(result.message)
                    }
                }
            }
        }
    }

    class Factory(
        private val repository: PatientRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AgendaViewModel::class.java))
            return AgendaViewModel(repository) as T
        }
    }
}

class NewEventViewModel(
    private val repository: PatientRepository,
) : ViewModel() {
    private val _saveState = MutableStateFlow<FormSaveUiState>(FormSaveUiState.Idle)
    val saveState: StateFlow<FormSaveUiState> = _saveState.asStateFlow()

    fun saveEvent(input: EventFormInput) {
        if (_saveState.value is FormSaveUiState.Loading) return

        val request = input.toRequestOrError()
        if (request == null) return

        viewModelScope.launch {
            _saveState.value = FormSaveUiState.Loading
            _saveState.value = when (val result = repository.saveAgendaEvent(request)) {
                is PatientDataResult.Success -> {
                    invalidatePatientRefreshes()
                    FormSaveUiState.Success(result.data)
                }
                is PatientDataResult.Failure -> FormSaveUiState.Error(result.message, result.fieldErrors)
            }
        }
    }

    fun clearSaveMessage() {
        if (_saveState.value !is FormSaveUiState.Loading) {
            _saveState.value = FormSaveUiState.Idle
        }
    }

    private fun EventFormInput.toRequestOrError(): CreateAgendaEventRequest? {
        val cleanTitle = title.cleanFormText(maxLength = 120)
        val apiType = EVENT_TYPE_MAP[type]
        val cleanLocation = location.cleanFormText(maxLength = 120)
        val cleanNotes = notes.cleanFormText(maxLength = 240)
        val durationValue = durationMinutes.toIntOrNull()?.takeIf { it in ALLOWED_DURATIONS }
        val parsedDate = date.toLocalDateOrNull()
        val parsedTime = startTime.toLocalTimeOrNull()
        val startDateTime = if (parsedDate != null && parsedTime != null) {
            LocalDateTime.of(parsedDate, parsedTime)
                .atZone(ZoneId.systemDefault())
        } else {
            null
        }
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val errors = buildMap {
            if (cleanTitle == null || cleanTitle.length < 3) {
                put("title", "Escribe un nombre de al menos 3 caracteres.")
            }
            if (apiType == null) {
                put("type", "Selecciona un tipo válido.")
            }
            if (parsedDate == null) {
                put("date", "Usa fecha con formato AAAA-MM-DD.")
            } else if (parsedDate.isBefore(LocalDate.now())) {
                put("date", "La fecha no puede estar en el pasado.")
            }
            if (parsedTime == null) {
                put("startTime", "Elige una hora válida.")
            }
            if (startDateTime != null && startDateTime.isBefore(now.minusMinutes(1))) {
                put("startTime", "La hora del evento no puede estar en el pasado.")
            }
            if (durationValue == null) {
                put("duration", "Selecciona una duración válida.")
            }
            if (cleanLocation == null || cleanLocation.length < 3) {
                put("location", "Agrega una ubicación o medio de contacto.")
            }
        }

        if (errors.isNotEmpty()) {
            _saveState.value = FormSaveUiState.Error(
                message = "Revisa los datos del evento.",
                fieldErrors = errors,
            )
            return null
        }

        val startsAt = startDateTime!!
        val endsAt = startsAt.plusMinutes(durationValue!!.toLong())
        return CreateAgendaEventRequest(
            type = apiType!!,
            title = cleanTitle!!,
            startsAt = startsAt.toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            endsAt = endsAt.toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            location = cleanLocation!!,
            notes = cleanNotes,
        )
    }

    class Factory(
        private val repository: PatientRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(NewEventViewModel::class.java))
            return NewEventViewModel(repository) as T
        }
    }

    private companion object {
        val EVENT_TYPE_MAP = mapOf(
            "Reunión" to "support_meeting",
            "Cita" to "appointment",
            "Grupo" to "group",
            "Llamada" to "call",
        )
        val ALLOWED_DURATIONS = setOf(30, 60, 90, 120)
    }
}

class SupervisorLinkViewModel(
    private val repository: PatientRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SupervisorLinkUiState>(SupervisorLinkUiState.Idle)
    val uiState: StateFlow<SupervisorLinkUiState> = _uiState.asStateFlow()
    private var lastKnownStatus = PatientSupervisionState.NoSupervisor

    init {
        refreshStatus()
    }

    fun requestLink(supervisorCode: String) {
        if (_uiState.value is SupervisorLinkUiState.Loading) return

        viewModelScope.launch {
            val normalizedCode = supervisorCode.trim().uppercase()
            val currentPreview = (_uiState.value as? SupervisorLinkUiState.Preview)
                ?.supervisor
            _uiState.value = SupervisorLinkUiState.Loading
            if (currentPreview?.code == normalizedCode) {
                submitLink(currentPreview.supervisorUid)
                return@launch
            }

            when (val result = repository.resolveSupervisorCode(normalizedCode)) {
                is PatientDataResult.Success -> {
                    _uiState.value = SupervisorLinkUiState.Preview(result.data)
                }
                is PatientDataResult.Failure -> {
                    _uiState.value = SupervisorLinkUiState.Error(result.message, result.fieldErrors)
                }
            }
        }
    }

    private suspend fun submitLink(supervisorReference: String) {
        _uiState.value = when (val result = repository.requestSupervisorLink(supervisorReference)) {
            is PatientDataResult.Success -> {
                invalidatePatientRefreshes()
                SupervisorLinkUiState.Success(result.data)
            }
            is PatientDataResult.Failure -> SupervisorLinkUiState.Error(result.message, result.fieldErrors)
        }
    }

    fun clearMessage() {
        if (_uiState.value !is SupervisorLinkUiState.Loading) {
            _uiState.value = SupervisorLinkUiState.Status(lastKnownStatus)
        }
    }

    private fun refreshStatus() {
        viewModelScope.launch {
            _uiState.value = SupervisorLinkUiState.Loading
            _uiState.value = when (val result = repository.loadProfileData()) {
                is PatientDataResult.Success -> {
                    lastKnownStatus = result.data.supervisionState
                    SupervisorLinkUiState.Status(lastKnownStatus)
                }
                is PatientDataResult.Failure -> SupervisorLinkUiState.Error(result.message, result.fieldErrors)
            }
        }
    }

    class Factory(
        private val repository: PatientRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SupervisorLinkViewModel::class.java))
            return SupervisorLinkViewModel(repository) as T
        }
    }
}

class PatientProfileViewModel(
    private val repository: PatientRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PatientProfileUiState>(PatientProfileUiState.Loading)
    val uiState: StateFlow<PatientProfileUiState> = _uiState.asStateFlow()
    private val _consentActionState = MutableStateFlow<FormSaveUiState>(FormSaveUiState.Idle)
    val consentActionState: StateFlow<FormSaveUiState> = _consentActionState.asStateFlow()
    private val _privacyActionState = MutableStateFlow<FormSaveUiState>(FormSaveUiState.Idle)
    val privacyActionState: StateFlow<FormSaveUiState> = _privacyActionState.asStateFlow()
    private val _profileActionState = MutableStateFlow<FormSaveUiState>(FormSaveUiState.Idle)
    val profileActionState: StateFlow<FormSaveUiState> = _profileActionState.asStateFlow()
    private val refreshController = patientRefreshController()

    init {
        refreshIfStale()
    }

    fun load() = refreshIfStale(force = true)

    fun refreshIfStale(force: Boolean = false) {
        refreshController.refreshIfStale(viewModelScope, force) {
            val previous = _uiState.value as? PatientProfileUiState.Content
            val cached = repository.loadCachedProfileData()
            if (cached != null) {
                _uiState.value = PatientProfileUiState.Content(
                    profile = cached.data,
                    isRefreshing = true,
                )
            } else if (previous == null) {
                _uiState.value = PatientProfileUiState.Loading
            } else {
                _uiState.value = previous.copy(isRefreshing = true, warning = null)
            }
            when (val result = repository.loadProfileData()) {
                is PatientDataResult.Success -> {
                    _uiState.value = PatientProfileUiState.Content(result.data)
                    true
                }
                is PatientDataResult.Failure -> {
                    if ((cached == null && previous == null) || result.statusCode in setOf(401, 403)) {
                        PatientProfileUiState.Error(result.message)
                    } else {
                        PatientProfileUiState.Content(
                            profile = (cached?.data ?: previous!!.profile).copy(isStale = true),
                            warning = result.message,
                        )
                    }
                    false
                }
            }
        }
    }

    fun setConsent(
        enabled: Boolean,
        selectedScopes: Set<String> = emptySet(),
    ) {
        if (_consentActionState.value is FormSaveUiState.Loading) return

        viewModelScope.launch {
            _consentActionState.value = FormSaveUiState.Loading
            _consentActionState.value = when (
                val result = repository.setSupervisorConsent(
                    enabled = enabled,
                    selectedScopes = selectedScopes.takeIf { enabled },
                )
            ) {
                is PatientDataResult.Success -> {
                    invalidatePatientRefreshes()
                    refreshIfStale(force = true)
                    FormSaveUiState.Success(result.data)
                }
                is PatientDataResult.Failure -> profileActionFailure(result)
            }
        }
    }

    fun cancelPendingSupervisorRequest(requestId: String) {
        if (_consentActionState.value is FormSaveUiState.Loading) return

        viewModelScope.launch {
            _consentActionState.value = FormSaveUiState.Loading
            _consentActionState.value = when (
                val result = repository.cancelPendingSupervisorRequest(requestId)
            ) {
                is PatientDataResult.Success -> {
                    invalidatePatientRefreshes()
                    refreshIfStale(force = true)
                    FormSaveUiState.Success(result.data)
                }
                is PatientDataResult.Failure -> profileActionFailure(result)
            }
        }
    }

    fun revokeConsent() {
        if (_consentActionState.value is FormSaveUiState.Loading) return

        viewModelScope.launch {
            _consentActionState.value = FormSaveUiState.Loading
            _consentActionState.value = when (val result = repository.revokeSupervisorConsent()) {
                is PatientDataResult.Success -> {
                    invalidatePatientRefreshes()
                    refreshIfStale(force = true)
                    FormSaveUiState.Success(result.data)
                }
                is PatientDataResult.Failure -> profileActionFailure(result)
            }
        }
    }

    fun requestSupervisorUnlink() {
        if (_consentActionState.value is FormSaveUiState.Loading) return

        viewModelScope.launch {
            _consentActionState.value = FormSaveUiState.Loading
            _consentActionState.value = when (val result = repository.requestSupervisorUnlink()) {
                is PatientDataResult.Success -> {
                    invalidatePatientRefreshes()
                    refreshIfStale(force = true)
                    FormSaveUiState.Success(result.data)
                }
                is PatientDataResult.Failure -> profileActionFailure(result)
            }
        }
    }

    fun clearConsentMessage() {
        if (_consentActionState.value !is FormSaveUiState.Loading) {
            _consentActionState.value = FormSaveUiState.Idle
        }
    }

    fun savePrivacy(input: PrivacyFormInput) {
        if (_privacyActionState.value is FormSaveUiState.Loading) return

        val anonymous = input.mode == PRIVACY_ANONYMOUS
        val currentProfileName = (_uiState.value as? PatientProfileUiState.Content)
            ?.profile
            ?.registeredName
            .orEmpty()
        val displayNameSource = when (input.mode) {
            PRIVACY_VISIBLE -> currentProfileName
            PRIVACY_NICKNAME -> input.displayName
            else -> "Paciente anónimo"
        }
        val cleanDisplayName = displayNameSource.cleanFormText(maxLength = 80)
        if (input.mode == PRIVACY_NICKNAME && (cleanDisplayName == null || !cleanDisplayName.isSafeDisplayName())) {
            _privacyActionState.value = FormSaveUiState.Error(
                message = "Revisa tu modo de privacidad.",
                fieldErrors = mapOf("displayName" to "Escribe un nombre o nickname válido."),
            )
            return
        }

        viewModelScope.launch {
            _privacyActionState.value = FormSaveUiState.Loading
            _privacyActionState.value = when (
                val result = repository.updatePrivacyMode(
                    displayName = cleanDisplayName ?: "Paciente anónimo",
                    anonymous = anonymous,
                    useNickname = input.mode == PRIVACY_NICKNAME,
                )
                ) {
                is PatientDataResult.Success -> {
                    invalidatePatientRefreshes()
                    repository.loadCachedProfileData()?.let { cached ->
                        _uiState.value = PatientProfileUiState.Content(
                            profile = cached.data,
                            isRefreshing = true,
                        )
                    }
                    refreshIfStale(force = true)
                    FormSaveUiState.Success(result.data)
                }
                is PatientDataResult.Failure -> profileActionFailure(result)
            }
        }
    }

    private fun profileActionFailure(result: PatientDataResult.Failure): FormSaveUiState.Error {
        if (result.statusCode in setOf(401, 403)) {
            _uiState.value = PatientProfileUiState.Error(result.message)
        }
        return FormSaveUiState.Error(result.message, result.fieldErrors)
    }

    fun clearPrivacyMessage() {
        if (_privacyActionState.value !is FormSaveUiState.Loading) {
            _privacyActionState.value = FormSaveUiState.Idle
        }
    }

    fun saveProfile(input: EditableProfileFormInput) {
        if (_profileActionState.value is FormSaveUiState.Loading) return

        val validation = ProfileFormValidator.validate(
            input = input,
            includeSupervisorType = false,
        )
        val profile = validation.value
        if (profile == null) {
            _profileActionState.value = FormSaveUiState.Error(
                message = "Revisa los datos del perfil.",
                fieldErrors = validation.fieldErrors,
            )
            return
        }

        viewModelScope.launch {
            _profileActionState.value = FormSaveUiState.Loading
            _profileActionState.value = when (
                val result = repository.updateOwnProfile(
                    profile.fullName,
                    profile.phone,
                    profile.sobrietyStartDate,
                )
            ) {
                is PatientDataResult.Success -> {
                    invalidatePatientRefreshes()
                    refreshIfStale(force = true)
                    FormSaveUiState.Success(result.data)
                }
                is PatientDataResult.Failure -> profileActionFailure(result)
            }
        }
    }

    fun clearProfileMessage() {
        if (_profileActionState.value !is FormSaveUiState.Loading) {
            _profileActionState.value = FormSaveUiState.Idle
        }
    }

    class Factory(
        private val repository: PatientRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PatientProfileViewModel::class.java))
            return PatientProfileViewModel(repository) as T
        }
    }

    private companion object {
        const val PRIVACY_VISIBLE = "Nombre visible"
        const val PRIVACY_NICKNAME = "Nickname"
        const val PRIVACY_ANONYMOUS = "Ocultar identidad"
    }
}

class NotificationsViewModel(
    private val repository: PatientRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<NotificationSettingsUiState>(NotificationSettingsUiState.Loading)
    val uiState: StateFlow<NotificationSettingsUiState> = _uiState.asStateFlow()
    private val _saveState = MutableStateFlow<FormSaveUiState>(FormSaveUiState.Idle)
    val saveState: StateFlow<FormSaveUiState> = _saveState.asStateFlow()
    private val refreshController = patientRefreshController()

    init {
        refreshIfStale()
    }

    fun load() = refreshIfStale(force = true)

    fun refreshIfStale(force: Boolean = false) {
        refreshController.refreshIfStale(viewModelScope, force) {
            val previous = _uiState.value as? NotificationSettingsUiState.Content
            val cachedSettings = repository.loadCachedNotificationSettings()
            val cachedEvents = repository.loadCachedAgendaEvents()
            if (cachedSettings != null) {
                _uiState.value = NotificationSettingsUiState.Content(
                    settings = cachedSettings.data,
                    events = cachedEvents?.data.orEmpty(),
                    offlineStatus = cachedSettings.offlineStatus.copy(isRefreshing = true, warning = null),
                )
            } else if (previous == null) {
                _uiState.value = NotificationSettingsUiState.Loading
            }
            val settingsResult = repository.loadNotificationSettings()
            if (settingsResult is PatientDataResult.Failure) {
                val visible = _uiState.value as? NotificationSettingsUiState.Content ?: previous
                _uiState.value = visible?.copy(
                    offlineStatus = visible.offlineStatus.copy(
                        isRefreshing = false,
                        warning = settingsResult.message,
                    ),
                ) ?: NotificationSettingsUiState.Error(settingsResult.message)
                return@refreshIfStale false
            }

            val events = when (val eventsResult = repository.loadAgendaEvents()) {
                is PatientDataResult.Success -> eventsResult.data
                is PatientDataResult.Failure -> emptyList()
            }
            _uiState.value = NotificationSettingsUiState.Content(
                settings = (settingsResult as PatientDataResult.Success).data,
                events = events,
                offlineStatus = settingsResult.offlineStatus,
            )
            true
        }
    }

    fun save(input: NotificationSettingsFormInput) {
        if (_saveState.value is FormSaveUiState.Loading) return

        val currentSettings = (_uiState.value as? NotificationSettingsUiState.Content)?.settings
        val settings = input.toSettingsOrError(currentSettings) ?: return

        viewModelScope.launch {
            _saveState.value = FormSaveUiState.Loading
            when (val result = repository.saveNotificationSettings(settings)) {
                is PatientDataResult.Success -> {
                    invalidatePatientRefreshes()
                    val events = when (val eventsResult = repository.loadAgendaEvents()) {
                        is PatientDataResult.Success -> eventsResult.data
                        is PatientDataResult.Failure -> emptyList()
                    }
                    _uiState.value = NotificationSettingsUiState.Content(
                        result.data,
                        events,
                        result.offlineStatus,
                    )
                    refreshController.markFresh()
                    _saveState.value = FormSaveUiState.Success("Configuración de notificaciones guardada.")
                }
                is PatientDataResult.Failure -> {
                    _saveState.value = FormSaveUiState.Error(result.message, result.fieldErrors)
                }
            }
        }
    }

    fun clearSaveMessage() {
        if (_saveState.value !is FormSaveUiState.Loading) {
            _saveState.value = FormSaveUiState.Idle
        }
    }

    private fun NotificationSettingsFormInput.toSettingsOrError(
        currentSettings: NotificationSettingsData?,
    ): NotificationSettingsData? {
        val dailyTime = dailyNoteTime.cleanTimeOrNull()
        val soberTime = soberDayTime.cleanTimeOrNull()
        val achievementTimeValue = achievementTime.cleanTimeOrNull()
        val motivationalTimeValue = motivationalTime.cleanTimeOrNull()
        val inactivityTimeValue = inactivityReminderTime.cleanTimeOrNull()
        val inactivityDaysValue = inactivityDays.toIntOrNull()
        val fieldErrors = buildMap {
            if (dailyNoteEnabled && dailyTime == null) put("dailyNoteTime", "Elige una hora válida.")
            if (soberDayEnabled && soberTime == null) put("soberDayTime", "Elige una hora válida.")
            if (achievementEnabled && achievementTimeValue == null) put("achievementTime", "Elige una hora válida.")
            if (motivationalEnabled && motivationalTimeValue == null) put("motivationalTime", "Elige una hora válida.")
            if (inactivityReminderEnabled && inactivityTimeValue == null) {
                put("inactivityReminderTime", "Elige una hora válida.")
            }
            if (inactivityReminderEnabled && inactivityDaysValue !in 2..14) {
                put("inactivityDays", "Usa un valor de 2 a 14 días.")
            }
        }

        if (fieldErrors.isNotEmpty()) {
            _saveState.value = FormSaveUiState.Error(
                message = "Revisa los horarios antes de guardar.",
                fieldErrors = fieldErrors,
            )
            return null
        }

        return NotificationSettingsData(
            id = currentSettings?.id,
            dailyNoteEnabled = dailyNoteEnabled,
            dailyNoteTime = dailyTime ?: currentSettings?.dailyNoteTime ?: "20:00",
            soberDayEnabled = soberDayEnabled,
            soberDayTime = soberTime ?: currentSettings?.soberDayTime ?: "09:00",
            achievementEnabled = achievementEnabled,
            achievementTime = achievementTimeValue ?: currentSettings?.achievementTime ?: "09:30",
            eventRemindersEnabled = eventRemindersEnabled,
            motivationalEnabled = motivationalEnabled,
            motivationalTime = motivationalTimeValue ?: currentSettings?.motivationalTime ?: "18:00",
            adaptiveRulesAvailable = currentSettings?.adaptiveRulesAvailable ?: false,
            adaptiveRulesVersion = currentSettings?.adaptiveRulesVersion,
            adaptiveSuggestionsEnabled = adaptiveSuggestionsEnabled,
            inactivityReminderEnabled = inactivityReminderEnabled,
            inactivityDays = inactivityDaysValue ?: currentSettings?.inactivityDays ?: 3,
            inactivityReminderTime = inactivityTimeValue
                ?: currentSettings?.inactivityReminderTime
                ?: "18:00",
            adaptiveCravingThreshold = currentSettings?.adaptiveCravingThreshold ?: 8,
            adaptiveAnxietyThreshold = currentSettings?.adaptiveAnxietyThreshold ?: 8,
            timezone = currentSettings?.timezone ?: "America/Mexico_City",
        )
    }

    private fun String.cleanTimeOrNull(): String? {
        val trimmed = trim().take(5)
        return trimmed.takeIf { Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(it) }
    }

    class Factory(
        private val repository: PatientRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(NotificationsViewModel::class.java))
            return NotificationsViewModel(repository) as T
        }
    }
}

private fun String.cleanFormText(maxLength: Int): String? {
    return filterNot { it.isISOControl() }
        .replace("<", "")
        .replace(">", "")
        .trim()
        .take(maxLength)
        .takeIf { it.isNotBlank() }
}

private fun String.isHumanLabel(minLength: Int, maxLength: Int): Boolean {
    if (length !in minLength..maxLength) return false

    return all { char ->
        char.isLetter() ||
            char.isWhitespace() ||
            char == '.' ||
            char == '\'' ||
            char == '-'
    }
}

private fun String.isSafeDisplayName(): Boolean {
    if (length !in 2..80) return false

    return all { char ->
        char.isLetterOrDigit() ||
            char.isWhitespace() ||
            char == '.' ||
            char == '\'' ||
            char == '-' ||
            char == '_'
    }
}

private fun String.toLocalDateOrNull(): LocalDate? {
    return try {
        LocalDate.parse(trim())
    } catch (_: DateTimeParseException) {
        null
    }
}

private fun String.toLocalTimeOrNull(): LocalTime? {
    return try {
        LocalTime.parse(trim())
    } catch (_: DateTimeParseException) {
        null
    }
}
