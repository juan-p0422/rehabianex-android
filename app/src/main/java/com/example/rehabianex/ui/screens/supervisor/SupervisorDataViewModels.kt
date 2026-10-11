package com.example.rehabianex.ui.screens.supervisor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.rehabianex.core.performance.StaleRefreshController
import com.example.rehabianex.core.performance.SUPERVISOR_REFRESH_SCOPE
import com.example.rehabianex.core.performance.RefreshInvalidationRegistry
import com.example.rehabianex.data.model.NotificationSettingsData
import com.example.rehabianex.data.model.SupervisorChatData
import com.example.rehabianex.data.model.SupervisorChatMessage
import com.example.rehabianex.data.model.SupervisorHomeData
import com.example.rehabianex.data.model.SupervisorPatientDetailData
import com.example.rehabianex.data.model.SupervisorPatientsData
import com.example.rehabianex.data.model.SupervisorProfileData
import com.example.rehabianex.data.repository.SupervisorDataResult
import com.example.rehabianex.data.repository.SupervisorRepository
import com.example.rehabianex.core.validation.MAX_AI_CHAT_QUESTION_LENGTH
import com.example.rehabianex.core.validation.sanitizeAiChatQuestion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private fun supervisorRefreshController() = StaleRefreshController(
    invalidationScope = SUPERVISOR_REFRESH_SCOPE,
)

private fun invalidateSupervisorRefreshes() {
    RefreshInvalidationRegistry.invalidate(SUPERVISOR_REFRESH_SCOPE)
}

sealed interface SupervisorHomeUiState {
    data object Loading : SupervisorHomeUiState
    data class Content(val data: SupervisorHomeData) : SupervisorHomeUiState
    data class Error(val message: String) : SupervisorHomeUiState
}

sealed interface SupervisorPatientsUiState {
    data object Loading : SupervisorPatientsUiState
    data class Content(
        val data: SupervisorPatientsData,
        val isRefreshing: Boolean = false,
        val lastSyncedAt: Long? = null,
        val errorMessage: String? = null,
    ) : SupervisorPatientsUiState
    data class Error(val message: String) : SupervisorPatientsUiState
}

sealed interface SupervisorPatientDetailUiState {
    data object Loading : SupervisorPatientDetailUiState
    data class Content(
        val data: SupervisorPatientDetailData,
        val isRefreshing: Boolean = false,
        val lastSyncedAt: Long? = null,
        val errorMessage: String? = null,
    ) : SupervisorPatientDetailUiState
    data class Error(val message: String) : SupervisorPatientDetailUiState
}

sealed interface SupervisorProfileUiState {
    data object Loading : SupervisorProfileUiState
    data class Content(val data: SupervisorProfileData) : SupervisorProfileUiState
    data class Error(val message: String) : SupervisorProfileUiState
}

data class SupervisorNotificationsData(
    val settings: NotificationSettingsData,
    val homeData: SupervisorHomeData,
)

sealed interface SupervisorNotificationsUiState {
    data object Loading : SupervisorNotificationsUiState
    data class Content(
        val data: SupervisorNotificationsData,
        val isRefreshing: Boolean = false,
        val lastSyncedAt: Long? = null,
        val errorMessage: String? = null,
    ) : SupervisorNotificationsUiState
    data class Error(val message: String) : SupervisorNotificationsUiState
}

data class SupervisorNotificationSettingsFormInput(
    val dailySummaryEnabled: Boolean,
    val dailySummaryTime: String,
    val relapseAlertEnabled: Boolean,
    val relapseAlertTime: String,
    val pendingRequestsEnabled: Boolean,
    val pendingRequestsTime: String,
    val followUpEnabled: Boolean,
    val followUpTime: String,
)

sealed interface RequestActionUiState {
    data object Idle : RequestActionUiState
    data object Loading : RequestActionUiState
    data class Success(val message: String) : RequestActionUiState
    data class Error(val message: String) : RequestActionUiState
}

data class SupervisorChatUiState(
    val loading: Boolean = true,
    val data: SupervisorChatData? = null,
    val question: String = "",
    val mode: String = "ai",
    val sending: Boolean = false,
    val errorMessage: String? = null,
    val isRefreshing: Boolean = false,
    val lastSyncedAt: Long? = null,
)

