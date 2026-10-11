package com.example.rehabianex.ui.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.rehabianex.data.repository.FcmDemoRepository
import com.example.rehabianex.data.repository.FcmDemoResult
import com.example.rehabianex.notifications.FcmDemoCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface FcmDemoUiState {
    data object Idle : FcmDemoUiState
    data class Sending(val type: String) : FcmDemoUiState
    data class Success(val type: String) : FcmDemoUiState
    data class Error(val type: String, val message: String) : FcmDemoUiState
}

class FcmDemoViewModel(
    private val repository: FcmDemoRepository,
    private val role: String?,
) : ViewModel() {
    private val _uiState = MutableStateFlow<FcmDemoUiState>(FcmDemoUiState.Idle)
    val uiState: StateFlow<FcmDemoUiState> = _uiState.asStateFlow()

    fun send(type: String) {
        if (!FcmDemoCatalog.isAllowed(role, type) || _uiState.value is FcmDemoUiState.Sending) return
        viewModelScope.launch {
            _uiState.value = FcmDemoUiState.Sending(type)
            _uiState.value = when (val result = repository.send(type)) {
                is FcmDemoResult.Success -> FcmDemoUiState.Success(result.type)
                is FcmDemoResult.Failure -> FcmDemoUiState.Error(type, result.message)
            }
        }
    }

    class Factory(
        private val repository: FcmDemoRepository,
        private val role: String?,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FcmDemoViewModel(repository, role) as T
        }
    }
}
