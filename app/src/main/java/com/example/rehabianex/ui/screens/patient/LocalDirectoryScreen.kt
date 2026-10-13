package com.example.rehabianex.ui.screens.patient

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rehabianex.data.model.LocalResource
import com.example.rehabianex.navigation.Routes
import com.example.rehabianex.ui.components.ChipSelector
import com.example.rehabianex.ui.components.DangerCard
import com.example.rehabianex.ui.components.InputField
import com.example.rehabianex.ui.components.OfflineStatusCard
import com.example.rehabianex.ui.components.PatientScreenScaffold
import com.example.rehabianex.ui.components.RehabianexCard
import com.example.rehabianex.ui.components.RehabianexOutlineButton
import com.example.rehabianex.ui.components.RehabianexSecondaryButton
import com.example.rehabianex.ui.components.ScreenHeader
import com.example.rehabianex.ui.components.WarningCard
import com.example.rehabianex.ui.theme.RehabianexColors
import java.text.DateFormat
import java.util.Date

@Composable
fun LocalDirectoryScreen(
    onNavigate: (String) -> Unit,
    uiState: LocalResourcesUiState,
    onRetry: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Todos") }

    PatientScreenScaffold(Routes.LocalDirectory, onNavigate) {
        ScreenHeader("Directorio local", "Recursos informativos de apoyo")
        Spacer(Modifier.height(12.dp))
        InputField(query, { query = it.take(100) }, "Buscar recurso")
        Spacer(Modifier.height(8.dp))
        ChipSelector(
            listOf("Todos", "AA/NA", "Clínicas", "Anexos", "Líneas"),
            filter,
            { filter = it },
        )
        Spacer(Modifier.height(12.dp))

        when (uiState) {
            LocalResourcesUiState.Loading -> DirectoryLoading()
            is LocalResourcesUiState.Error -> DirectoryError(uiState.message, onRetry)
            is LocalResourcesUiState.Content -> {
                OfflineStatusCard(uiState.offlineStatus)
                uiState.offlineStatus.lastSyncedAt?.let { syncedAt ->
                    Text(
                        "Última sincronización del directorio: ${DateFormat.getDateTimeInstance().format(Date(syncedAt))}",
                        color = RehabianexColors.GrayText,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                DirectoryContent(
                    resources = uiState.resources.filteredBy(query, filter),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        WarningCard {
            Text("Revisión informativa", fontWeight = FontWeight.Bold)
            Text(
                "La fuente y la fecha indican revisión de datos informativos; " +
                    "no constituyen acreditación ni evaluación de calidad clínica.",
            )
        }
    }
}

@Composable
private fun DirectoryContent(resources: List<LocalResource>) {
    if (resources.isEmpty()) {
        RehabianexCard {
            Text("No hay recursos para mostrar.")
            Spacer(Modifier.height(4.dp))
            Text(
                "Prueba con otro filtro o vuelve a intentar cuando la base tenga recursos registrados.",
                color = RehabianexColors.GrayText,
            )
        }
        return
    }

    resources.forEachIndexed { index, resource ->
        ResourceCard(resource)
        if (index != resources.lastIndex) {
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun DirectoryLoading() {
    RehabianexCard {
        CircularProgressIndicator(color = RehabianexColors.GreenMain)
        Spacer(Modifier.height(10.dp))
        Text("Cargando recursos locales...")
    }
}

@Composable
private fun DirectoryError(
    message: String,
    onRetry: () -> Unit,
) {
    DangerCard {
        Text(message)
    }
    Spacer(Modifier.height(10.dp))
    RehabianexOutlineButton("Reintentar", onClick = onRetry)
}

@Composable
private fun ResourceCard(resource: LocalResource) {
    val context = LocalContext.current

    RehabianexCard {
        Text(resource.name, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(resource.typeLabel, color = RehabianexColors.GreenDark)
        Spacer(Modifier.height(4.dp))
        Text("Detalles: ${resource.detail.ifBlank { "No disponible" }}", color = RehabianexColors.GrayText)
        Text("Ciudad: ${resource.city.ifBlank { "No disponible" }}", color = RehabianexColors.GrayText)
        Text("Fuente: ${resource.sourceName.ifBlank { "No disponible" }}", color = RehabianexColors.GrayText)
        Text("Enlace de fuente: ${resource.sourceUrl.ifBlank { "No disponible" }}", color = RehabianexColors.GrayText)
        Text("Fecha de revisión: ${resource.reviewedAt.ifBlank { "No disponible" }}", color = RehabianexColors.GrayText)
        Text("Última actualización técnica: ${resource.updatedAt.ifBlank { "No disponible" }}", color = RehabianexColors.GrayText)
        Text(
            "Teléfono: ${resource.phone.ifBlank { "No disponible" }}",
            color = RehabianexColors.GrayText,
        )
        if (resource.phone.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            RehabianexSecondaryButton("Contactar recurso") {
                dialPhone(
                    context = context,
                    phone = resource.phone,
                    message = "Se abrirá el teléfono para marcar a ${resource.name}.",
                )
            }
        }
    }
}

private fun List<LocalResource>.filteredBy(
    query: String,
    filter: String,
): List<LocalResource> {
    val cleanQuery = query.trim().lowercase()

    return filter { resource ->
        val matchesFilter = filter == "Todos" || resource.typeLabel == filter
        val matchesQuery = cleanQuery.isBlank() ||
            resource.name.lowercase().contains(cleanQuery) ||
            resource.city.lowercase().contains(cleanQuery) ||
            resource.detail.lowercase().contains(cleanQuery)

        matchesFilter && matchesQuery
    }
}

internal fun dialPhone(
    context: Context,
    phone: String,
    message: String,
) {
    val digits = phone.filter { it.isDigit() || it == '+' }
    if (digits.isBlank()) {
        Toast.makeText(context, "Este contacto no tiene un teléfono válido.", Toast.LENGTH_SHORT).show()
        return
    }

    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    val intent = Intent(Intent.ACTION_DIAL).apply {
        data = Uri.parse("tel:${Uri.encode(digits)}")
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, "No se encontró una app para marcar teléfono.", Toast.LENGTH_SHORT).show()
        }
}