class SupervisorHomeViewModel(
    private val repository: SupervisorRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SupervisorHomeUiState>(SupervisorHomeUiState.Loading)
    val uiState: StateFlow<SupervisorHomeUiState> = _uiState.asStateFlow()

    private val _actionState = MutableStateFlow<RequestActionUiState>(RequestActionUiState.Idle)
    val actionState: StateFlow<RequestActionUiState> = _actionState.asStateFlow()
    private val refreshController = supervisorRefreshController()

    init {
        refreshIfStale()
    }

    fun load() = refreshIfStale(force = true)

    fun refreshIfStale(force: Boolean = false) {
        refreshController.refreshIfStale(viewModelScope, force) {
            val previous = (_uiState.value as? SupervisorHomeUiState.Content)?.data
            val cached = repository.loadCachedHomeData()
            val cachedData = (cached as? SupervisorDataResult.Success)?.data
            _uiState.value = cachedData?.let(SupervisorHomeUiState::Content)
                ?: previous?.copy(isRefreshing = true, refreshMessage = null)
                    ?.let(SupervisorHomeUiState::Content)
                ?: SupervisorHomeUiState.Loading
            when (val result = repository.loadHomeData()) {
                is SupervisorDataResult.Success -> {
                    _uiState.value = SupervisorHomeUiState.Content(result.data)
                    true
                }
                is SupervisorDataResult.Failure -> {
                    val visible = cachedData ?: previous
                    _uiState.value = visible?.takeUnless { result.statusCode in setOf(401, 403) }?.let {
                    SupervisorHomeUiState.Content(
                        it.copy(isRefreshing = false, refreshMessage = result.message),
                    )
                    } ?: SupervisorHomeUiState.Error(result.message)
                    false
                }
            }
        }
    }

    fun respondRequest(requestId: String, accepted: Boolean) {
        if (_actionState.value is RequestActionUiState.Loading) return

        viewModelScope.launch {
            _actionState.value = RequestActionUiState.Loading
            _actionState.value = when (val result = repository.respondRequest(requestId, accepted)) {
                is SupervisorDataResult.Success -> {
                    invalidateSupervisorRefreshes()
                    refreshIfStale(force = true)
                    RequestActionUiState.Success(result.data)
                }

                is SupervisorDataResult.Failure -> RequestActionUiState.Error(result.message)
            }
        }
    }

    class Factory(
        private val repository: SupervisorRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SupervisorHomeViewModel::class.java))
            return SupervisorHomeViewModel(repository) as T
        }
    }
}

class PatientsListViewModel(
    private val repository: SupervisorRepository,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow<SupervisorPatientsUiState>(SupervisorPatientsUiState.Loading)
    val uiState: StateFlow<SupervisorPatientsUiState> = _uiState.asStateFlow()

    private val _actionState = MutableStateFlow<RequestActionUiState>(RequestActionUiState.Idle)
    val actionState: StateFlow<RequestActionUiState> = _actionState.asStateFlow()
    private val refreshController = supervisorRefreshController()

    init {
        refreshIfStale()
    }

    fun load() = refreshIfStale(force = true)

    fun refreshIfStale(force: Boolean = false) {
        refreshController.refreshIfStale(viewModelScope, force) {
            val previous = _uiState.value as? SupervisorPatientsUiState.Content
            _uiState.value = previous?.copy(isRefreshing = true, errorMessage = null)
                ?: SupervisorPatientsUiState.Loading
            when (val result = repository.loadPatientsData()) {
                is SupervisorDataResult.Success -> {
                    _uiState.value = SupervisorPatientsUiState.Content(
                        data = result.data,
                        lastSyncedAt = System.currentTimeMillis(),
                    )
                    true
                }
                is SupervisorDataResult.Failure -> {
                    _uiState.value = previous?.takeUnless { result.statusCode in setOf(401, 403) }?.copy(
                        isRefreshing = false,
                        errorMessage = result.message,
                    ) ?: SupervisorPatientsUiState.Error(result.message)
                    false
                }
            }
        }
    }

    fun respondRequest(requestId: String, accepted: Boolean) {
        if (_actionState.value is RequestActionUiState.Loading) return

        viewModelScope.launch {
            _actionState.value = RequestActionUiState.Loading
            _actionState.value = when (val result = repository.respondRequest(requestId, accepted)) {
                is SupervisorDataResult.Success -> {
                    invalidateSupervisorRefreshes()
                    refreshIfStale(force = true)
                    RequestActionUiState.Success(result.data)
                }

                is SupervisorDataResult.Failure -> RequestActionUiState.Error(result.message)
            }
        }
    }

    fun unlinkPatient(patientUid: String) {
        if (_actionState.value is RequestActionUiState.Loading) return

        viewModelScope.launch {
            _actionState.value = RequestActionUiState.Loading
            _actionState.value = when (val result = repository.unlinkPatient(patientUid)) {
                is SupervisorDataResult.Success -> {
                    invalidateSupervisorRefreshes()
                    refreshIfStale(force = true)
                    RequestActionUiState.Success(result.data)
                }
                is SupervisorDataResult.Failure -> RequestActionUiState.Error(result.message)
            }
        }
    }

    class Factory(
        private val repository: SupervisorRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PatientsListViewModel::class.java))
            return PatientsListViewModel(repository) as T
        }
    }
}

