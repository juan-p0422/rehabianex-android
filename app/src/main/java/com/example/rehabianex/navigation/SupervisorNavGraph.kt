package com.example.rehabianex.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.rehabianex.data.repository.SupervisorRepository
import com.example.rehabianex.ui.screens.supervisor.AiChatScreen
import com.example.rehabianex.ui.screens.supervisor.AiChatViewModel
import com.example.rehabianex.ui.screens.supervisor.PatientDetailScreen
import com.example.rehabianex.ui.screens.supervisor.PatientDetailViewModel
import com.example.rehabianex.ui.screens.supervisor.PatientsListScreen
import com.example.rehabianex.ui.screens.supervisor.PatientsListViewModel
import com.example.rehabianex.ui.screens.supervisor.SupervisorHomeScreen
import com.example.rehabianex.ui.screens.supervisor.SupervisorHomeViewModel
import com.example.rehabianex.ui.screens.supervisor.SupervisorNotificationsScreen
import com.example.rehabianex.ui.screens.supervisor.SupervisorNotificationsViewModel
import com.example.rehabianex.ui.screens.supervisor.SupervisorProfileScreen
import com.example.rehabianex.ui.screens.supervisor.SupervisorProfileViewModel

internal fun NavGraphBuilder.supervisorGraph(
    navController: NavHostController,
    supervisorRepository: SupervisorRepository,
    onLogout: () -> Unit,
) {
    val navigate: (String) -> Unit = navController::navigateSingleTop

    composable(Routes.SupervisorHome) {
        val viewModel: SupervisorHomeViewModel = viewModel(
            factory = SupervisorHomeViewModel.Factory(supervisorRepository),
        )
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()
        val actionState = viewModel.actionState.collectAsStateWithLifecycle()
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            viewModel.refreshIfStale()
        }
        SupervisorHomeScreen(
            onNavigate = navigate,
            onLogout = onLogout,
            uiState = uiState.value,
            actionState = actionState.value,
            onRetry = viewModel::load,
            onRespondRequest = viewModel::respondRequest,
        )
    }
    composable(Routes.PatientsList) {
        val viewModel: PatientsListViewModel = viewModel(
            factory = PatientsListViewModel.Factory(supervisorRepository),
        )
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()
        val actionState = viewModel.actionState.collectAsStateWithLifecycle()
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            viewModel.refreshIfStale()
        }
        PatientsListScreen(
            onNavigate = navigate,
            uiState = uiState.value,
            actionState = actionState.value,
            onRetry = viewModel::load,
            onRespondRequest = viewModel::respondRequest,
            onUnlinkPatient = viewModel::unlinkPatient,
        )
    }
    composable(Routes.PatientDetail) {
        PatientDetailScreen(
            onNavigate = navigate,
            uiState = null,
            onRetry = {},
        )
    }
    composable(
        route = Routes.PatientDetailArg,
        arguments = listOf(navArgument("patientUid") { type = NavType.StringType }),
    ) { entry ->
        val patientUid = entry.arguments?.getString("patientUid").orEmpty()
        val viewModel: PatientDetailViewModel = viewModel(
            key = "patient_detail_$patientUid",
            factory = PatientDetailViewModel.Factory(
                repository = supervisorRepository,
                patientUid = patientUid,
            ),
        )
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            viewModel.refreshIfStale()
        }
        PatientDetailScreen(
            onNavigate = navigate,
            uiState = uiState.value,
            onRetry = viewModel::load,
        )
    }
    composable(Routes.AiChat) {
        val viewModel: AiChatViewModel = viewModel(
            factory = AiChatViewModel.Factory(supervisorRepository),
        )
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            viewModel.refreshIfStale()
        }
        AiChatScreen(
            onNavigate = navigate,
            onBack = {
                if (!navController.popBackStack()) {
                    navController.navigateSingleTop(Routes.SupervisorHome)
                }
            },
            uiState = uiState.value,
            onRetry = viewModel::load,
            onInputChanged = viewModel::updateQuestion,
            onModeChanged = viewModel::updateMode,
            onSend = viewModel::sendQuestion,
        )
    }
    composable(Routes.SupervisorProfile) {
        val viewModel: SupervisorProfileViewModel = viewModel(
            factory = SupervisorProfileViewModel.Factory(supervisorRepository),
        )
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            viewModel.refreshIfStale()
        }
        SupervisorProfileScreen(
            onNavigate = navigate,
            onLogout = onLogout,
            uiState = uiState.value,
            onRetry = viewModel::load,
        )
    }
    composable(Routes.SupervisorNotifications) {
        val viewModel: SupervisorNotificationsViewModel = viewModel(
            factory = SupervisorNotificationsViewModel.Factory(supervisorRepository),
        )
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()
        val saveState = viewModel.saveState.collectAsStateWithLifecycle()
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            viewModel.refreshIfStale()
        }
        SupervisorNotificationsScreen(
            onNavigate = navigate,
            uiState = uiState.value,
            saveState = saveState.value,
            onRetry = viewModel::load,
            onSave = viewModel::save,
            onInputChanged = viewModel::clearSaveMessage,
        )
    }
}
