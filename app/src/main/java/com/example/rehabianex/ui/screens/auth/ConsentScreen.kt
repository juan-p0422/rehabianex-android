package com.example.rehabianex.ui.screens.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rehabianex.ui.components.BlueCard
import com.example.rehabianex.ui.components.RehabianexButton
import com.example.rehabianex.ui.components.RehabianexCard
import com.example.rehabianex.ui.components.RehabianexOutlineButton
import com.example.rehabianex.ui.components.ScreenScaffold
import com.example.rehabianex.ui.components.WarningCard
import com.example.rehabianex.ui.theme.RehabianexColors

@Composable
fun ConsentScreen(
    onConfigureSupervisionClick: () -> Unit,
    onContinueWithoutSupervisionClick: () -> Unit,
) {
    ScreenScaffold {
        Text(
            text = "Supervisión opcional",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "Permisos independientes del aviso de privacidad",
            color = RehabianexColors.GrayText,
        )
        Spacer(Modifier.height(14.dp))
        BlueCard {
            Text(
                text = "Aceptar el aviso de privacidad no comparte tus datos con un supervisor.",
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(12.dp))
        ConsentItem("Elige voluntariamente si deseas vincular un supervisor")
        Spacer(Modifier.height(10.dp))
        ConsentItem("Revisa qué datos y permisos deseas compartir")
        Spacer(Modifier.height(10.dp))
        ConsentItem("Confirma la solicitud antes de crear el consentimiento")
        Spacer(Modifier.height(12.dp))
        WarningCard {
            Text(
                "La supervisión puede configurarse después. RehabiAnex no sustituye atención médica, psicológica ni terapéutica.",
            )
        }
        Spacer(Modifier.height(14.dp))
        RehabianexButton(
            text = "Configurar supervisión",
            onClick = onConfigureSupervisionClick,
        )
        Spacer(Modifier.height(9.dp))
        RehabianexOutlineButton(
            text = "Continuar sin supervisor",
            onClick = onContinueWithoutSupervisionClick,
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ConsentItem(text: String) {
    RehabianexCard {
        Text("✓  $text")
    }
}