class PatientDetailViewModel(
    private val repository: SupervisorRepository,
    private val patientUid: String,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow<SupervisorPatientDetailUiState>(SupervisorPatientDetailUiState.Loading)
    val uiState: StateFlow<SupervisorPatientDetailUiState> = _uiState.asStateFlow()
    private val refreshController = supervisorRefreshController()

    fun load() = refreshIfStale(force = true)

    fun refreshIfStale(force: Boolean = false) {
        refreshController.refreshIfStale(viewModelScope, force) {
            val previous = _uiState.value as? SupervisorPatientDetailUiState.Content
            _uiState.value = previous?.copy(isRefreshing = true, errorMessage = null)
                ?: SupervisorPatientDetailUiState.Loading
            when (val result = repository.loadPatientDetail(patientUid)) {
                is SupervisorDataResult.Success -> {
                    _uiState.value = SupervisorPatientDetailUiState.Content(
                        data = result.data,
                        lastSyncedAt = System.currentTimeMillis(),
                    )
                    true
                }
                is SupervisorDataResult.Failure -> {
                    _uiState.value = previous?.takeUnless { result.statusCode in setOf(401, 403) }?.copy(
                        isRefreshing = false,
                        errorMessage = result.message,
                    ) ?: SupervisorPatientDetailUiState.Error(result.message)
                    false
                }
            }
        }
    }

    class Factory(
        private val repository: SupervisorRepository,
        private val patientUid: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PatientDetailViewModel::class.java))
            return PatientDetailViewModel(repository, patientUid) as T
        }
    }
}

class SupervisorProfileViewModel(
    private val repository: SupervisorRepository,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow<SupervisorProfileUiState>(SupervisorProfileUiState.Loading)
    val uiState: StateFlow<SupervisorProfileUiState> = _uiState.asStateFlow()
    private val refreshController = supervisorRefreshController()

    init {
        refreshIfStale()
    }

    fun load() = refreshIfStale(force = true)

    fun refreshIfStale(force: Boolean = false) {
        refreshController.refreshIfStale(viewModelScope, force) {
            val previous = (_uiState.value as? SupervisorProfileUiState.Content)?.data
            val cached = repository.loadCachedProfileData()
            val cachedData = (cached as? SupervisorDataResult.Success)?.data
            _uiState.value = cachedData?.let(SupervisorProfileUiState::Content)
                ?: previous?.copy(isRefreshing = true, refreshMessage = null)
                    ?.let(SupervisorProfileUiState::Content)
                ?: SupervisorProfileUiState.Loading
            when (val result = repository.loadProfileData()) {
                is SupervisorDataResult.Success -> {
                    _uiState.value = SupervisorProfileUiState.Content(result.data)
                    true
                }
                is SupervisorDataResult.Failure -> {
                    val visible = cachedData ?: previous
                    _uiState.value = visible?.takeUnless { result.statusCode in setOf(401, 403) }?.let {
                    SupervisorProfileUiState.Content(
                        it.copy(isRefreshing = false, refreshMessage = result.message),
                    )
                    } ?: SupervisorProfileUiState.Error(result.message)
                    false
                }
            }
        }
    }

    class Factory(
        private val repository: SupervisorRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SupervisorProfileViewModel::class.java))
            return SupervisorProfileViewModel(repository) as T
        }
    }
}

