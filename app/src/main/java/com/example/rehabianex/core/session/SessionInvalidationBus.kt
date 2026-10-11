package com.example.rehabianex.core.session

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SessionInvalidationBus {
    private val mutableEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events = mutableEvents.asSharedFlow()

    fun notifySessionExpired(
        message: String = "Tu sesión expiró. Inicia sesión nuevamente.",
    ) {
        mutableEvents.tryEmit(message)
    }
}
