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
        val query: String = "",
        val page: Int = 1,
        val total: Int = 0,
        val hasMore: Boolean = false,
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
    private var selectedQuery = ""
    private var selectedPage = 1
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
        selectedPage = 1
        refreshIfStale(force = true)
    }

    fun search(query: String) {
        if (_actionState.value is AdminSupervisorActionUiState.Loading) return
        selectedQuery = query.trim().take(MAX_SEARCH_LENGTH)
        selectedPage = 1
        refreshIfStale(force = true)
    }

    fun previousPage() {
        if (selectedPage <= 1 || _actionState.value is AdminSupervisorActionUiState.Loading) return
        selectedPage--
        refreshIfStale(force = true)
    }

    fun nextPage() {
        val content = _uiState.value as? AdminSupervisorsUiState.Content ?: return
        if (!content.hasMore || _actionState.value is AdminSupervisorActionUiState.Loading) return
        selectedPage++
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
                    query = selectedQuery,
                    page = selectedPage,
                )
            } ?: AdminSupervisorsUiState.Loading(filter)
        }

        return when (
            val result = repository.supervisors(
                query = selectedQuery.takeIf(String::isNotBlank),
                status = filter.apiStatus,
                page = selectedPage,
            )
        ) {
            is AdminDataResult.Success -> {
                _uiState.value = AdminSupervisorsUiState.Content(
                    filter = filter,
                    supervisors = result.data.supervisors,
                    dashboard = repository.cachedDashboard(),
                    query = selectedQuery,
                    page = result.data.page,
                    total = result.data.total,
                    hasMore = result.data.hasMore,
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
                        query = selectedQuery,
                        page = selectedPage,
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

    private companion object {
        const val MAX_SEARCH_LENGTH = 120
    }

}