class SupervisorNotificationsViewModel(
    private val repository: SupervisorRepository,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow<SupervisorNotificationsUiState>(SupervisorNotificationsUiState.Loading)
    val uiState: StateFlow<SupervisorNotificationsUiState> = _uiState.asStateFlow()

    private val _saveState = MutableStateFlow<RequestActionUiState>(RequestActionUiState.Idle)
    val saveState: StateFlow<RequestActionUiState> = _saveState.asStateFlow()
    private val refreshController = supervisorRefreshController()

    init {
        refreshIfStale()
    }

    fun load() = refreshIfStale(force = true)

    fun refreshIfStale(force: Boolean = false) {
        refreshController.refreshIfStale(viewModelScope, force) {
            val previous = _uiState.value as? SupervisorNotificationsUiState.Content
            _uiState.value = previous?.copy(isRefreshing = true, errorMessage = null)
                ?: SupervisorNotificationsUiState.Loading
            val settingsResult = repository.loadNotificationSettings()
            if (settingsResult is SupervisorDataResult.Failure) {
                _uiState.value = previous?.takeUnless {
                    settingsResult.statusCode in setOf(401, 403)
                }?.copy(
                    isRefreshing = false,
                    errorMessage = settingsResult.message,
                ) ?: SupervisorNotificationsUiState.Error(settingsResult.message)
                return@refreshIfStale false
            }

            val homeResult = repository.loadHomeData()
            if (homeResult is SupervisorDataResult.Failure) {
                _uiState.value = previous?.takeUnless {
                    homeResult.statusCode in setOf(401, 403)
                }?.copy(
                    isRefreshing = false,
                    errorMessage = homeResult.message,
                ) ?: SupervisorNotificationsUiState.Error(homeResult.message)
                return@refreshIfStale false
            }

            _uiState.value = SupervisorNotificationsUiState.Content(
                SupervisorNotificationsData(
                    settings = (settingsResult as SupervisorDataResult.Success).data,
                    homeData = (homeResult as SupervisorDataResult.Success).data,
                ),
                lastSyncedAt = System.currentTimeMillis(),
            )
            true
        }
    }

    fun save(input: SupervisorNotificationSettingsFormInput) {
        if (_saveState.value is RequestActionUiState.Loading) return

        val currentSettings = (_uiState.value as? SupervisorNotificationsUiState.Content)
            ?.data
            ?.settings
        val settings = input.toSettingsOrError(currentSettings) ?: return

        viewModelScope.launch {
            _saveState.value = RequestActionUiState.Loading
            _saveState.value = when (val result = repository.saveNotificationSettings(settings)) {
                is SupervisorDataResult.Success -> {
                    val currentData = (_uiState.value as? SupervisorNotificationsUiState.Content)?.data
                    if (currentData != null) {
                        _uiState.value = SupervisorNotificationsUiState.Content(
                            currentData.copy(settings = result.data),
                        )
                    } else {
                        refreshIfStale(force = true)
                    }
                    RequestActionUiState.Success("Notificaciones guardadas correctamente.")
                }
                is SupervisorDataResult.Failure -> RequestActionUiState.Error(result.message)
            }
        }
    }

    fun clearSaveMessage() {
        if (_saveState.value !is RequestActionUiState.Loading) {
            _saveState.value = RequestActionUiState.Idle
        }
    }

    private fun SupervisorNotificationSettingsFormInput.toSettingsOrError(
        currentSettings: NotificationSettingsData?,
    ): NotificationSettingsData? {
        val summaryTime = dailySummaryTime.cleanTimeOrNull()
        val relapseTime = relapseAlertTime.cleanTimeOrNull()
        val requestsTime = pendingRequestsTime.cleanTimeOrNull()
        val followTime = followUpTime.cleanTimeOrNull()

        val firstInvalid = listOfNotNull(
            if (dailySummaryEnabled && summaryTime == null) "Elige una hora válida para el resumen diario." else null,
            if (relapseAlertEnabled && relapseTime == null) "Elige una hora válida para revisar recaídas." else null,
            if (pendingRequestsEnabled && requestsTime == null) "Elige una hora válida para revisar solicitudes." else null,
            if (followUpEnabled && followTime == null) "Elige una hora válida para seguimiento." else null,
        ).firstOrNull()

        if (firstInvalid != null) {
            _saveState.value = RequestActionUiState.Error(firstInvalid)
            return null
        }

        return NotificationSettingsData(
            id = currentSettings?.id,
            dailyNoteEnabled = dailySummaryEnabled,
            dailyNoteTime = summaryTime ?: currentSettings?.dailyNoteTime ?: "08:00",
            soberDayEnabled = relapseAlertEnabled,
            soberDayTime = relapseTime ?: currentSettings?.soberDayTime ?: "09:00",
            achievementEnabled = pendingRequestsEnabled,
            achievementTime = requestsTime ?: currentSettings?.achievementTime ?: "09:30",
            eventRemindersEnabled = false,
            motivationalEnabled = followUpEnabled,
            motivationalTime = followTime ?: currentSettings?.motivationalTime ?: "18:00",
        )
    }

    private fun String.cleanTimeOrNull(): String? {
        val trimmed = trim().take(5)
        return trimmed.takeIf { Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(it) }
    }

    class Factory(
        private val repository: SupervisorRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SupervisorNotificationsViewModel::class.java))
            return SupervisorNotificationsViewModel(repository) as T
        }
    }
}

