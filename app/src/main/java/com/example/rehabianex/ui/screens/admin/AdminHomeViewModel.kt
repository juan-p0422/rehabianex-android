package com.example.rehabianex.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.rehabianex.core.performance.StaleRefreshController
import com.example.rehabianex.core.performance.ADMIN_REFRESH_SCOPE
import com.example.rehabianex.core.performance.RefreshInvalidationRegistry
import com.example.rehabianex.data.model.AdminSupervisorDto
import com.example.rehabianex.data.repository.AdminDataResult
import com.example.rehabianex.data.repository.AdminDashboardCacheData
import com.example.rehabianex.data.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AdminSupervisorFilter(
    val apiStatus: String,
) {
    PendingReview("pending_review"),
    Active("active"),
    Suspended("suspended"),
}

sealed interface AdminSupervisorsUiState {
    val filter: AdminSupervisorFilter

    data class Loading(
        override val filter: AdminSupervisorFilter,
    ) : AdminSupervisorsUiState

    data class Content(
        override val filter: AdminSupervisorFilter,
        val supervisors: List<AdminSupervisorDto>,
        val dashboard: AdminDashboardCacheData? = null,
        val isRefreshing: Boolean = false,
        val refreshMessage: String? = null,
        val listAuthoritative: Boolean = true,
    ) : AdminSupervisorsUiState

    data class Error(
        override val filter: AdminSupervisorFilter,
        val message: String,
    ) : AdminSupervisorsUiState
}

sealed interface AdminSupervisorActionUiState {
    data object Idle : AdminSupervisorActionUiState
    data class Loading(
        val supervisorUid: String,
    ) : AdminSupervisorActionUiState

    data class Success(
        val message: String,
    ) : AdminSupervisorActionUiState

    data class Error(
        val message: String,
    ) : AdminSupervisorActionUiState
}

class AdminHomeViewModel(
    private val repository: AdminRepository,
) : ViewModel() {
    private var selectedFilter = AdminSupervisorFilter.PendingReview
    private val _uiState =
        MutableStateFlow<AdminSupervisorsUiState>(
            AdminSupervisorsUiState.Loading(selectedFilter),
        )
    val uiState: StateFlow<AdminSupervisorsUiState> = _uiState.asStateFlow()

    private val _actionState =
        MutableStateFlow<AdminSupervisorActionUiState>(AdminSupervisorActionUiState.Idle)
    val actionState: StateFlow<AdminSupervisorActionUiState> = _actionState.asStateFlow()
    private val refreshController = StaleRefreshController(invalidationScope = ADMIN_REFRESH_SCOPE)

    init {
        refreshIfStale()
    }

    fun load() = refreshIfStale(force = true)

    fun refreshIfStale(force: Boolean = false) {
        refreshController.refreshIfStale(viewModelScope, force) {
            refreshSelected(showLoading = true)
        }
    }

    fun selectFilter(filter: AdminSupervisorFilter) {
        if (
            filter == selectedFilter ||
            _actionState.value is AdminSupervisorActionUiState.Loading
        ) {
            return
        }

        selectedFilter = filter
        refreshIfStale(force = true)
    }

    fun authorize(supervisorUid: String) {
        performAction(
            supervisorUid = supervisorUid,
            successMessage = "Supervisor autorizado correctamente.",
        ) {
            repository.authorize(supervisorUid)
        }
    }

    fun reject(
        supervisorUid: String,
        reason: String,
    ) {
        performAction(
            supervisorUid = supervisorUid,
            successMessage = "Supervisor rechazado correctamente.",
        ) {
            repository.reject(supervisorUid, reason)
        }
    }

    fun suspendSupervisor(
        supervisorUid: String,
        reason: String,
    ) {
        performAction(
            supervisorUid = supervisorUid,
            successMessage = "Supervisor suspendido correctamente.",
        ) {
            repository.suspend(supervisorUid, reason)
        }
    }

    fun reactivate(supervisorUid: String) {
        performAction(
            supervisorUid = supervisorUid,
            successMessage = "Supervisor reactivado correctamente.",
        ) {
            repository.reactivate(supervisorUid)
        }
    }

    private fun performAction(
        supervisorUid: String,
        successMessage: String,
        action: suspend () -> AdminDataResult<AdminSupervisorDto>,
    ) {
        if (_actionState.value is AdminSupervisorActionUiState.Loading) return

        viewModelScope.launch {
            _actionState.value = AdminSupervisorActionUiState.Loading(supervisorUid)
            when (val result = action()) {
                is AdminDataResult.Success -> {
                    RefreshInvalidationRegistry.invalidate(ADMIN_REFRESH_SCOPE)
                    val refreshed = refreshSelected(showLoading = false)
                    if (refreshed) refreshController.markFresh()
                    _actionState.value = AdminSupervisorActionUiState.Success(
                        if (refreshed) {
                            successMessage
                        } else {
                            "$successMessage Actualiza la lista para ver los cambios."
                        },
                    )
                }

                is AdminDataResult.Failure -> {
                    _actionState.value = AdminSupervisorActionUiState.Error(result.message)
                }
            }
        }
    }

    private suspend fun refreshSelected(showLoading: Boolean): Boolean {
        val filter = selectedFilter
        val cachedDashboard = repository.cachedDashboard()
        if (showLoading) {
            _uiState.value = cachedDashboard?.let {
                AdminSupervisorsUiState.Content(
                    filter = filter,
                    supervisors = emptyList(),
                    dashboard = it,
                    isRefreshing = true,
                    listAuthoritative = false,
                )
            } ?: AdminSupervisorsUiState.Loading(filter)
        }

        return when (
            val result = repository.supervisors(
                status = filter.apiStatus,
            )
        ) {
            is AdminDataResult.Success -> {
                _uiState.value = AdminSupervisorsUiState.Content(
                    filter = filter,
                    supervisors = result.data,
                    dashboard = repository.cachedDashboard(),
                )
                true
            }

            is AdminDataResult.Failure -> {
                _uiState.value = cachedDashboard?.let {
                    AdminSupervisorsUiState.Content(
                        filter = filter,
                        supervisors = emptyList(),
                        dashboard = it,
                        refreshMessage = result.message,
                        listAuthoritative = false,
                    )
                } ?: AdminSupervisorsUiState.Error(filter, result.message)
                false
            }
        }
    }

    class Factory(
        private val repository: AdminRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AdminHomeViewModel::class.java))
            return AdminHomeViewModel(repository) as T
        }
    }

}
