package com.example.rehabianex.ui.screens.supervisor

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.rehabianex.ui.theme.RehabianexColors
import java.text.DateFormat
import java.util.Date

@Composable
internal fun SafeCacheStatus(
    isRefreshing: Boolean,
    lastSyncedAt: Long?,
    refreshMessage: String?,
) {
    when {
        isRefreshing -> Text("Actualizando...", color = RehabianexColors.GrayText)
        refreshMessage != null && lastSyncedAt != null -> Text(
            "Última actualización: ${lastSyncedAt.asUpdateTime()}",
            color = RehabianexColors.GrayText,
        )
    }
}

private fun Long.asUpdateTime(): String = DateFormat.getDateTimeInstance(
    DateFormat.SHORT,
    DateFormat.SHORT,
).format(Date(this))
