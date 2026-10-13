package com.example.rehabianex.ui.screens.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.rehabianex.data.model.AccountDeletionReceipt
import com.example.rehabianex.data.model.DataControlSummary
import com.example.rehabianex.data.repository.DataControlRepository
import com.example.rehabianex.data.repository.DataControlResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DataControlUiState(
    val loading: Boolean = true,
    val summary: DataControlSummary? = null,
    val errorMessage: String? = null,
    val exportState: DataExportUiState = DataExportUiState.Idle,
    val deletionState: AccountDeletionUiState = AccountDeletionUiState.Idle,
)

sealed interface DataExportUiState {
    data object Idle : DataExportUiState
    data object Loading : DataExportUiState
    data class Ready(val json: String) : DataExportUiState
    data class Error(val message: String) : DataExportUiState
}

sealed interface AccountDeletionUiState {
    data object Idle : AccountDeletionUiState
    data object Reauthenticating : AccountDeletionUiState
    data object Requesting : AccountDeletionUiState
    data class Success(val receipt: AccountDeletionReceipt) : AccountDeletionUiState
    data class Error(
        val message: String,
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : AccountDeletionUiState
}

class DataControlViewModel(
    private val repository: DataControlRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DataControlUiState())
    val uiState: StateFlow<DataControlUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (_uiState.value.loading && _uiState.value.summary != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, errorMessage = null)
            _uiState.value = when (val result = repository.loadSummary()) {
                is DataControlResult.Success -> _uiState.value.copy(
                    loading = false,
                    summary = result.data,
                    errorMessage = null,
                )
                is DataControlResult.Failure -> _uiState.value.copy(
                    loading = false,
                    errorMessage = result.message,
                )
            }
        }
    }

    fun exportData() {
        if (_uiState.value.exportState is DataExportUiState.Loading) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(exportState = DataExportUiState.Loading)
            _uiState.value = when (val result = repository.exportOwnData()) {
                is DataControlResult.Success -> _uiState.value.copy(
                    exportState = DataExportUiState.Ready(result.data),
                )
                is DataControlResult.Failure -> _uiState.value.copy(
                    exportState = DataExportUiState.Error(result.message),
                )
            }
        }
    }

    fun markExportHandled(errorMessage: String? = null) {
        _uiState.value = _uiState.value.copy(
            exportState = errorMessage?.let(DataExportUiState::Error) ?: DataExportUiState.Idle,
        )
    }

    fun requestAccountDeletion(
        password: String,
        confirmation: String,
        acknowledgeRetentionLimits: Boolean,
    ) {
        val state = _uiState.value
        if (state.deletionState is AccountDeletionUiState.Reauthenticating ||
            state.deletionState is AccountDeletionUiState.Requesting
        ) return
        val summary = state.summary ?: return
        if (!summary.automatedAccountDeletion) {
            _uiState.value = state.copy(
                deletionState = AccountDeletionUiState.Error(
                    "Este tipo de cuenta requiere atención manual para solicitar eliminación.",
                ),
            )
            return
        }
        if (confirmation != summary.confirmationPhrase || !acknowledgeRetentionLimits) {
            _uiState.value = state.copy(
                deletionState = AccountDeletionUiState.Error(
                    "Confirma la frase exacta y los límites de conservación.",
                ),
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                deletionState = AccountDeletionUiState.Reauthenticating,
            )
            when (val reauth = repository.reauthenticate(password)) {
                is DataControlResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        deletionState = AccountDeletionUiState.Error(
                            reauth.message,
                            reauth.fieldErrors,
                        ),
                    )
                    return@launch
                }
                is DataControlResult.Success -> Unit
            }

            _uiState.value = _uiState.value.copy(
                deletionState = AccountDeletionUiState.Requesting,
            )
            _uiState.value = when (
                val deletion = repository.requestAccountDeletion(
                    confirmation = confirmation,
                    acknowledgeRetentionLimits = acknowledgeRetentionLimits,
                )
            ) {
                is DataControlResult.Success -> _uiState.value.copy(
                    deletionState = AccountDeletionUiState.Success(deletion.data),
                )
                is DataControlResult.Failure -> _uiState.value.copy(
                    deletionState = AccountDeletionUiState.Error(
                        deletion.message,
                        deletion.fieldErrors,
                    ),
                )
            }
        }
    }

    fun clearDeletionError() {
        if (_uiState.value.deletionState is AccountDeletionUiState.Error) {
            _uiState.value = _uiState.value.copy(deletionState = AccountDeletionUiState.Idle)
        }
    }

    class Factory(
        private val repository: DataControlRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DataControlViewModel::class.java))
            return DataControlViewModel(repository) as T
        }
    }
}
