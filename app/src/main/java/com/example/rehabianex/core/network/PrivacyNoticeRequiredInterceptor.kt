package com.example.rehabianex.core.network

import com.example.rehabianex.core.session.PrivacyNoticeRequiredBus
import com.google.gson.JsonParser
import okhttp3.Interceptor
import okhttp3.Response

class PrivacyNoticeRequiredInterceptor(
    private val onPrivacyNoticeRequired: () -> Unit = PrivacyNoticeRequiredBus::notifyRequired,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (isPrivacyNoticeReadRequired(response.code, response.peekBody(MAX_ERROR_BODY_BYTES).string())) {
            onPrivacyNoticeRequired()
        }
        return response
    }

    internal companion object {
        fun isPrivacyNoticeReadRequired(statusCode: Int, responseBody: String): Boolean {
            if (statusCode != HTTP_PRECONDITION_REQUIRED) return false
            return runCatching {
                JsonParser.parseString(responseBody)
                    .takeIf { it.isJsonObject }
                    ?.asJsonObject
                    ?.get("code")
                    ?.takeIf { it.isJsonPrimitive }
                    ?.asString == PRIVACY_NOTICE_REQUIRED_CODE
            }.getOrDefault(false)
        }

        private const val HTTP_PRECONDITION_REQUIRED = 428
        private const val PRIVACY_NOTICE_REQUIRED_CODE = "privacy_notice_read_required"
        private const val MAX_ERROR_BODY_BYTES = 8_192L
    }
}
