package com.example.rehabianex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.rehabianex.ui.theme.RehabianexColors

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
    )
    subtitle?.let {
        Spacer(Modifier.height(4.dp))
        Text(
            text = it,
            color = RehabianexColors.GrayText,
        )
    }
}

@Composable
fun BackLink(
    onClick: () -> Unit,
    text: String = "← Volver",
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 0.dp),
    ) {
        Text(text, color = RehabianexColors.GreenDark)
    }
}

@Composable
fun LabelValueCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    background: Color = RehabianexColors.GrayCard,
) {
    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, textAlign = TextAlign.Center)
            Spacer(Modifier.height(3.dp))
            Text(
                text = value,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun ActionCard(
    title: String,
    subtitle: String,
    buttonText: String? = null,
    onClick: (() -> Unit)? = null,
) {
    RehabianexCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = RehabianexColors.GrayText)
        if (buttonText != null && onClick != null) {
            Spacer(Modifier.height(10.dp))
            RehabianexSecondaryButton(
                text = buttonText,
                onClick = onClick,
            )
        }
    }
}

@Composable
fun AvatarCircle(
    initials: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(58.dp)
            .background(RehabianexColors.GreenSoft, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SettingSwitchCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    RehabianexCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(description, color = RehabianexColors.GrayText)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = RehabianexColors.GreenMain,
                ),
            )
        }
    }
}

@Composable
fun SimpleBarChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
) {
    val accessibleValues = values.joinToString(separator = ", ") { value ->
        "${(value.coerceIn(0f, 1f) * 100).toInt()} por ciento"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp)
            .background(RehabianexColors.GreenVerySoft, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .clearAndSetSemantics {
                contentDescription = "Valores de la tendencia: $accessibleValues"
            },
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        values.forEach { value ->
            val normalizedValue = value.coerceIn(0.1f, 1f)
            Box(
                modifier = Modifier
                    .size(width = 26.dp, height = (80 * normalizedValue).dp)
                    .background(
                        chartColorForValue(normalizedValue),
                        RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                    ),
            )
        }
    }
}

private fun chartColorForValue(value: Float) = when {
    value <= 0.3f -> RehabianexColors.GreenMain
    value <= 0.5f -> RehabianexColors.BlueSoft
    value <= 0.7f -> RehabianexColors.Yellow
    else -> RehabianexColors.Red
}

@Composable
fun ChatBubble(
    text: String,
    fromUser: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .background(
                    if (fromUser) RehabianexColors.GreenMain else Color.White,
                    RoundedCornerShape(16.dp),
                )
                .padding(12.dp),
            color = RehabianexColors.Black,
        )
    }
}
