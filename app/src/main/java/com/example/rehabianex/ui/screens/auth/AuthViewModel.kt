package com.example.rehabianex.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.rehabianex.data.model.AuthSession
import com.example.rehabianex.data.repository.AuthRepository
import com.example.rehabianex.data.repository.AuthResult
import com.example.rehabianex.validation.AuthValidator
import com.example.rehabianex.validation.LoginFormInput
import com.example.rehabianex.validation.RegisterFormInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState

    data class Success(
        val session: AuthSession,
        val message: String,
    ) : AuthUiState

    data class AccountCreated(
        val uid: String,
        val role: String,
        val message: String,
    ) : AuthUiState

    data class Error(
        val message: String,
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : AuthUiState
}

class AuthViewModel(
    private val repository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun login(input: LoginFormInput) {
        if (_uiState.value is AuthUiState.Loading) return

        val validation = AuthValidator.validateLogin(input)
        val request = validation.value

        if (request == null) {
            _uiState.value = AuthUiState.Error(
                message = "Revisa los datos marcados.",
                fieldErrors = validation.fieldErrors,
            )
            return
        }

        execute { repository.login(request) }
    }

    fun register(input: RegisterFormInput) {
        if (_uiState.value is AuthUiState.Loading) return

        val validation = AuthValidator.validateRegister(input)
        val request = validation.value

        if (request == null) {
            _uiState.value = AuthUiState.Error(
                message = "Revisa los datos marcados.",
                fieldErrors = validation.fieldErrors,
            )
            return
        }

        execute { repository.register(request) }
    }

    fun clearState() {
        _uiState.value = AuthUiState.Idle
    }

    private fun execute(request: suspend () -> AuthResult) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            _uiState.value = when (val result = request()) {
                is AuthResult.Success -> AuthUiState.Success(
                    session = result.session,
                    message = result.message,
                )

                is AuthResult.AccountCreated -> AuthUiState.AccountCreated(
                    uid = result.uid,
                    role = result.role,
                    message = result.message,
                )

                is AuthResult.Failure -> AuthUiState.Error(
                    message = result.message,
                    fieldErrors = result.fieldErrors,
                )
            }
        }
    }

    class Factory(
        private val repository: AuthRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AuthViewModel::class.java))
            return AuthViewModel(repository) as T
        }
    }
}
