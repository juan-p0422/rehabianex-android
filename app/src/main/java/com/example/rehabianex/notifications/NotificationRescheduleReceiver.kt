package com.example.rehabianex.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Reprograma avisos tras reinicio, cambio de zona/hora o actualización de la app. */
class NotificationRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in SUPPORTED_ACTIONS) {
            NotificationRescheduleWorker.enqueue(context)
        }
    }

    private companion object {
        val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
