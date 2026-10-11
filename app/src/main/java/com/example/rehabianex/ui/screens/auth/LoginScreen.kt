package com.example.rehabianex.ui.screens.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.rehabianex.ui.components.AppLogo
import com.example.rehabianex.ui.components.BlueCard
import com.example.rehabianex.ui.components.DangerCard
import com.example.rehabianex.ui.components.InputField
import com.example.rehabianex.ui.components.RehabianexButton
import com.example.rehabianex.ui.components.RehabianexSecondaryButton
import com.example.rehabianex.ui.components.ScreenScaffold
import com.example.rehabianex.ui.components.WarningCard
import com.example.rehabianex.ui.theme.RehabianexColors

@Composable
fun LoginScreen(
    onLoginClick: (email: String, password: String) -> Unit,
    onRegisterClick: () -> Unit,
    uiState: AuthUiState = AuthUiState.Idle,
    onInputChanged: () -> Unit = {},
    infoMessage: String? = null,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val fieldErrors = (uiState as? AuthUiState.Error)?.fieldErrors.orEmpty()
    val isLoading = uiState is AuthUiState.Loading

    ScreenScaffold {
        Spacer(Modifier.height(14.dp))
        AppLogo(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            size = 122.dp,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Iniciar sesión",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Bienvenido a RehabiAnex",
            modifier = Modifier.fillMaxWidth(),
            color = RehabianexColors.GrayText,
            textAlign = TextAlign.Center,
        )
        infoMessage?.let {
            Spacer(Modifier.height(12.dp))
            BlueCard {
                Text(it)
            }
        }
        Spacer(Modifier.height(16.dp))
        InputField(
            value = email,
            onValueChange = {
                email = it.take(254)
                onInputChanged()
            },
            label = "Correo electrónico",
            errorMessage = fieldErrors["email"],
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Spacer(Modifier.height(10.dp))
        InputField(
            value = password,
            onValueChange = {
                password = it.take(128)
                onInputChanged()
            },
            label = "Contraseña",
            isPassword = true,
            errorMessage = fieldErrors["password"],
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        if (uiState is AuthUiState.Error) {
            Spacer(Modifier.height(12.dp))
            DangerCard {
                Text(uiState.message)
            }
        }
        Spacer(Modifier.height(14.dp))
        RehabianexButton(
            text = if (isLoading) "Iniciando sesión..." else "Iniciar sesión",
            onClick = { onLoginClick(email, password) },
            enabled = !isLoading,
        )
        Spacer(Modifier.height(8.dp))
        RehabianexSecondaryButton(
            text = "Crear cuenta",
            onClick = onRegisterClick,
        )
        Spacer(Modifier.height(12.dp))
        WarningCard {
            Text(
                text = "Tus datos se manejan bajo consentimiento y privacidad.",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}
