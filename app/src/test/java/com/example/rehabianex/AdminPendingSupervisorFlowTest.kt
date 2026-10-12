package com.example.rehabianex

import com.example.rehabianex.data.model.AdminSupervisorDto
import com.example.rehabianex.data.model.AdminSupervisorResponse
import com.example.rehabianex.data.model.AdminSupervisorsResponse
import com.example.rehabianex.data.remote.ApiService
import com.example.rehabianex.data.repository.AdminRepository
import com.example.rehabianex.ui.screens.admin.AdminHomeViewModel
import com.example.rehabianex.ui.screens.admin.AdminSupervisorActionUiState
import com.example.rehabianex.ui.screens.admin.AdminSupervisorFilter
import com.example.rehabianex.ui.screens.admin.AdminSupervisorsUiState
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class AdminPendingSupervisorFlowTest {
    @Test
    fun pendingSupervisor_isAuthorizedAndListRefreshesFromApi() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var pending = true
            var authorizeCalls = 0
            val requestedStatuses = mutableListOf<String?>()
            val pendingSupervisor = AdminSupervisorDto(
                uid = "supervisor-pending",
                fullName = "Dra. Laura Martínez",
                supervisorType = "clinical_psychologist",
                supervisorCode = null,
                authorized = false,
                verified = false,
                status = "pending_review",
            )
            val apiService = Proxy.newProxyInstance(
                ApiService::class.java.classLoader,
                arrayOf(ApiService::class.java),
            ) { _, method, arguments ->
                when (method.name) {
                    "adminSupervisors" -> {
                        requestedStatuses += arguments?.getOrNull(1) as? String
                        val data = if (pending) listOf(pendingSupervisor) else emptyList()
                        Response.success(
                            AdminSupervisorsResponse(
                                ok = true,
                                count = data.size,
                                data = data,
                            ),
                        )
                    }

                    "authorizeAdminSupervisor" -> {
                        authorizeCalls += 1
                        pending = false
                        Response.success(
                            AdminSupervisorResponse(
                                ok = true,
                                message = "Supervisor autorizado correctamente.",
                                data = pendingSupervisor.copy(
                                    authorized = true,
                                    verified = true,
                                    status = "active",
                                ),
                            ),
                        )
                    }

                    else -> error("Unexpected ApiService call: ${method.name}")
                }
            } as ApiService
            val viewModel = AdminHomeViewModel(AdminRepository(apiService))

            viewModel.load()
            advanceUntilIdle()

            val initialContent = viewModel.uiState.value as AdminSupervisorsUiState.Content
            assertEquals(listOf(pendingSupervisor), initialContent.supervisors)

            viewModel.authorize("supervisor-pending")
            advanceUntilIdle()

            val refreshedContent = viewModel.uiState.value as AdminSupervisorsUiState.Content
            assertTrue(refreshedContent.supervisors.isEmpty())
            assertEquals(1, authorizeCalls)
            assertEquals(listOf("pending_review", "pending_review"), requestedStatuses)
            assertTrue(
                viewModel.actionState.value is AdminSupervisorActionUiState.Success,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun activeSupervisor_isSuspendedThenReactivatedWithApiRefresh() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var currentStatus = "active"
            var suspendCalls = 0
            var reactivateCalls = 0
            val requestedStatuses = mutableListOf<String?>()
            val supervisor = AdminSupervisorDto(
                uid = "supervisor-active",
                fullName = "Dr. Carlos Rivera",
                supervisorType = "psychiatrist",
                supervisorCode = "RA-12345678",
                authorized = true,
                verified = true,
                status = "active",
            )
            val apiService = Proxy.newProxyInstance(
                ApiService::class.java.classLoader,
                arrayOf(ApiService::class.java),
            ) { _, method, arguments ->
                when (method.name) {
                    "adminSupervisors" -> {
                        val requestedStatus = arguments?.getOrNull(1) as? String
                        requestedStatuses += requestedStatus
                        val data = if (requestedStatus == currentStatus) {
                            listOf(
                                supervisor.copy(
                                    status = currentStatus,
                                    authorized = currentStatus == "active",
                                ),
                            )
                        } else {
                            emptyList()
                        }
                        Response.success(
                            AdminSupervisorsResponse(
                                ok = true,
                                count = data.size,
                                data = data,
                            ),
                        )
                    }

                    "suspendAdminSupervisor" -> {
                        suspendCalls += 1
                        currentStatus = "suspended"
                        Response.success(
                            AdminSupervisorResponse(
                                ok = true,
                                message = "Supervisor suspendido correctamente.",
                                data = supervisor.copy(
                                    status = "suspended",
                                    authorized = false,
                                ),
                            ),
                        )
                    }

                    "reactivateAdminSupervisor" -> {
                        reactivateCalls += 1
                        currentStatus = "active"
                        Response.success(
                            AdminSupervisorResponse(
                                ok = true,
                                message = "Supervisor reactivado correctamente.",
                                data = supervisor,
                            ),
                        )
                    }

                    else -> error("Unexpected ApiService call: ${method.name}")
                }
            } as ApiService
            val viewModel = AdminHomeViewModel(AdminRepository(apiService))

            viewModel.selectFilter(AdminSupervisorFilter.Active)
            advanceUntilIdle()
            val activeContent = viewModel.uiState.value as AdminSupervisorsUiState.Content
            assertEquals(listOf(supervisor), activeContent.supervisors)

            viewModel.suspendSupervisor("supervisor-active", "Revisión administrativa")
            advanceUntilIdle()
            val refreshedActive = viewModel.uiState.value as AdminSupervisorsUiState.Content
            assertTrue(refreshedActive.supervisors.isEmpty())

            viewModel.selectFilter(AdminSupervisorFilter.Suspended)
            advanceUntilIdle()
            val suspendedContent = viewModel.uiState.value as AdminSupervisorsUiState.Content
            assertEquals("suspended", suspendedContent.supervisors.single().status)

            viewModel.reactivate("supervisor-active")
            advanceUntilIdle()
            val refreshedSuspended = viewModel.uiState.value as AdminSupervisorsUiState.Content
            assertTrue(refreshedSuspended.supervisors.isEmpty())

            assertEquals(1, suspendCalls)
            assertEquals(1, reactivateCalls)
            assertEquals(
                listOf("active", "active", "suspended", "suspended"),
                requestedStatuses,
            )
            assertTrue(
                viewModel.actionState.value is AdminSupervisorActionUiState.Success,
            )
        } finally {
            Dispatchers.resetMain()
        }
    }
}
