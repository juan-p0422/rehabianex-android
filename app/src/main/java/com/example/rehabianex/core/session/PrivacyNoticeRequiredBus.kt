package com.example.rehabianex.core.session

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Informa a la navegación que el servidor exige confirmar el aviso vigente.
 * No borra la sesión ni la evidencia local; el gate reconcilia ambos estados.
 */
object PrivacyNoticeRequiredBus {
    private val mutableEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events = mutableEvents.asSharedFlow()

    fun notifyRequired() {
        mutableEvents.tryEmit(Unit)
    }
}
