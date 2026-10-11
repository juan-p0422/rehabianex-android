package com.example.rehabianex.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.rehabianex.ui.theme.RehabianexColors

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
        visualTransformation = if (isPassword) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
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
