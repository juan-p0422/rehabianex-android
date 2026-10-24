package com.example.rehabianex.ui.screens.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.rehabianex.data.model.PrivacyNoticeDto
import com.example.rehabianex.data.repository.PrivacyNoticeConfirmResult
import com.example.rehabianex.data.repository.PrivacyNoticeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PrivacyNoticeUiState {
    data object Loading : PrivacyNoticeUiState
    data object AccessGranted : PrivacyNoticeUiState
    data class Content(
        val notice: PrivacyNoticeDto,
        val warning: String? = null,
        val submitting: Boolean = false,
        val error: String? = null,
    ) : PrivacyNoticeUiState
    data class Error(val message: String) : PrivacyNoticeUiState
}

class PrivacyNoticeViewModel(
    private val repository: PrivacyNoticeRepository,
    private val confirmationRequired: Boolean,
) : ViewModel() {
    private val _uiState = MutableStateFlow<PrivacyNoticeUiState>(PrivacyNoticeUiState.Loading)
    val uiState: StateFlow<PrivacyNoticeUiState> = _uiState.asStateFlow()

    init {
        loadInternal()
    }

    fun load() {
        if (_uiState.value is PrivacyNoticeUiState.Loading) return
        loadInternal()
    }

    private fun loadInternal() {
        viewModelScope.launch {
            _uiState.value = PrivacyNoticeUiState.Loading
            val result = runCatching { repository.load(confirmationRequired) }
                .getOrElse {
                    _uiState.value = PrivacyNoticeUiState.Error(
                        "No fue posible abrir el aviso. Intenta nuevamente.",
                    )
                    return@launch
                }
            _uiState.value = if (
                confirmationRequired &&
                result.accessGranted
            ) {
                PrivacyNoticeUiState.AccessGranted
            } else {
                PrivacyNoticeUiState.Content(result.notice, result.warning)
            }
        }
    }

    fun confirmRead() {
        val content = _uiState.value as? PrivacyNoticeUiState.Content ?: return
        if (content.submitting) return
        val version = content.notice.version?.takeIf { it.isNotBlank() } ?: return

        viewModelScope.launch {
            _uiState.value = content.copy(submitting = true, error = null)
            _uiState.value = when (val result = repository.confirmRead(version)) {
                PrivacyNoticeConfirmResult.Success -> PrivacyNoticeUiState.AccessGranted
                is PrivacyNoticeConfirmResult.Failure -> content.copy(
                    submitting = false,
                    error = result.message,
                )
            }
        }
    }

    class Factory(
        private val repository: PrivacyNoticeRepository,
        private val confirmationRequired: Boolean,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PrivacyNoticeViewModel::class.java))
            return PrivacyNoticeViewModel(repository, confirmationRequired) as T
        }
    }
}
