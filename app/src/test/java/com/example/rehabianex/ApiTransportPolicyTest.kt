package com.example.rehabianex

import com.example.rehabianex.core.network.validatedApiBaseUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiTransportPolicyTest {
    @Test
    fun releaseAcceptsOnlyCleanHttpsBaseUrl() {
        assertEquals(
            "https://rehabianex-api.onrender.com/api/",
            validatedApiBaseUrl("https://rehabianex-api.onrender.com/api", isDebug = false),
        )
        assertThrows(IllegalArgumentException::class.java) {
            validatedApiBaseUrl("http://rehabianex-api.onrender.com/api/", isDebug = false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validatedApiBaseUrl("https://user:secret@example.com/api/", isDebug = false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validatedApiBaseUrl("https://example.com/api/?token=secret", isDebug = false)
        }
    }

    @Test
    fun debugAllowsOnlyExplicitLoopbackHttp() {
        assertEquals(
            "http://10.0.2.2:8000/api/",
            validatedApiBaseUrl("http://10.0.2.2:8000/api", isDebug = true),
        )
        assertThrows(IllegalArgumentException::class.java) {
            validatedApiBaseUrl("http://192.168.1.25:8000/api/", isDebug = true)
        }
    }
}
