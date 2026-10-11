package com.example.rehabianex.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

const val NOTIFICATION_PERMISSION_DISABLED_MESSAGE =
    "Las notificaciones están desactivadas. Puedes habilitarlas desde Ajustes para recibir recordatorios."

internal fun shouldShowNotificationPermissionBanner(
    sdkInt: Int,
    permissionGranted: Boolean,
): Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU && !permissionGranted

@Composable
fun NotificationPermissionBanner() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun permissionGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    var showBanner by remember(context) {
        mutableStateOf(
            shouldShowNotificationPermissionBanner(
                sdkInt = Build.VERSION.SDK_INT,
                permissionGranted = permissionGranted(),
            ),
        )
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                showBanner = shouldShowNotificationPermissionBanner(
                    sdkInt = Build.VERSION.SDK_INT,
                    permissionGranted = permissionGranted(),
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showBanner) {
        WarningCard {
            Text("Permiso requerido", fontWeight = FontWeight.Bold)
            Text(NOTIFICATION_PERMISSION_DISABLED_MESSAGE)
            Spacer(Modifier.height(8.dp))
            RehabianexOutlineButton(
                text = "Abrir ajustes",
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        },
                    )
                },
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}
