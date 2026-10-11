package com.example.rehabianex.notifications

import android.content.Context
import androidx.core.content.edit
import com.example.rehabianex.BuildConfig
import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.data.model.FcmTokenRegistrationRequest
import com.example.rehabianex.data.model.FcmTokenRevocationRequest
import com.example.rehabianex.data.remote.ApiService
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import java.security.MessageDigest
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FcmTokenManager(
    context: Context,
    private val apiService: ApiService,
    private val sessionManager: SessionManager,
) {
    private val applicationContext = context.applicationContext
    private val lifecycleStore = FcmTokenLifecycleStore(applicationContext)

    /**
     * Reanuda el registro únicamente después de que AuthRepository haya guardado
     * una sesión nueva y válida. La red se procesa fuera del flujo de login.
     */
    fun registerAfterAuthentication(): Boolean {
        val owner = currentSessionOwner() ?: return false
        lifecycleStore.resumeForAuthenticatedOwner(owner)
        scheduleRegistration(owner, includeImmediateAttempt = true)
        return true
    }

    /** Revalida el token al abrir la app, sin levantar una suspensión de logout. */
    fun ensureRegisteredForActiveSession(): Boolean {
        val owner = currentSessionOwner() ?: return false
        if (!registrationIsAllowed(owner)) return false
        scheduleRegistration(owner, includeImmediateAttempt = true)
        return true
    }

    /** Invocado por onNewToken. Nunca reanuda por sí mismo un registro suspendido. */
    suspend fun registerToken(token: String): Boolean {
        val owner = currentSessionOwner() ?: return false
        if (!registrationIsAllowed(owner)) return false
        val registered = registerTokenOnce(token, owner)
        if (!registered && registrationIsAllowed(owner)) {
            scheduleRegistration(owner, includeImmediateAttempt = false)
        }
        return registered
    }

    suspend fun revokeCurrentToken(): Boolean {
        lifecycleStore.beginLogout()
        cancelScheduledRegistration()
        return try {
            tokenMutationMutex.withLock {
                val messaging = FirebaseMessaging.getInstance()
                val token = sessionManager.getFcmToken()?.validFcmTokenOrNull()
                val remoteRevoked = token?.let { safeToken ->
                    try {
                        apiService.revokeFcmToken(
                            FcmTokenRevocationRequest(safeToken),
                        ).isSuccessful
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        false
                    }
                } ?: false

                // Impide que la instalación reutilice el token del usuario anterior.
                try {
                    messaging.deleteToken().awaitCompletion()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Logout y limpieza local deben continuar aunque Firebase falle.
                }
                remoteRevoked
            }
        } finally {
            sessionManager.clearFcmToken()
            lifecycleStore.finishLogoutSuspended()
        }
    }

    private fun scheduleRegistration(
        owner: FcmSessionOwner,
        includeImmediateAttempt: Boolean,
    ) {
        val delays = if (includeImmediateAttempt) {
            listOf(0L) + FCM_REGISTRATION_RETRY_DELAYS_MILLIS
        } else {
            FCM_REGISTRATION_RETRY_DELAYS_MILLIS
        }
        synchronized(RETRY_JOB_LOCK) {
            retryJob?.cancel()
            retryJob = applicationScope.launch {
                for (delayMillis in delays) {
                    if (delayMillis > 0L) delay(delayMillis)
                    if (!registrationIsAllowed(owner)) return@launch
                    val token = tokenOrNull(FirebaseMessaging.getInstance()) ?: continue
                    if (registerTokenOnce(token, owner)) return@launch
                }
            }
        }
    }

    private suspend fun registerTokenOnce(
        token: String,
        expectedOwner: FcmSessionOwner,
    ): Boolean {
        val safeToken = token.validFcmTokenOrNull() ?: return false
        if (!registrationIsAllowed(expectedOwner)) return false

        return tokenMutationMutex.withLock {
            if (!registrationIsAllowed(expectedOwner)) return@withLock false
            val request = FcmTokenRegistrationRequest(
                fcmToken = safeToken,
                appVersion = BuildConfig.VERSION_NAME,
                timezone = ZoneId.systemDefault().id,
                locale = Locale.getDefault().toLanguageTag().ifBlank { DEFAULT_LOCALE },
            )
            val registered = try {
                apiService.registerFcmToken(request).isSuccessful
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            }
            if (!registered) return@withLock false

            // Logout puede comenzar mientras el POST está en vuelo. En ese caso se
            // revoca inmediatamente y nunca se persiste el token como registrado.
            if (!registrationIsAllowed(expectedOwner)) {
                try {
                    apiService.revokeFcmToken(
                        FcmTokenRevocationRequest(safeToken),
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // El flujo de logout hará un segundo intento con el token guardado.
                }
                return@withLock false
            }

            sessionManager.saveFcmToken(safeToken)
            lifecycleStore.markRegistered(expectedOwner)
            true
        }
    }

    private fun registrationIsAllowed(expectedOwner: FcmSessionOwner): Boolean {
        val currentOwner = currentSessionOwner() ?: return false
        if (currentOwner != expectedOwner) return false
        return shouldRegisterFcmToken(
            state = lifecycleStore.readState(),
            hasActiveSession = sessionManager.hasActiveSession(),
            currentOwner = currentOwner,
        )
    }

    private fun currentSessionOwner(): FcmSessionOwner? {
        if (!sessionManager.hasActiveSession()) return null
        return fcmSessionOwnerOrNull(sessionManager.getUid(), sessionManager.getRole())
    }

    private fun cancelScheduledRegistration() {
        synchronized(RETRY_JOB_LOCK) {
            retryJob?.cancel()
            retryJob = null
        }
    }

    private fun firebaseIsConfigured(): Boolean =
        FirebaseApp.getApps(applicationContext).isNotEmpty()

    private fun String.validFcmTokenOrNull(): String? = trim()
        .takeIf { it.isNotEmpty() && it.length <= MAX_FCM_TOKEN_LENGTH && it.none(Char::isWhitespace) }

    private suspend fun tokenOrNull(messaging: FirebaseMessaging): String? {
        if (!firebaseIsConfigured()) return null
        return try {
            messaging.token.awaitValue()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun com.google.android.gms.tasks.Task<String>.awaitValue(): String =
        suspendCancellableCoroutine { continuation ->
            addOnCompleteListener { task ->
                if (!continuation.isActive) return@addOnCompleteListener
                if (task.isSuccessful && task.result != null) {
                    continuation.resume(task.result)
                } else {
                    continuation.resumeWithException(
                        task.exception ?: IllegalStateException("FCM token unavailable"),
                    )
                }
            }
        }

    private suspend fun com.google.android.gms.tasks.Task<Void>.awaitCompletion() {
        suspendCancellableCoroutine { continuation ->
            addOnCompleteListener { task ->
                if (!continuation.isActive) return@addOnCompleteListener
                if (task.isSuccessful) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(
                        task.exception ?: IllegalStateException("FCM token deletion failed"),
                    )
                }
            }
        }
    }

    private companion object {
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val tokenMutationMutex = Mutex()
        val RETRY_JOB_LOCK = Any()
        var retryJob: Job? = null

        const val MAX_FCM_TOKEN_LENGTH = 16_384
        const val DEFAULT_LOCALE = "es-MX"
    }
}

internal data class FcmSessionOwner(
    val fingerprint: String,
    val role: String,
)

internal data class FcmTokenLifecycleState(
    val logoutInProgress: Boolean,
    val registrationSuspended: Boolean,
    val activeOwnerFingerprint: String?,
    val lastRegisteredOwnerFingerprint: String?,
)

internal fun fcmSessionOwnerOrNull(uid: String?, role: String?): FcmSessionOwner? {
    val safeUid = uid?.trim()?.takeIf {
        it.isNotEmpty() && it.length <= MAX_OWNER_UID_LENGTH && it.none(Char::isWhitespace)
    } ?: return null
    val safeRole = role?.trim()?.takeIf { it in VALID_OWNER_ROLES } ?: return null
    val fingerprint = MessageDigest.getInstance("SHA-256")
        .digest("$safeUid|$safeRole".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    return FcmSessionOwner(fingerprint = fingerprint, role = safeRole)
}

internal fun shouldRegisterFcmToken(
    state: FcmTokenLifecycleState,
    hasActiveSession: Boolean,
    currentOwner: FcmSessionOwner?,
): Boolean {
    if (!hasActiveSession || currentOwner == null) return false
    if (state.logoutInProgress || state.registrationSuspended) return false
    return state.activeOwnerFingerprint == null ||
        state.activeOwnerFingerprint == currentOwner.fingerprint
}

private class FcmTokenLifecycleStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun readState(): FcmTokenLifecycleState = synchronized(STATE_LOCK) {
        FcmTokenLifecycleState(
            logoutInProgress = preferences.getBoolean(KEY_LOGOUT_IN_PROGRESS, false),
            registrationSuspended = preferences.getBoolean(KEY_REGISTRATION_SUSPENDED, false),
            activeOwnerFingerprint = preferences.getString(KEY_ACTIVE_OWNER, null),
            lastRegisteredOwnerFingerprint = preferences.getString(KEY_REGISTERED_OWNER, null),
        )
    }

    fun beginLogout() = synchronized(STATE_LOCK) {
        preferences.edit(commit = true) {
            putBoolean(KEY_LOGOUT_IN_PROGRESS, true)
            putBoolean(KEY_REGISTRATION_SUSPENDED, true)
            remove(KEY_ACTIVE_OWNER)
        }
    }

    fun finishLogoutSuspended() = synchronized(STATE_LOCK) {
        preferences.edit(commit = true) {
            putBoolean(KEY_LOGOUT_IN_PROGRESS, false)
            putBoolean(KEY_REGISTRATION_SUSPENDED, true)
            remove(KEY_ACTIVE_OWNER)
            remove(KEY_REGISTERED_OWNER)
        }
    }

    fun resumeForAuthenticatedOwner(owner: FcmSessionOwner) = synchronized(STATE_LOCK) {
        preferences.edit(commit = true) {
            putBoolean(KEY_LOGOUT_IN_PROGRESS, false)
            putBoolean(KEY_REGISTRATION_SUSPENDED, false)
            putString(KEY_ACTIVE_OWNER, owner.fingerprint)
        }
    }

    fun markRegistered(owner: FcmSessionOwner) {
        synchronized(STATE_LOCK) {
            if (preferences.getString(KEY_ACTIVE_OWNER, null) != owner.fingerprint) return
            preferences.edit(commit = true) {
                putString(KEY_REGISTERED_OWNER, owner.fingerprint)
            }
        }
    }

    private companion object {
        val STATE_LOCK = Any()
        const val PREFERENCES_NAME = "rehabianex_fcm_lifecycle"
        const val KEY_LOGOUT_IN_PROGRESS = "logout_in_progress"
        const val KEY_REGISTRATION_SUSPENDED = "registration_suspended"
        const val KEY_ACTIVE_OWNER = "active_owner_fingerprint"
        const val KEY_REGISTERED_OWNER = "registered_owner_fingerprint"
    }
}

private val VALID_OWNER_ROLES = setOf("patient", "supervisor", "admin")
internal val FCM_REGISTRATION_RETRY_DELAYS_MILLIS = listOf(1_000L, 5_000L, 15_000L, 60_000L)
private const val MAX_OWNER_UID_LENGTH = 128
