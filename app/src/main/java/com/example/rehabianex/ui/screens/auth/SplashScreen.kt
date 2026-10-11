package com.example.rehabianex.ui.screens.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.rehabianex.ui.components.AppLogo
import com.example.rehabianex.ui.components.RehabianexButton
import com.example.rehabianex.ui.components.ScreenScaffold
import com.example.rehabianex.ui.components.SoftCard
import com.example.rehabianex.ui.components.WarningCard
import com.example.rehabianex.ui.theme.RehabianexColors

@Composable
fun SplashScreen(
    onStartClick: () -> Unit,
) {
    ScreenScaffold {
        Spacer(Modifier.height(24.dp))
        AppLogo(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            size = 148.dp,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "RehabiAnex",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Acompañamiento digital para la recuperación",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            color = RehabianexColors.GrayText,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        SoftCard {
            Text(
                text = "Herramienta complementaria para registrar avances, recibir apoyo y mejorar la continuidad del acompañamiento.",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(12.dp))
        WarningCard {
            Text(
                text = "No sustituye atención médica, psicológica o terapéutica.",
                modifier = Modifier.fillMaxWidth(),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(14.dp))
        RehabianexButton(
            text = "Comenzar",
            onClick = onStartClick,
        )
        Spacer(Modifier.height(12.dp))
    }
}