class AiChatViewModel(
    private val repository: SupervisorRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SupervisorChatUiState())
    val uiState: StateFlow<SupervisorChatUiState> = _uiState.asStateFlow()
    private val refreshController = supervisorRefreshController()

    init {
        refreshIfStale()
    }

    fun load() = refreshIfStale(force = true)

    fun refreshIfStale(force: Boolean = false) {
        refreshController.refreshIfStale(viewModelScope, force) {
            val previous = _uiState.value
            _uiState.value = if (previous.data == null) {
                previous.copy(loading = true, errorMessage = null)
            } else {
                previous.copy(isRefreshing = true, errorMessage = null)
            }
            when (val result = repository.loadChatData()) {
                is SupervisorDataResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        data = result.data.copy(messages = emptyList()),
                        errorMessage = null,
                        isRefreshing = false,
                        lastSyncedAt = System.currentTimeMillis(),
                    )
                    true
                }

                is SupervisorDataResult.Failure -> {
                    _uiState.value = if (result.statusCode in setOf(401, 403)) {
                        SupervisorChatUiState(loading = false, errorMessage = result.message)
                    } else {
                        previous.copy(
                            loading = false,
                            errorMessage = result.message,
                            isRefreshing = false,
                        )
                    }
                    false
                }
            }
        }
    }

    fun updateQuestion(value: String) {
        _uiState.value = _uiState.value.copy(
            question = value
                .sanitizeAiChatQuestion()
                .take(MAX_AI_CHAT_QUESTION_LENGTH + 1),
            errorMessage = null,
        )
    }

    fun updateMode(value: String) {
        if (value !in setOf("ai", "local") || _uiState.value.sending) return
        _uiState.value = _uiState.value.copy(mode = value, errorMessage = null)
    }

    fun sendQuestion() {
        val currentState = _uiState.value
        if (currentState.sending || currentState.loading) return

        val data = currentState.data
        val question = currentState.question.sanitizeAiChatQuestion()

        when {
            data == null -> {
                _uiState.value = currentState.copy(errorMessage = "Primero carga tus datos de supervisor.")
                return
            }

            !data.authorized -> {
                _uiState.value = currentState.copy(
                    errorMessage = "Tu cuenta de supervisor no está disponible para usar el asistente.",
                )
                return
            }

            data.patientsCount == 0 -> {
                _uiState.value = currentState.copy(
                    errorMessage = "Aún no tienes pacientes con permiso activo. El chat estará disponible cuando tengas al menos un paciente vinculado.",
                )
                return
            }

            question.isBlank() -> {
                _uiState.value = currentState.copy(
                    errorMessage = "Escribe una pregunta antes de enviarla.",
                )
                return
            }

            question.length > MAX_AI_CHAT_QUESTION_LENGTH -> {
                _uiState.value = currentState.copy(
                    errorMessage = "La pregunta no puede superar $MAX_AI_CHAT_QUESTION_LENGTH caracteres.",
                )
                return
            }

            question.length < 6 -> {
                _uiState.value = currentState.copy(
                    errorMessage = "Escribe una pregunta un poco más específica.",
                )
                return
            }
        }

        val userMessage = SupervisorChatMessage(
            text = question,
            fromUser = true,
        )
        val messagesWithQuestion = data.messages + userMessage
        _uiState.value = currentState.copy(
            question = "",
            sending = true,
            errorMessage = null,
            data = data.copy(messages = messagesWithQuestion),
        )

        viewModelScope.launch {
            _uiState.value = when (
                val result = repository.askSupervisorChat(question, mode = currentState.mode)
            ) {
                is SupervisorDataResult.Success -> {
                    val latestData = _uiState.value.data ?: data
                    _uiState.value.copy(
                        sending = false,
                        data = latestData.copy(
                            messages = latestData.messages + result.data,
                        ),
                    )
                }

                is SupervisorDataResult.Failure -> {
                    if (result.statusCode == 403) {
                        SupervisorChatUiState(
                            loading = false,
                            data = data.copy(
                                authorized = false,
                                patientsCount = 0,
                                messages = emptyList(),
                            ),
                            errorMessage = result.message,
                        )
                    } else if (result.statusCode == 409) {
                        SupervisorChatUiState(
                            loading = false,
                            data = data.copy(
                                patientsCount = 0,
                                messages = emptyList(),
                            ),
                            errorMessage = result.message,
                        )
                    } else {
                        _uiState.value.copy(
                            sending = false,
                            errorMessage = result.message,
                        )
                    }
                }
            }
        }
    }

    class Factory(
        private val repository: SupervisorRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AiChatViewModel::class.java))
            return AiChatViewModel(repository) as T
        }
    }
}
