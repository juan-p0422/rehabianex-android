package com.example.rehabianex.ui.screens.admin

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rehabianex.R
import com.example.rehabianex.data.model.AdminSupervisorDto
import com.example.rehabianex.navigation.Routes
import com.example.rehabianex.ui.components.BottomNavItem
import com.example.rehabianex.ui.components.BottomNavigationBar
import com.example.rehabianex.ui.components.DangerCard
import com.example.rehabianex.ui.components.HomeMenuItem
import com.example.rehabianex.ui.components.HomeOverflowMenu
import com.example.rehabianex.ui.components.RehabianexButton
import com.example.rehabianex.ui.components.RehabianexCard
import com.example.rehabianex.ui.components.RehabianexDangerButton
import com.example.rehabianex.ui.components.RehabianexOutlineButton
import com.example.rehabianex.ui.components.ScreenHeader
import com.example.rehabianex.ui.components.ScreenScaffold
import com.example.rehabianex.ui.components.SectionTitle
import com.example.rehabianex.ui.components.SoftCard
import com.example.rehabianex.ui.theme.RehabianexColors
import java.text.DateFormat
import java.util.Date

@Composable
fun AdminHomeScreen(
    uiState: AdminSupervisorsUiState,
    actionState: AdminSupervisorActionUiState,
    onRetry: () -> Unit,
    onSelectFilter: (AdminSupervisorFilter) -> Unit,
    onAuthorize: (String) -> Unit,
    onReject: (String, String) -> Unit,
    onSuspend: (String, String) -> Unit,
    onReactivate: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
) {
    var reasonActionUid by rememberSaveable { mutableStateOf<String?>(null) }
    var reasonActionName by rememberSaveable { mutableStateOf("") }
    var reasonActionType by rememberSaveable { mutableStateOf<String?>(null) }
    var actionReason by rememberSaveable { mutableStateOf("") }
    val actionLoading = actionState is AdminSupervisorActionUiState.Loading

    ScreenScaffold(
        bottomBar = {
            BottomNavigationBar(
                items = AdminBottomNavItems,
                selectedRoute = uiState.filter.navigationRoute(),
                onItemClick = { item ->
                    item.route.toAdminFilter()?.let(onSelectFilter)
                },
            )
        },
    ) {
        HomeOverflowMenu(
            items = listOf(
                HomeMenuItem(
                    label = "Demostración FCM",
                    route = Routes.FcmDemo,
                    iconRes = R.drawable.ic_nav_alerts,
                ),
                HomeMenuItem(
                    label = "Cerrar sesión",
                    route = ADMIN_LOGOUT_ACTION,
                    iconRes = R.drawable.ic_nav_more,
                ),
            ),
            onNavigate = { action ->
                if (action == ADMIN_LOGOUT_ACTION && !actionLoading) onLogout()
                else if (!actionLoading) onNavigate(action)
            },
        )
        Spacer(Modifier.height(4.dp))
        ScreenHeader(
            title = "Panel de administración",
            subtitle = "Gestión de cuentas de supervisión",
        )
        Spacer(Modifier.height(16.dp))
        SoftCard {
            Text(
                text = "Cuenta administradora activa.",
                fontWeight = FontWeight.Bold,
            )
        }
        (uiState as? AdminSupervisorsUiState.Content)?.dashboard?.let { summary ->
            Spacer(Modifier.height(10.dp))
            SoftCard {
                Text("Resumen seguro", fontWeight = FontWeight.Bold)
                Text("Pendientes: ${summary.pendingSupervisors}")
                Text("Activos: ${summary.activeSupervisors}")
                Text("Suspendidos: ${summary.suspendedSupervisors}")
                when {
                    uiState.isRefreshing -> Text("Actualizando...", color = RehabianexColors.GrayText)
                    uiState.refreshMessage != null -> Text(
                        "Última actualización: ${summary.lastSyncedAt.asUpdateTime()}",
                        color = RehabianexColors.GrayText,
                    )
                }
            }
            uiState.refreshMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(message, color = RehabianexColors.GrayText)
            }
        }
        Spacer(Modifier.height(16.dp))
        AdminActionNotice(actionState)
        SectionTitle(uiState.filter.sectionTitle())
        Spacer(Modifier.height(10.dp))

        when (uiState) {
            is AdminSupervisorsUiState.Loading -> RehabianexCard {
                Text("Consultando ${uiState.filter.loadingLabel()}...")
            }

            is AdminSupervisorsUiState.Error -> DangerCard {
                Text(uiState.message, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                RehabianexButton(
                    text = "Reintentar",
                    enabled = !actionLoading,
                    onClick = onRetry,
                )
            }

            is AdminSupervisorsUiState.Content -> {
                if (!uiState.listAuthoritative) {
                    SoftCard {
                        Text(
                            "La lista requiere conexión y se está actualizando. El resumen guardado no permite realizar acciones.",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else if (uiState.supervisors.isEmpty()) {
                    SoftCard {
                        Text(
                            text = uiState.filter.emptyMessage(),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    uiState.supervisors.forEachIndexed { index, supervisor ->
                        if (index > 0) Spacer(Modifier.height(10.dp))
                        SupervisorManagementCard(
                            supervisor = supervisor,
                            filter = uiState.filter,
                            actionLoading = actionLoading,
                            onAuthorize = onAuthorize,
                            onReject = { uid, name ->
                                reasonActionUid = uid
                                reasonActionName = name
                                reasonActionType = REJECT_ACTION
                                actionReason = ""
                            },
                            onSuspend = { uid, name ->
                                reasonActionUid = uid
                                reasonActionName = name
                                reasonActionType = SUSPEND_ACTION
                                actionReason = ""
                            },
                            onReactivate = onReactivate,
                        )
                    }
                }
            }
        }
    }

    reasonActionUid?.let { uid ->
        val suspending = reasonActionType == SUSPEND_ACTION
        AlertDialog(
            onDismissRequest = {
                if (!actionLoading) reasonActionUid = null
            },
            title = {
                Text(if (suspending) "Suspender supervisor" else "Rechazar supervisor")
            },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text(
                        if (suspending) {
                            "Confirma la suspensión de $reasonActionName e indica una razón breve."
                        } else {
                            "Indica una razón breve para rechazar a $reasonActionName."
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = actionReason,
                        onValueChange = {
                            actionReason = it.take(MAX_ACTION_REASON_LENGTH)
                        },
                        modifier = Modifier,
                        label = { Text("Razón") },
                        enabled = !actionLoading,
                        singleLine = false,
                        minLines = 2,
                        maxLines = 4,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (suspending) {
                            onSuspend(uid, actionReason.trim())
                        } else {
                            onReject(uid, actionReason.trim())
                        }
                        reasonActionUid = null
                    },
                    enabled = actionReason.isNotBlank() && !actionLoading,
                ) {
                    Text(if (suspending) "Confirmar suspensión" else "Confirmar rechazo")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { reasonActionUid = null },
                    enabled = !actionLoading,
                ) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
private fun SupervisorManagementCard(
    supervisor: AdminSupervisorDto,
    filter: AdminSupervisorFilter,
    actionLoading: Boolean,
    onAuthorize: (String) -> Unit,
    onReject: (String, String) -> Unit,
    onSuspend: (String, String) -> Unit,
    onReactivate: (String) -> Unit,
) {
    val uid = supervisor.uid.orEmpty()
    val fullName = supervisor.fullName.orReadable("Supervisor sin nombre")
    val canAct = uid.isNotBlank() && !actionLoading

    RehabianexCard {
        Text(
            text = fullName,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        AdminField("Tipo", supervisor.supervisorType.supervisorTypeLabel())
        AdminField("Código", supervisor.supervisorCode.orReadable("Sin asignar"))
        AdminField("Estado", supervisor.status.statusLabel())
        AdminField("Autorizado", supervisor.authorized.yesNoLabel())
        AdminField("Verificado", supervisor.verified.yesNoLabel())
        Spacer(Modifier.height(12.dp))
        when (filter) {
            AdminSupervisorFilter.PendingReview -> {
                RehabianexButton(
                    text = "Autorizar",
                    enabled = canAct,
                    onClick = { onAuthorize(uid) },
                )
                Spacer(Modifier.height(8.dp))
                RehabianexOutlineButton(
                    text = "Rechazar",
                    enabled = canAct,
                    onClick = { onReject(uid, fullName) },
                )
            }

            AdminSupervisorFilter.Active -> {
                RehabianexDangerButton(
                    text = "Suspender",
                    enabled = canAct,
                    onClick = { onSuspend(uid, fullName) },
                )
            }

            AdminSupervisorFilter.Suspended -> {
                RehabianexButton(
                    text = "Reactivar",
                    enabled = canAct,
                    onClick = { onReactivate(uid) },
                )
            }
        }
    }
}

@Composable
private fun AdminField(
    label: String,
    value: String,
) {
    Text(
        text = "$label: $value",
        color = RehabianexColors.GrayText,
    )
}

@Composable
private fun AdminActionNotice(actionState: AdminSupervisorActionUiState) {
    when (actionState) {
        AdminSupervisorActionUiState.Idle -> Unit
        is AdminSupervisorActionUiState.Loading -> {
            SoftCard {
                Text("Procesando solicitud administrativa...")
            }
            Spacer(Modifier.height(12.dp))
        }

        is AdminSupervisorActionUiState.Success -> {
            SoftCard {
                Text(actionState.message, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
        }

        is AdminSupervisorActionUiState.Error -> {
            DangerCard {
                Text(actionState.message, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun String?.orReadable(fallback: String): String =
    this?.trim()?.takeIf(String::isNotEmpty) ?: fallback

private fun String?.supervisorTypeLabel(): String = when (this?.trim()?.lowercase()) {
    "clinical_psychologist" -> "Psicología clínica"
    "psychiatrist" -> "Psiquiatría"
    "support_sponsor" -> "Padrino o madrina"
    else -> orReadable("Sin especificar")
}

private fun String?.statusLabel(): String = when (this?.trim()?.lowercase()) {
    "pending_review", "pending" -> "Pendiente de revisión"
    "active" -> "Activo"
    "rejected" -> "Rechazado"
    "suspended" -> "Suspendido"
    else -> orReadable("Sin estado")
}

private fun Boolean?.yesNoLabel(): String = if (this == true) "Sí" else "No"

private fun AdminSupervisorFilter.sectionTitle(): String = when (this) {
    AdminSupervisorFilter.PendingReview -> "Supervisores pendientes"
    AdminSupervisorFilter.Active -> "Supervisores activos"
    AdminSupervisorFilter.Suspended -> "Supervisores suspendidos"
}

private fun AdminSupervisorFilter.loadingLabel(): String = sectionTitle().lowercase()

private fun AdminSupervisorFilter.emptyMessage(): String = when (this) {
    AdminSupervisorFilter.PendingReview -> "No hay supervisores pendientes de revisión."
    AdminSupervisorFilter.Active -> "No hay supervisores activos."
    AdminSupervisorFilter.Suspended -> "No hay supervisores suspendidos."
}

@Composable
fun AdminPanelUnavailableScreen(
    onLogout: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    ScreenScaffold {
        HomeOverflowMenu(
            items = listOf(
                HomeMenuItem(
                    label = "Demostración FCM",
                    route = Routes.FcmDemo,
                    iconRes = R.drawable.ic_nav_alerts,
                ),
                HomeMenuItem(
                    label = "Cerrar sesión",
                    route = ADMIN_LOGOUT_ACTION,
                    iconRes = R.drawable.ic_nav_more,
                ),
            ),
            onNavigate = { action ->
                if (action == ADMIN_LOGOUT_ACTION) onLogout()
                else onNavigate(action)
            },
        )
        Spacer(Modifier.height(4.dp))
        ScreenHeader(
            title = "Administración no disponible",
            subtitle = "Esta función está deshabilitada en esta versión",
        )
        Spacer(Modifier.height(16.dp))
        SoftCard {
            Text(
                text = "El panel administrativo solo está habilitado en entornos internos autorizados.",
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private val AdminBottomNavItems = listOf(
    BottomNavItem("Pendientes", ADMIN_PENDING_ROUTE, R.drawable.ic_nav_alerts),
    BottomNavItem("Activos", ADMIN_ACTIVE_ROUTE, R.drawable.ic_nav_patients),
    BottomNavItem("Suspendidos", ADMIN_SUSPENDED_ROUTE, R.drawable.ic_nav_support),
)

private fun AdminSupervisorFilter.navigationRoute(): String = when (this) {
    AdminSupervisorFilter.PendingReview -> ADMIN_PENDING_ROUTE
    AdminSupervisorFilter.Active -> ADMIN_ACTIVE_ROUTE
    AdminSupervisorFilter.Suspended -> ADMIN_SUSPENDED_ROUTE
}

private fun String.toAdminFilter(): AdminSupervisorFilter? = when (this) {
    ADMIN_PENDING_ROUTE -> AdminSupervisorFilter.PendingReview
    ADMIN_ACTIVE_ROUTE -> AdminSupervisorFilter.Active
    ADMIN_SUSPENDED_ROUTE -> AdminSupervisorFilter.Suspended
    else -> null
}

private const val REJECT_ACTION = "reject"
private const val SUSPEND_ACTION = "suspend"
private const val MAX_ACTION_REASON_LENGTH = 1_000
private const val ADMIN_LOGOUT_ACTION = "admin_logout_action"
private const val ADMIN_PENDING_ROUTE = "admin_pending"
private const val ADMIN_ACTIVE_ROUTE = "admin_active"
private const val ADMIN_SUSPENDED_ROUTE = "admin_suspended"

private fun Long.asUpdateTime(): String = DateFormat.getDateTimeInstance(
    DateFormat.SHORT,
    DateFormat.SHORT,
).format(Date(this))
