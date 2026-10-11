package com.example.rehabianex.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rehabianex.ui.theme.RehabianexColors
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun TimeSelector12h(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorMessage: String? = null,
    minuteStep: Long = 1,
) {
    val time = value.toLocalTimeOrDefault()

    fun update(nextTime: LocalTime) {
        if (enabled) {
            onValueChange(nextTime.toStorageTime())
        }
    }

    RehabianexCard(
        modifier = modifier,
        contentPadding = PaddingValues(14.dp),
    ) {
        Text(label, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            text = formatTime12h(value),
            color = RehabianexColors.GreenDark,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 24.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            TimeStepper(
                label = "Hora",
                value = time.hour12().toString(),
                enabled = enabled,
                onDecrease = { update(time.plusHours(-1)) },
                onIncrease = { update(time.plusHours(1)) },
                modifier = Modifier.weight(1f),
            )
            TimeStepper(
                label = "Min",
                value = "%02d".format(time.minute),
                enabled = enabled,
                onDecrease = { update(time.plusMinutes(-minuteStep)) },
                onIncrease = { update(time.plusMinutes(minuteStep)) },
                modifier = Modifier.weight(1f),
            )
            PeriodSelector(
                selected = if (time.hour < 12) "AM" else "PM",
                enabled = enabled,
                onSelected = { update(time.withPeriod(it)) },
                modifier = Modifier.weight(1.15f),
            )
        }
        Text(
            text = "Ajusta hora y minutos de uno en uno.",
            color = RehabianexColors.GrayText,
            fontSize = 12.sp,
        )
        errorMessage?.let { message ->
            Spacer(Modifier.height(6.dp))
            Text(message, color = RehabianexColors.Red)
        }
    }
}

@Composable
private fun TimeStepper(
    label: String,
    value: String,
    enabled: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = RehabianexColors.GrayText, fontSize = 12.sp)
        StepButton("−", enabled, onDecrease)
        Text(
            value,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )
        StepButton("+", enabled, onIncrease)
    }
}

@Composable
private fun StepButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(width = 48.dp, height = 36.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(0.dp),
        border = BorderStroke(1.dp, RehabianexColors.GreenMain),
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PeriodSelector(
    selected: String,
    enabled: Boolean,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Periodo", color = RehabianexColors.GrayText, fontSize = 12.sp)
        listOf("AM", "PM").forEach { period ->
            FilterChip(
                selected = selected == period,
                enabled = enabled,
                onClick = { onSelected(period) },
                label = { Text(period) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = RehabianexColors.GreenMain,
                    selectedLabelColor = RehabianexColors.GrayCard,
                    containerColor = RehabianexColors.GrayCard,
                ),
            )
        }
    }
}

fun formatTime12h(value: String): String {
    val time = value.toLocalTimeOrNull() ?: return value
    val period = if (time.hour < 12) "AM" else "PM"
    return "${time.hour12()}:%02d $period".format(time.minute)
}

private fun String.toLocalTimeOrDefault(): LocalTime {
    return toLocalTimeOrNull() ?: LocalTime.of(9, 0)
}

private fun String.toLocalTimeOrNull(): LocalTime? {
    return runCatching {
        LocalTime.parse(this, DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrNull()
}

private fun LocalTime.toStorageTime(): String {
    return "%02d:%02d".format(hour, minute)
}

private fun LocalTime.hour12(): Int {
    val hourValue = hour % 12
    return if (hourValue == 0) 12 else hourValue
}

private fun LocalTime.withPeriod(period: String): LocalTime {
    val hour12 = hour12()
    val newHour = if (period == "AM") {
        if (hour12 == 12) 0 else hour12
    } else {
        if (hour12 == 12) 12 else hour12 + 12
    }
    return withHour(newHour)
}
