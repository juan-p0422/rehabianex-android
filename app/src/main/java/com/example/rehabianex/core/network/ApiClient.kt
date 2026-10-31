package com.example.rehabianex.core.network

import com.example.rehabianex.BuildConfig
import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.data.remote.ApiService
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    fun createApiService(
        sessionManager: SessionManager,
        baseUrl: String = BuildConfig.API_BASE_URL,
        isDebug: Boolean = BuildConfig.DEBUG,
    ): ApiService {
        return Retrofit.Builder()
            .baseUrl(validatedApiBaseUrl(baseUrl, isDebug))
            .client(createOkHttpClient(sessionManager, isDebug))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    fun createOkHttpClient(
        sessionManager: SessionManager,
        isDebug: Boolean = BuildConfig.DEBUG,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(sessionManager))
            .addInterceptor(PrivacyNoticeRequiredInterceptor())
            .apply {
                if (isDebug) {
                    addInterceptor(
                        HttpLoggingInterceptor(SanitizedHttpLogger()).apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                        },
                    )
                }
            }
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private const val CONNECT_TIMEOUT_SECONDS = 20L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val WRITE_TIMEOUT_SECONDS = 30L
}

internal fun validatedApiBaseUrl(baseUrl: String, isDebug: Boolean): String {
    val parsed = baseUrl.trim().toHttpUrlOrNull()
        ?: throw IllegalArgumentException("API_BASE_URL no es una URL válida.")
    require(parsed.username.isEmpty() && parsed.password.isEmpty()) {
        "API_BASE_URL no debe incluir credenciales."
    }
    require(parsed.query == null && parsed.fragment == null) {
        "API_BASE_URL no debe incluir parámetros ni fragmentos."
    }
    val debugLoopbackHttp = isDebug && parsed.scheme == "http" &&
        parsed.host in DEBUG_LOOPBACK_HOSTS
    require(parsed.isHttps || debugLoopbackHttp) {
        "API_BASE_URL debe usar HTTPS; solo debug admite HTTP en loopback."
    }
    return parsed.newBuilder()
        .encodedPath(parsed.encodedPath.ensureTrailingSlash())
        .build()
        .toString()
}

private fun String.ensureTrailingSlash(): String = if (endsWith('/')) this else "$this/"

private val DEBUG_LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2", "::1")
