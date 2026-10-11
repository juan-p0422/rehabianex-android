package com.example.rehabianex.ui.screens.patient

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rehabianex.navigation.Routes
import com.example.rehabianex.data.model.PatientSupervisionState
import com.example.rehabianex.ui.components.BlueCard
import com.example.rehabianex.ui.components.DangerCard
import com.example.rehabianex.ui.components.InputField
import com.example.rehabianex.ui.components.PatientScreenScaffold
import com.example.rehabianex.ui.components.RehabianexButton
import com.example.rehabianex.ui.components.RehabianexCard
import com.example.rehabianex.ui.components.ScreenHeader
import com.example.rehabianex.ui.components.SoftCard
import com.example.rehabianex.ui.components.WarningCard
import com.example.rehabianex.ui.theme.RehabianexColors

@Composable
fun SupervisorLinkScreen(
    onNavigate: (String) -> Unit,
    uiState: SupervisorLinkUiState,
    onRequestLink: (String) -> Unit,
    onInputChanged: () -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    val isLoading = uiState is SupervisorLinkUiState.Loading
    val fieldErrors = (uiState as? SupervisorLinkUiState.Error)?.fieldErrors.orEmpty()
    val canRequestLink = when (uiState) {
        is SupervisorLinkUiState.Status ->
            uiState.supervisionState == PatientSupervisionState.NoSupervisor ||
                uiState.supervisionState == PatientSupervisionState.LinkRejected ||
                uiState.supervisionState == PatientSupervisionState.Unlinked
        is SupervisorLinkUiState.Success -> false
        else -> true
    }

    fun updateCode(value: String) {
        if (!isLoading) {
            code = value
                .filterNot { it.isISOControl() }
                .replace("<", "")
                .replace(">", "")
                .uppercase()
                .take(32)
            onInputChanged()
        }
    }

    PatientScreenScaffold(Routes.SupervisorLink, onNavigate) {
        ScreenHeader("Supervisor autorizado", "Vinculación segura")
        Spacer(Modifier.height(12.dp))
        BlueCard {
            Text(
                "Ingresa el código corto del supervisor. El acceso a tus datos comenzará solo " +
                    "cuando la solicitud sea aceptada y tu permiso siga activo.",
            )
        }
        Spacer(Modifier.height(12.dp))
        InputField(
            value = code,
            onValueChange = ::updateCode,
            label = "Código de supervisor",
            errorMessage = fieldErrors["code"] ?: fieldErrors["supervisorUid"],
            enabled = !isLoading && canRequestLink,
        )
        Spacer(Modifier.height(12.dp))
        RehabianexButton(
            text = when {
                isLoading -> "Verificando..."
                uiState is SupervisorLinkUiState.Preview -> "Confirmar solicitud"
                else -> "Verificar supervisor"
            },
            enabled = !isLoading && canRequestLink,
        ) {
            onRequestLink(code)
        }
        Spacer(Modifier.height(12.dp))
        LinkStatusCard(uiState)
        Spacer(Modifier.height(12.dp))
        RehabianexCard {
            Text("¿Qué pasa después?", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("1. Tu solicitud queda pendiente.")
            Text("2. El supervisor debe aceptarla desde su cuenta.")
            Text("3. Tu perfil queda vinculado cuando el supervisor acepta.")
            Text("4. Puedes pausar el acceso desde privacidad.")
        }
        Spacer(Modifier.height(12.dp))
        WarningCard {
            Text("Puedes revocar el acceso desde privacidad. Al hacerlo, tu supervisor dejará de consultar tus registros.")
        }
    }
}

@Composable
private fun LinkStatusCard(uiState: SupervisorLinkUiState) {
    when (uiState) {
        SupervisorLinkUiState.Idle -> RehabianexCard {
            Text("Estado de solicitud:")
            Text("Sin solicitud activa", fontWeight = FontWeight.Bold)
        }
        SupervisorLinkUiState.Loading -> RehabianexCard {
            CircularProgressIndicator(color = RehabianexColors.GreenMain)
            Spacer(Modifier.height(8.dp))
            Text("Verificando información del supervisor...")
        }
        is SupervisorLinkUiState.Status -> RehabianexCard {
            Text("Estado de supervisión:")
            Text(
                when (uiState.supervisionState) {
                    PatientSupervisionState.NoSupervisor -> "Sin supervisor"
                    PatientSupervisionState.LinkPending -> "Solicitud pendiente"
                    PatientSupervisionState.LinkRejected -> "Solicitud rechazada"
                    PatientSupervisionState.Active -> "Supervisión activa"
                PatientSupervisionState.Paused -> "Acceso pausado"
                PatientSupervisionState.Revoked -> "Consentimiento revocado"
                PatientSupervisionState.UnlinkPending -> "Desvinculación pendiente"
                    PatientSupervisionState.Unlinked -> "Desvinculado"
                },
                fontWeight = FontWeight.Bold,
            )
        }
        is SupervisorLinkUiState.Preview -> SoftCard {
            Text("Supervisor encontrado", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(uiState.supervisor.fullName)
            Text(uiState.supervisor.supervisorType, color = RehabianexColors.GrayText)
            Text(
                if (uiState.supervisor.available) "Disponible" else "No disponible",
                color = if (uiState.supervisor.available) {
                    RehabianexColors.GreenDark
                } else {
                    RehabianexColors.Red
                },
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text("Código: ${uiState.supervisor.code}")
            Spacer(Modifier.height(8.dp))
            Text("Confirma para enviar la solicitud de vinculación.")
        }
        is SupervisorLinkUiState.Success -> SoftCard {
            Text("Solicitud enviada", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(uiState.message)
            Spacer(Modifier.height(4.dp))
            Text("Estado: Pendiente de aceptación del supervisor.")
        }
        is SupervisorLinkUiState.Error -> DangerCard {
            Text("No se pudo enviar", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(uiState.message)
        }
    }
}
