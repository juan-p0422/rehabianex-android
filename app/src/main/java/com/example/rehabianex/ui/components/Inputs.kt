package com.example.rehabianex.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.rehabianex.ui.theme.RehabianexColors
import com.example.rehabianex.validation.PHONE_REGION_INTERNATIONAL
import com.example.rehabianex.validation.PHONE_REGION_MEXICO

@Composable
fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    errorMessage: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = errorMessage != null,
        supportingText = errorMessage?.let { message ->
            { Text(message) }
        },
        shape = RoundedCornerShape(12.dp),
        visualTransformation = if (isPassword && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        trailingIcon = if (isPassword) {
            {
                TextButton(
                    onClick = { passwordVisible = !passwordVisible },
                    enabled = enabled,
                ) {
                    Text(if (passwordVisible) "Ocultar" else "Mostrar")
                }
            }
        } else {
            null
        },
        keyboardOptions = keyboardOptions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = RehabianexColors.GrayCard,
            unfocusedContainerColor = RehabianexColors.GrayCard,
            focusedBorderColor = RehabianexColors.GreenMain,
            unfocusedBorderColor = RehabianexColors.GrayBorder,
            focusedPlaceholderColor = RehabianexColors.GrayText,
            unfocusedPlaceholderColor = RehabianexColors.GrayText,
        ),
    )
}

@Composable
fun PhoneNumberField(
    value: String,
    region: String,
    onValueChange: (String) -> Unit,
    onRegionChange: (String) -> Unit,
    errorMessage: String? = null,
    enabled: Boolean = true,
    allowLegacyInternational: Boolean = false,
) {
    val mexicoLabel = "México +52"
    val internationalLabel = "Otro país (solo lectura)"
    ChipSelector(
        options = if (allowLegacyInternational) {
            listOf(mexicoLabel, internationalLabel)
        } else {
            listOf(mexicoLabel)
        },
        selected = if (region == PHONE_REGION_MEXICO) mexicoLabel else internationalLabel,
        onSelected = { selected ->
            onRegionChange(
                if (selected == mexicoLabel) PHONE_REGION_MEXICO else PHONE_REGION_INTERNATIONAL,
            )
        },
    )
    InputField(
        value = value,
        onValueChange = { raw ->
            val accepted = if (region == PHONE_REGION_MEXICO) {
                raw.filter(Char::isDigit).take(10)
            } else {
                raw.filter { char ->
                    char.isDigit() || char in setOf('+', ' ', '-', '(', ')', '.')
                }.take(24)
            }
            onValueChange(accepted)
        },
        label = if (region == PHONE_REGION_MEXICO) {
            "Teléfono nacional (10 dígitos)"
        } else {
            "Teléfono con código internacional"
        },
        errorMessage = errorMessage,
        enabled = enabled && region == PHONE_REGION_MEXICO,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
    )
    if (region == PHONE_REGION_INTERNATIONAL) {
        Text(
            "Este número antiguo se conserva para consulta. Para editarlo, selecciona México +52.",
            color = RehabianexColors.GrayText,
        )
    }
}

@Composable
fun TextAreaField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp),
        placeholder = { Text(label) },
        minLines = 3,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = RehabianexColors.GrayCard,
            unfocusedContainerColor = RehabianexColors.GrayCard,
            focusedBorderColor = RehabianexColors.GreenMain,
            unfocusedBorderColor = RehabianexColors.GrayBorder,
        ),
    )
}
