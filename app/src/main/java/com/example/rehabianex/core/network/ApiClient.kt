package com.example.rehabianex.core.network

import com.example.rehabianex.BuildConfig
import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.data.remote.ApiService
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
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
            .baseUrl(baseUrl.withTrailingSlash())
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

    private fun String.withTrailingSlash(): String {
        require(startsWith("https://") || startsWith("http://")) {
            "API_BASE_URL debe comenzar con http:// o https://"
        }
        return if (endsWith("/")) this else "$this/"
    }

    private const val CONNECT_TIMEOUT_SECONDS = 20L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val WRITE_TIMEOUT_SECONDS = 30L
}
