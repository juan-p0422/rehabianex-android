package com.example.rehabianex.notifications

import com.example.rehabianex.AppContainer
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RehabianexFirebaseMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Never log the token. If there is no authenticated session, login will retry.
        serviceScope.launch {
            AppContainer(applicationContext).fcmTokenManager.registerToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        serviceScope.launch {
            handleMessage(message)
        }
    }

    private fun handleMessage(message: RemoteMessage) {
        val container = AppContainer(applicationContext)
        if (!container.sessionManager.hasActiveSession()) return

        val type = validatedFcmType(message.data[FcmContract.DATA_TYPE]) ?: return
        val entityId = message.data[FcmContract.DATA_ENTITY_ID].safeEntityIdOrNull()
        val createdAt = validatedFcmCreatedAt(message.data[FcmContract.DATA_CREATED_AT])
        val notificationId = message.data[FcmContract.DATA_NOTIFICATION_ID]
            ?: message.messageId
            ?: listOfNotNull(type, entityId, createdAt).joinToString("|")
        if (FcmNotificationDeduplicator.isDuplicate(
                context = applicationContext,
                type = type,
                notificationId = notificationId,
                entityId = entityId,
            )
        ) return

        val shown = FcmNotificationPresenter.show(
            context = applicationContext,
            role = container.sessionManager.getRole(),
            type = type,
            title = message.data[FcmContract.DATA_TITLE],
            body = message.data[FcmContract.DATA_BODY],
            requestedRoute = message.data[FcmContract.DATA_ROUTE],
            entityId = entityId,
            priority = message.data[FcmContract.DATA_PRIORITY],
            remoteNotificationId = notificationId,
            messageId = message.messageId,
        )
        if (shown) {
            FcmNotificationDeduplicator.recordRemote(
                context = applicationContext,
                type = type,
                notificationId = notificationId,
                entityId = entityId,
            )
            if (type == FcmContract.TYPE_APPOINTMENT_REMINDER && entityId != null) {
                AgendaNotificationHelper.cancelEventReminder(applicationContext, entityId)
            }
        }
    }
}
