package com.example.rehabianex.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.example.rehabianex.data.repository.PatientRepository
import com.example.rehabianex.ui.screens.patient.AchievementsScreen
import com.example.rehabianex.ui.screens.patient.AchievementsViewModel
import com.example.rehabianex.ui.screens.patient.ActionPlanScreen
import com.example.rehabianex.ui.screens.patient.AgendaScreen
import com.example.rehabianex.ui.screens.patient.AgendaViewModel
import com.example.rehabianex.ui.screens.patient.EmotionalCheckinScreen
import com.example.rehabianex.ui.screens.patient.EmotionalCheckinViewModel
import com.example.rehabianex.ui.screens.patient.InterventionsScreen
import com.example.rehabianex.ui.screens.patient.LocalDirectoryScreen
import com.example.rehabianex.ui.screens.patient.LocalDirectoryViewModel
import com.example.rehabianex.ui.screens.patient.MetricsScreen
import com.example.rehabianex.ui.screens.patient.MetricsViewModel
import com.example.rehabianex.ui.screens.patient.NewEventScreen
import com.example.rehabianex.ui.screens.patient.NewEventViewModel
import com.example.rehabianex.ui.screens.patient.NotificationsScreen
import com.example.rehabianex.ui.screens.patient.NotificationsViewModel
import com.example.rehabianex.ui.screens.patient.PatientHomeScreen
import com.example.rehabianex.ui.screens.patient.PatientHomeViewModel
import com.example.rehabianex.ui.screens.patient.PatientProfileScreen
import com.example.rehabianex.ui.screens.patient.PatientProfileViewModel
import com.example.rehabianex.ui.screens.patient.SupervisorLinkScreen
import com.example.rehabianex.ui.screens.patient.SupervisorLinkViewModel
import com.example.rehabianex.ui.screens.patient.SupportNetworkScreen
import com.example.rehabianex.ui.screens.patient.SupportNetworkViewModel

