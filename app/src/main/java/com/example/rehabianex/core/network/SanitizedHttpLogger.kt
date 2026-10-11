package com.example.rehabianex.core.network

import android.util.Log
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Keeps debug HTTP diagnostics without exposing identifiers embedded in URLs.
 * Request and response bodies/headers remain disabled by the BASIC interceptor level.
 */
internal class SanitizedHttpLogger(
    private val sink: (String) -> Unit = { message -> Log.i(LOG_TAG, message) },
) : HttpLoggingInterceptor.Logger {
    override fun log(message: String) {
        sink(SafeHttpLogSanitizer.sanitize(message))
    }

    private companion object {
        const val LOG_TAG = "OkHttp.Safe"
    }
}

internal object SafeHttpLogSanitizer {
    fun sanitize(message: String): String = URL_PATTERN.replace(message) { match ->
        sanitizeUrl(match.value)
    }

    private fun sanitizeUrl(url: String): String {
        val fragmentIndex = url.indexOf('#')
        val withoutFragment = if (fragmentIndex >= 0) url.substring(0, fragmentIndex) else url
        val fragment = if (fragmentIndex >= 0) "#$REDACTED" else ""
        val queryIndex = withoutFragment.indexOf('?')
        val rawBase = if (queryIndex >= 0) withoutFragment.substring(0, queryIndex) else withoutFragment
        val rawQuery = if (queryIndex >= 0) withoutFragment.substring(queryIndex + 1) else null

        val safeBase = DYNAMIC_PATH_PATTERNS.fold(rawBase) { current, pattern ->
            pattern.replace(current) { pathMatch ->
                pathMatch.groupValues[1] + REDACTED
            }
        }
        val safeQuery = rawQuery?.split('&')?.joinToString("&") { parameter ->
            val name = parameter.substringBefore('=')
            "$name=$REDACTED"
        }

        return buildString {
            append(safeBase)
            if (safeQuery != null) {
                append('?')
                append(safeQuery)
            }
            append(fragment)
        }
    }

    private const val REDACTED = "<redacted>"
    private val URL_PATTERN = Regex("https?://[^\\s)]+")
    private val DYNAMIC_PATH_PATTERNS = listOf(
        Regex("(/admin/supervisors/)[^/?#]+"),
        Regex("(/supervisors/)(?!resolve(?:[/?#]|$))[^/?#]+"),
        Regex("(/patients/)[^/?#]+"),
        Regex("(/support-contacts/)[^/?#]+"),
        Regex("(/agenda-events/)[^/?#]+"),
        Regex("(/consents/)[^/?#]+"),
        Regex("(/notification-settings/)[^/?#]+"),
        Regex("(/supervision-requests/)[^/?#]+"),
        Regex("(/interventions/)[^/?#]+"),
    )
}
