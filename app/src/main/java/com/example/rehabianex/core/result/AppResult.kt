package com.example.rehabianex.core.result

sealed class AppResult<out T> {
    data class Success<T>(
        val data: T,
    ) : AppResult<T>()

    data class Error(
        val message: String,
        val code: Int? = null,
        val throwable: Throwable? = null,
    ) : AppResult<Nothing>()
}