internal fun NavGraphBuilder.patientGraph(
    navController: NavHostController,
    patientRepository: PatientRepository,
    onLogout: () -> Unit,
) {
    val navigate: (String) -> Unit = navController::navigateSingleTop

    composable(Routes.PatientHome) {
        val viewModel: PatientHomeViewModel = viewModel(
            factory = PatientHomeViewModel.Factory(patientRepository),
        )
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            viewModel.refreshIfStale()
        }
        PatientHomeScreen(
            onNavigate = navigate,
            uiState = uiState.value,
            onRetry = viewModel::load,
        )
    }
    composable(Routes.EmotionalCheckin) {
        val viewModel: EmotionalCheckinViewModel = viewModel(
            factory = EmotionalCheckinViewModel.Factory(patientRepository),
        )
        val saveState = viewModel.saveState.collectAsStateWithLifecycle()
        EmotionalCheckinScreen(
            onNavigate = navigate,
            saveState = saveState.value,
            onSave = viewModel::save,
            onInputChanged = viewModel::clearMessage,
        )
    }
    composable(Routes.Metrics) {
        val viewModel: MetricsViewModel = viewModel(
            factory = MetricsViewModel.Factory(patientRepository),
        )
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            viewModel.refreshIfStale()
        }
        MetricsScreen(
            onNavigate = navigate,
            uiState = uiState.value,
            onRetry = viewModel::load,
        )
    }
    composable(Routes.Achievements) {
        val viewModel: AchievementsViewModel = viewModel(
            factory = AchievementsViewModel.Factory(patientRepository),
        )
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            viewModel.refreshIfStale()
        }
        AchievementsScreen(
            onNavigate = navigate,
            uiState = uiState.value,
            onRetry = viewModel::load,
        )
    }
    composable(Routes.Interventions) {
        InterventionsScreen(navigate)
    }
    composable(Routes.ActionPlan) {
        ActionPlanScreen(
            onBack = { navController.popBackStack() },
        )
    }
    composable(Routes.SupportNetwork) {
        val viewModel: SupportNetworkViewModel = viewModel(
            factory = SupportNetworkViewModel.Factory(patientRepository),
        )
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()
        val saveState = viewModel.saveState.collectAsStateWithLifecycle()
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            viewModel.refreshIfStale()
        }
        SupportNetworkScreen(
            onNavigate = navigate,
            uiState = uiState.value,
            saveState = saveState.value,
            onRetry = viewModel::load,
            onSaveContact = viewModel::saveContact,
            onInputChanged = viewModel::clearSaveMessage,
        )
    }
    composable(Routes.Agenda) {
        val viewModel: AgendaViewModel = viewModel(
            factory = AgendaViewModel.Factory(patientRepository),
        )
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            viewModel.refreshIfStale()
        }
        AgendaScreen(
            onNavigate = navigate,
            uiState = uiState.value,
            onRetry = viewModel::load,
            onCompleteEvent = { event -> viewModel.completeEvent(event.id) },
            onCancelEvent = { event -> viewModel.cancelEvent(event.id) },
            onReportAttendance = { event, attended -> viewModel.reportAttendance(event.id, attended) },
        )
    }
    composable(Routes.NewEvent) {
        val viewModel: NewEventViewModel = viewModel(
            factory = NewEventViewModel.Factory(patientRepository),
        )
        val saveState = viewModel.saveState.collectAsStateWithLifecycle()
        NewEventScreen(
            saveState = saveState.value,
            onSave = viewModel::saveEvent,
            onInputChanged = viewModel::clearSaveMessage,
            onBackToAgenda = {
                navController.navigate(Routes.Agenda) {
                    popUpTo(Routes.NewEvent) {
                        inclusive = true
                    }
                }
            },
            onCancel = { navController.popBackStack() },
        )
    }
    composable(Routes.LocalDirectory) {
        val viewModel: LocalDirectoryViewModel = viewModel(
            factory = LocalDirectoryViewModel.Factory(patientRepository),
        )
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            viewModel.refreshIfStale()
        }
        LocalDirectoryScreen(
            onNavigate = navigate,
            uiState = uiState.value,
            onRetry = viewModel::load,
        )
    }
    composable(Routes.SupervisorLink) {
        val viewModel: SupervisorLinkViewModel = viewModel(
            factory = SupervisorLinkViewModel.Factory(patientRepository),
        )
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()
        SupervisorLinkScreen(
            onNavigate = navigate,
            uiState = uiState.value,
            onRequestLink = viewModel::requestLink,
            onInputChanged = viewModel::clearMessage,
        )
    }
    composable(Routes.Notifications) {
        val viewModel: NotificationsViewModel = viewModel(
            factory = NotificationsViewModel.Factory(patientRepository),
        )
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()
        val saveState = viewModel.saveState.collectAsStateWithLifecycle()
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            viewModel.refreshIfStale()
        }
        NotificationsScreen(
            onNavigate = navigate,
            uiState = uiState.value,
            saveState = saveState.value,
            onRetry = viewModel::load,
            onSave = viewModel::save,
            onInputChanged = viewModel::clearSaveMessage,
        )
    }
    composable(Routes.PatientProfile) {
        val viewModel: PatientProfileViewModel = viewModel(
            factory = PatientProfileViewModel.Factory(patientRepository),
        )
        val uiState = viewModel.uiState.collectAsStateWithLifecycle()
        val consentActionState = viewModel.consentActionState.collectAsStateWithLifecycle()
        val privacyActionState = viewModel.privacyActionState.collectAsStateWithLifecycle()
        val profileActionState = viewModel.profileActionState.collectAsStateWithLifecycle()
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            viewModel.refreshIfStale()
        }
        PatientProfileScreen(
            onNavigate = navigate,
            onLogout = onLogout,
            uiState = uiState.value,
            consentActionState = consentActionState.value,
            privacyActionState = privacyActionState.value,
            profileActionState = profileActionState.value,
            onRetry = viewModel::load,
                onSetConsent = viewModel::setConsent,
                onRevokeConsent = viewModel::revokeConsent,
                onRequestSupervisorUnlink = viewModel::requestSupervisorUnlink,
                onCancelPendingSupervisorRequest = viewModel::cancelPendingSupervisorRequest,
            onClearConsentMessage = viewModel::clearConsentMessage,
            onSavePrivacy = viewModel::savePrivacy,
            onClearPrivacyMessage = viewModel::clearPrivacyMessage,
            onSaveProfile = viewModel::saveProfile,
            onClearProfileMessage = viewModel::clearProfileMessage,
        )
    }
}
