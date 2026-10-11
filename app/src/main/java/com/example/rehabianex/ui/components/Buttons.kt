package com.example.rehabianex.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.rehabianex.ui.theme.RehabianexColors

private val ButtonShape = RoundedCornerShape(24.dp)

@Composable
fun RehabianexButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FilledRehabianexButton(
        text = text,
        onClick = onClick,
        containerColor = RehabianexColors.GreenMain,
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
fun RehabianexSecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FilledRehabianexButton(
        text = text,
        onClick = onClick,
        containerColor = RehabianexColors.BlueSoft,
        modifier = modifier,
    )
}

@Composable
fun RehabianexDangerButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FilledRehabianexButton(
        text = text,
        onClick = onClick,
        containerColor = RehabianexColors.Red,
        modifier = modifier,
        enabled = enabled,
    )
}

@Composable
fun RehabianexOutlineButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        enabled = enabled,
        shape = ButtonShape,
        border = BorderStroke(1.dp, RehabianexColors.GreenMain),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = RehabianexColors.GreenDark,
            containerColor = Color.White,
        ),
    ) {
        Text(text)
    }
}

@Composable
private fun FilledRehabianexButton(
    text: String,
    onClick: () -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
    ) {
        Text(text)
    }
}
