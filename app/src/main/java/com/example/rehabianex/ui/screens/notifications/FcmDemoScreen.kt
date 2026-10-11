package com.example.rehabianex.ui.screens.notifications

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rehabianex.notifications.FcmDemoCase
import com.example.rehabianex.notifications.FcmDemoCatalog
import com.example.rehabianex.ui.components.DangerCard
import com.example.rehabianex.ui.components.RehabianexButton
import com.example.rehabianex.ui.components.RehabianexCard
import com.example.rehabianex.ui.components.RehabianexOutlineButton
import com.example.rehabianex.ui.components.ScreenHeader
import com.example.rehabianex.ui.components.ScreenScaffold
import com.example.rehabianex.ui.components.SoftCard
import com.example.rehabianex.ui.theme.RehabianexColors

@Composable
fun FcmDemoScreen(
    role: String?,
    uiState: FcmDemoUiState,
    onSend: (String) -> Unit,
    onBack: () -> Unit,
) {
    val cases = FcmDemoCatalog.forRole(role)
    val sendingType = (uiState as? FcmDemoUiState.Sending)?.type

    ScreenScaffold {
        ScreenHeader(
            title = "Demostración de notificaciones",
            subtitle = "Envío real al dispositivo de esta sesión",
        )
        Spacer(Modifier.height(12.dp))
        SoftCard {
            Text("Uso seguro", fontWeight = FontWeight.Bold)
            Text(
                "Cada botón envía un caso técnico genérico únicamente a tus dispositivos activos. " +
                    "No crea recaídas, citas, notas ni otros datos reales.",
            )
        }
        Spacer(Modifier.height(12.dp))
        when (uiState) {
            FcmDemoUiState.Idle -> Unit
            is FcmDemoUiState.Sending -> SoftCard {
                CircularProgressIndicator(color = RehabianexColors.GreenMain)
                Spacer(Modifier.height(6.dp))
                Text("Solicitando notificación...")
            }
            is FcmDemoUiState.Success -> SoftCard {
                Text("Notificación enviada.", fontWeight = FontWeight.Bold)
                Text("Abre el panel de notificaciones para verla y tocarla.")
            }
            is FcmDemoUiState.Error -> DangerCard {
                Text("No fue posible enviar la demostración.", fontWeight = FontWeight.Bold)
                Text(uiState.message)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (cases.isEmpty()) {
            DangerCard { Text("La sesión actual no tiene un rol compatible con la demostración.") }
        } else {
            cases.forEach { demoCase ->
                DemoCaseCard(
                    demoCase = demoCase,
                    enabled = sendingType == null,
                    isSending = sendingType == demoCase.type,
                    onSend = onSend,
                )
                Spacer(Modifier.height(10.dp))
            }
        }
        RehabianexOutlineButton(text = "Volver", onClick = onBack)
    }
}

@Composable
private fun DemoCaseCard(
    demoCase: FcmDemoCase,
    enabled: Boolean,
    isSending: Boolean,
    onSend: (String) -> Unit,
) {
    RehabianexCard {
        Text(demoCase.event, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(demoCase.body)
        Spacer(Modifier.height(4.dp))
        Text(demoCase.type, color = RehabianexColors.GrayText)
        Spacer(Modifier.height(8.dp))
        RehabianexButton(
            text = if (isSending) "Enviando..." else "Forzar notificación",
            enabled = enabled,
            onClick = { onSend(demoCase.type) },
        )
    }
}
