package com.example.rehabianex.ui.screens.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.rehabianex.ui.components.BlueCard
import com.example.rehabianex.ui.components.RehabianexButton
import com.example.rehabianex.ui.components.RehabianexSecondaryButton
import com.example.rehabianex.ui.components.ScreenScaffold
import com.example.rehabianex.ui.components.SoftCard
import com.example.rehabianex.ui.components.WarningCard
import com.example.rehabianex.ui.theme.RehabianexColors

@Composable
fun RoleSelectionScreen(
    onContinueAsPatient: () -> Unit,
    onContinueAsSupervisor: () -> Unit,
) {
    ScreenScaffold {
        Text(
            text = "Selecciona tu rol",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "La experiencia se adapta según tu perfil.",
            color = RehabianexColors.GrayText,
        )
        Spacer(Modifier.height(16.dp))
        SoftCard {
            RoleDescription(
                title = "Paciente o supervisado",
                description = "Registra emociones, consulta recursos, agenda actividades y usa tu red de apoyo.",
            )
        }
        Spacer(Modifier.height(10.dp))
        RehabianexButton(
            text = "Continuar como paciente",
            onClick = onContinueAsPatient,
        )
        Spacer(Modifier.height(12.dp))
        BlueCard {
            RoleDescription(
                title = "Doctor, Padrino o Supervisor",
                description = "Consulta información autorizada de usuarios vinculados.",
            )
        }
        Spacer(Modifier.height(10.dp))
        RehabianexSecondaryButton(
            text = "Continuar como supervisor",
            onClick = onContinueAsSupervisor,
        )
        Spacer(Modifier.height(14.dp))
        WarningCard {
            Text(
                text = "El acceso a datos requiere consentimiento explícito y control por roles.",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun RoleDescription(
    title: String,
    description: String,
) {
    Text(
        text = title,
        modifier = Modifier.fillMaxWidth(),
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = description,
        modifier = Modifier.fillMaxWidth(),
        color = RehabianexColors.GrayText,
        textAlign = TextAlign.Center,
    )
}
