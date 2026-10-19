package com.example.rehabianex.ui.screens.privacy

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rehabianex.ui.components.RehabianexButton
import com.example.rehabianex.ui.components.RehabianexCard
import com.example.rehabianex.ui.components.RehabianexOutlineButton
import com.example.rehabianex.ui.components.ScreenScaffold
import com.example.rehabianex.ui.components.WarningCard
import com.example.rehabianex.ui.theme.RehabianexColors

@Composable
fun PrivacyNoticeScreen(
    uiState: PrivacyNoticeUiState,
    confirmationRequired: Boolean,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    var checked by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold {
        Text(
            text = "Aviso de privacidad",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        when (uiState) {
            PrivacyNoticeUiState.Loading -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("Cargando el aviso vigente…")
            }
            PrivacyNoticeUiState.AccessGranted -> Unit
            is PrivacyNoticeUiState.Error -> {
                WarningCard { Text(uiState.message) }
                Spacer(Modifier.height(12.dp))
                RehabianexButton("Reintentar", onClick = onRetry)
                if (confirmationRequired) {
                    Spacer(Modifier.height(8.dp))
                    RehabianexOutlineButton("Cerrar sesión", onClick = onLogout)
                } else {
                    Spacer(Modifier.height(8.dp))
                    RehabianexOutlineButton("Volver", onClick = onBack)
                }
            }
            is PrivacyNoticeUiState.Content -> {
                Text(
                    "Versión ${uiState.notice.version ?: "no disponible"} · " +
                        "Publicación ${uiState.notice.publishedOn ?: "no disponible"}",
                    color = RehabianexColors.GrayText,
                )
                uiState.warning?.let {
                    Spacer(Modifier.height(10.dp))
                    WarningCard { Text(it) }
                }
                Spacer(Modifier.height(12.dp))
                RehabianexCard {
                    PrivacyNoticeContent(uiState.notice.content.orEmpty())
                }
                Spacer(Modifier.height(12.dp))
                if (confirmationRequired) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { role = Role.Checkbox },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { checked = it },
                            enabled = !uiState.submitting,
                            modifier = Modifier.semantics {
                                contentDescription = "Confirmar lectura del Aviso de Privacidad"
                            },
                        )
                        Text(
                            "He leído el Aviso de Privacidad",
                            modifier = Modifier.padding(start = 4.dp),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    uiState.error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(10.dp))
                    RehabianexButton(
                        text = if (uiState.submitting) "Guardando…" else "Continuar",
                        enabled = checked && !uiState.submitting,
                        onClick = onConfirm,
                    )
                    Spacer(Modifier.height(8.dp))
                    RehabianexOutlineButton(
                        text = "Cerrar sesión",
                        enabled = !uiState.submitting,
                        onClick = onLogout,
                    )
                } else {
                    RehabianexOutlineButton("Volver", onClick = onBack)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PrivacyNoticeContent(content: String) {
    Column {
        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() -> Spacer(Modifier.height(10.dp))
                line.startsWith("## ") -> Text(
                    text = line.removePrefix("## "),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                line.startsWith("# ") -> Text(
                    text = line.removePrefix("# "),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                else -> Text(text = line, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
