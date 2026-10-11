package com.example.rehabianex

import com.example.rehabianex.core.network.SafeHttpLogSanitizer
import com.example.rehabianex.core.network.SanitizedHttpLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeHttpLoggingTest {
    @Test
    fun `redacts query values while preserving diagnostic fields`() {
        val raw =
            "--> GET https://rehabianex-api.onrender.com/api/agenda-events" +
                "?patient_uid=technical-user-123&status=scheduled&limit=50"

        val safe = SafeHttpLogSanitizer.sanitize(raw)

        assertEquals(
            "--> GET https://rehabianex-api.onrender.com/api/agenda-events" +
                "?patient_uid=<redacted>&status=<redacted>&limit=<redacted>",
            safe,
        )
        assertFalse(safe.contains("technical-user-123"))
    }

    @Test
    fun `redacts dynamic path identifiers in request and response logs`() {
        val request =
            "--> GET https://rehabianex-api.onrender.com/api/supervisors/technical-supervisor-123/" +
                "patients/technical-patient-456"
        val response =
            "<-- 200 https://rehabianex-api.onrender.com/api/patients/technical-patient-456 " +
                "(321ms, unknown-length body)"

        val safeRequest = SafeHttpLogSanitizer.sanitize(request)
        val safeResponse = SafeHttpLogSanitizer.sanitize(response)

        assertTrue(safeRequest.contains("/supervisors/<redacted>/patients/<redacted>"))
        assertTrue(safeResponse.contains("<-- 200"))
        assertTrue(safeResponse.contains("(321ms, unknown-length body)"))
        assertFalse(safeRequest.contains("technical-supervisor-123"))
        assertFalse(safeRequest.contains("technical-patient-456"))
        assertFalse(safeResponse.contains("technical-patient-456"))
    }

    @Test
    fun `keeps static routes and network failures useful`() {
        val staticRoute =
            "<-- 200 https://rehabianex-api.onrender.com/api/supervisors/resolve" +
                "?code=RA-TEST123#private-fragment (85ms)"
        val failure =
            "<-- HTTP FAILED: java.net.SocketTimeoutException: timeout"

        val safeRoute = SafeHttpLogSanitizer.sanitize(staticRoute)

        assertTrue(safeRoute.contains("/supervisors/resolve?code=<redacted>#<redacted>"))
        assertFalse(safeRoute.contains("RA-TEST123"))
        assertFalse(safeRoute.contains("private-fragment"))
        assertEquals(failure, SafeHttpLogSanitizer.sanitize(failure))
    }

    @Test
    fun `logger never forwards the original identifier`() {
        val captured = mutableListOf<String>()
        val logger = SanitizedHttpLogger(captured::add)

        logger.log(
            "--> PATCH https://rehabianex-api.onrender.com/api/consents/opaque-consent-123",
        )

        assertEquals(1, captured.size)
        assertTrue(captured.single().endsWith("/consents/<redacted>"))
        assertFalse(captured.single().contains("opaque-consent-123"))
    }
}
