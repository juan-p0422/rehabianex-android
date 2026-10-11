package com.example.rehabianex.core.network

import com.example.rehabianex.core.session.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val sessionManager: SessionManager,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()
            .header("Accept", "application/json")

        if (originalRequest.body != null) {
            requestBuilder.header("Content-Type", "application/json")
        }

        if (!originalRequest.isPublicAuthRequest()) {
            sessionManager.getIdToken()
                ?.takeIf { it.isNotBlank() }
                ?.let { token ->
                    requestBuilder.header("Authorization", "Bearer $token")
                }
        }

        return chain.proceed(requestBuilder.build())
    }

    private fun okhttp3.Request.isPublicAuthRequest(): Boolean {
        val path = url.encodedPath
        return path.endsWith("/auth/login") ||
            path.endsWith("/auth/register") ||
            path.endsWith("/auth/refresh") ||
            path.endsWith("/auth/firebase")
    }
}
