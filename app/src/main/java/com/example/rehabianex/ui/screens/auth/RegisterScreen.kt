package com.example.rehabianex.ui.screens.auth

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.rehabianex.ui.components.ChipSelector
import com.example.rehabianex.ui.components.DangerCard
import com.example.rehabianex.ui.components.InputField
import com.example.rehabianex.ui.components.PhoneNumberField
import com.example.rehabianex.ui.components.RehabianexButton
import com.example.rehabianex.ui.components.RehabianexOutlineButton
import com.example.rehabianex.ui.components.ScreenScaffold
import com.example.rehabianex.ui.components.SoftCard
import com.example.rehabianex.ui.components.WarningCard
import com.example.rehabianex.ui.theme.RehabianexColors
import com.example.rehabianex.data.model.PRIVACY_NOTICE_VERSION
import com.example.rehabianex.validation.RegisterFormInput
import com.example.rehabianex.validation.PHONE_REGION_MEXICO
import com.example.rehabianex.validation.SUPERVISOR_TYPE_OPTIONS
import com.example.rehabianex.validation.SUPERVISOR_TYPE_OTHER

@Composable
fun RegisterScreen(
    onRegisterClick: (RegisterFormInput) -> Unit,
    onPrivacyNoticeClick: () -> Unit,
    uiState: AuthUiState = AuthUiState.Idle,
    onInputChanged: () -> Unit = {},
) {
    var fullName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordConfirmation by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf("patient") }
    var phone by rememberSaveable { mutableStateOf("") }
    var phoneRegion by rememberSaveable { mutableStateOf(PHONE_REGION_MEXICO) }
    var gender by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf("") }
    var supervisorType by rememberSaveable {
        mutableStateOf("clinical_psychologist")
    }
    var supervisorTypeOther by rememberSaveable { mutableStateOf("") }
    var acceptedPrivacy by rememberSaveable { mutableStateOf(false) }
    val fieldErrors = (uiState as? AuthUiState.Error)?.fieldErrors.orEmpty()
    val isLoading = uiState is AuthUiState.Loading

    ScreenScaffold {
        Text(
            text = "Crear cuenta",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "Completa tus datos iniciales",
            color = RehabianexColors.GrayText,
        )
        Spacer(Modifier.height(14.dp))
        InputField(
            value = fullName,
            onValueChange = {
                fullName = it.take(100)
                onInputChanged()
            },
            label = "Nombre completo",
            errorMessage = fieldErrors["fullName"],
            enabled = !isLoading,
        )
        Spacer(Modifier.height(9.dp))
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
        Spacer(Modifier.height(9.dp))
        PhoneNumberField(
            value = phone,
            region = phoneRegion,
            onValueChange = {
                phone = it
                onInputChanged()
            },
            onRegionChange = {
                phoneRegion = it
                phone = ""
                onInputChanged()
            },
            errorMessage = fieldErrors["phone"],
            enabled = !isLoading,
        )
        Spacer(Modifier.height(9.dp))
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
        Spacer(Modifier.height(9.dp))
        InputField(
            value = passwordConfirmation,
            onValueChange = {
                passwordConfirmation = it.take(128)
                onInputChanged()
            },
            label = "Confirmar contraseña",
            isPassword = true,
            errorMessage = fieldErrors["passwordConfirmation"],
            enabled = !isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Spacer(Modifier.height(12.dp))
        SoftCard {
            Text(
                text = "Tipo de cuenta",
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            ChipSelector(
                options = listOf("Paciente", "Supervisor"),
                selected = if (role == "patient") "Paciente" else "Supervisor",
                onSelected = {
                    role = if (it == "Paciente") "patient" else "supervisor"
                    onInputChanged()
                },
            )
            fieldErrors["role"]?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (role == "patient") {
            SoftCard {
                Text(
                    text = "Datos del paciente",
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                ChipSelector(
                    options = listOf("Mujer", "Hombre", "Otro"),
                    selected = when (gender) {
                        "female" -> "Mujer"
                        "male" -> "Hombre"
                        "other" -> "Otro"
                        else -> ""
                    },
                    onSelected = {
                        gender = when (it) {
                            "Mujer" -> "female"
                            "Hombre" -> "male"
                            else -> "other"
                        }
                        onInputChanged()
                    },
                )
                fieldErrors["gender"]?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(6.dp))
                InputField(
                    value = age,
                    onValueChange = {
                        age = it.filter(Char::isDigit).take(3)
                        onInputChanged()
                    },
                    label = "Edad",
                    errorMessage = fieldErrors["age"],
                    enabled = !isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        } else {
            SoftCard {
                Text(
                    text = "Datos del supervisor",
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                ChipSelector(
                    options = SUPERVISOR_TYPE_OPTIONS.values.toList(),
                    selected = SUPERVISOR_TYPE_OPTIONS[supervisorType] ?: "Otro",
                    onSelected = { label ->
                        supervisorType = SUPERVISOR_TYPE_OPTIONS.entries
                            .first { it.value == label }
                            .key
                        onInputChanged()
                    },
                )
                fieldErrors["supervisorType"]?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                if (supervisorType == SUPERVISOR_TYPE_OTHER) {
                    Spacer(Modifier.height(6.dp))
                    InputField(
                        value = supervisorTypeOther,
                        onValueChange = {
                            supervisorTypeOther = it.take(80)
                            onInputChanged()
                        },
                        label = "Describe el tipo de supervisor",
                        errorMessage = fieldErrors["supervisorTypeOther"],
                        enabled = !isLoading,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        RehabianexOutlineButton(
            text = "Leer Aviso de Privacidad completo",
            enabled = !isLoading,
            onClick = onPrivacyNoticeClick,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = acceptedPrivacy,
                onCheckedChange = {
                    acceptedPrivacy = it
                    onInputChanged()
                },
                enabled = !isLoading,
            )
            Text(
                text = "Acepto el tratamiento de mis datos conforme al Aviso de Privacidad " +
                    "(versión $PRIVACY_NOTICE_VERSION).",
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        fieldErrors["privacy"]?.let {
            Text(
                text = it,
                modifier = Modifier.padding(start = 12.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(8.dp))
        SoftCard {
            Text(
                text = "Esta aceptación permite tratar los datos necesarios para crear y operar tu cuenta.",
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "No autoriza a ningún supervisor. La vinculación y los permisos para compartir datos se configuran después mediante un consentimiento independiente.",
                color = RehabianexColors.GrayText,
            )
        }
        Spacer(Modifier.height(8.dp))
        WarningCard {
            Text("RehabiAnex no sustituye atención médica, psicológica ni terapéutica.")
        }
        if (uiState is AuthUiState.Error) {
            Spacer(Modifier.height(10.dp))
            DangerCard {
                Text(uiState.message)
            }
        }
        Spacer(Modifier.height(10.dp))
        RehabianexButton(
            text = if (isLoading) "Creando cuenta..." else "Registrar",
            onClick = {
                onRegisterClick(
                    RegisterFormInput(
                        fullName = fullName,
                        email = email,
                        password = password,
                        passwordConfirmation = passwordConfirmation,
                        role = role,
                        phone = phone,
                        gender = gender,
                        age = age,
                        supervisorType = supervisorType,
                        acceptedPrivacy = acceptedPrivacy,
                        phoneRegion = phoneRegion,
                        supervisorTypeOther = supervisorTypeOther,
                    ),
                )
            },
            enabled = !isLoading && acceptedPrivacy,
        )
        Spacer(Modifier.height(12.dp))
    }
}
