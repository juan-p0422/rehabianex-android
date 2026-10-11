package com.example.rehabianex.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rehabianex.data.model.OfflineStatus

@Composable
fun OfflineStatusCard(status: OfflineStatus) {
    when {
        status.isOffline -> WarningCard {
            Text("Modo sin conexión", fontWeight = FontWeight.Bold)
            Text("Se muestran datos guardados en este dispositivo.")
        }
        status.isRefreshing -> SoftCard {
            Text("Mostrando datos guardados", fontWeight = FontWeight.Bold)
            Text("Actualizando información desde el servidor…")
        }
    }
    if (status.isOffline || status.isRefreshing) {
        Spacer(Modifier.height(8.dp))
    }
    if (status.pendingSyncCount > 0) {
        WarningCard {
            Text("Cambios pendientes", fontWeight = FontWeight.Bold)
            Text(
                if (status.pendingSyncCount == 1) {
                    "Hay 1 registro pendiente de sincronizar."
                } else {
                    "Hay ${status.pendingSyncCount} registros pendientes de sincronizar."
                },
            )
        }
        Spacer(Modifier.height(8.dp))
    }
    status.warning?.takeIf { it.isNotBlank() }?.let { warning ->
        WarningCard {
            Text("Revisión necesaria", fontWeight = FontWeight.Bold)
            Text(warning)
        }
        Spacer(Modifier.height(8.dp))
    }
}
