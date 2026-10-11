package com.example.rehabianex.core.performance

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

const val DEFAULT_SCREEN_REFRESH_TTL_MILLIS = 20_000L
const val PATIENT_REFRESH_SCOPE = "patient"
const val SUPERVISOR_REFRESH_SCOPE = "supervisor"
const val ADMIN_REFRESH_SCOPE = "admin"

object RefreshInvalidationRegistry {
    private val versions = ConcurrentHashMap<String, AtomicLong>()

    fun version(scope: String): Long = versions.getOrPut(scope) { AtomicLong(0L) }.get()

    fun invalidate(scope: String) {
        versions.getOrPut(scope) { AtomicLong(0L) }.incrementAndGet()
    }
}

/**
 * Coordina un único refresh por pantalla. Las solicitudes no forzadas se omiten
 * mientras exista trabajo activo o el último resultado exitoso siga dentro del TTL.
 */
class StaleRefreshController(
    private val ttlMillis: Long = DEFAULT_SCREEN_REFRESH_TTL_MILLIS,
    private val invalidationScope: String? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private var refreshJob: Job? = null

    @Volatile
    var lastSyncedAt: Long? = null
        private set
    private var lastSyncedVersion: Long? = null

    fun refreshIfStale(
        scope: CoroutineScope,
        force: Boolean = false,
        refresh: suspend () -> Boolean,
    ): Boolean = synchronized(lock) {
        val now = clock()
        val requestVersion = invalidationScope?.let(RefreshInvalidationRegistry::version)
        val versionIsCurrent = invalidationScope == null || lastSyncedVersion == requestVersion
        val isFresh = lastSyncedAt?.let { now - it < ttlMillis } == true && versionIsCurrent
        if (!force && (refreshJob?.isActive == true || isFresh)) {
            return@synchronized false
        }

        if (force) refreshJob?.cancel()
        refreshJob = scope.launch {
            if (refresh()) {
                lastSyncedVersion = requestVersion
                lastSyncedAt = clock()
            } else {
                lastSyncedAt = null
            }
        }
        true
    }

    fun invalidate() {
        lastSyncedAt = null
    }

    fun markFresh() {
        lastSyncedVersion = invalidationScope?.let(RefreshInvalidationRegistry::version)
        lastSyncedAt = clock()
    }
}
