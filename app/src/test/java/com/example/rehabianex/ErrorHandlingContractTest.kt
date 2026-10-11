package com.example.rehabianex

import com.example.rehabianex.core.error.ErrorMapper
import com.example.rehabianex.data.remote.ApiErrorParser
import java.io.IOException
import java.net.SocketTimeoutException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorHandlingContractTest {
    @Test
    fun mapsEveryOfficialHttpCodeToFriendlyMessage() {
        val expectations = mapOf(
            400 to "datos inválidos",
            401 to "sesión expiró",
            403 to "permiso",
            404 to "no se encontró",
            409 to "conflicto",
            422 to "datos marcados",
            429 to "demasiadas solicitudes",
            500 to "problema temporal",
            502 to "servicio externo",
        )

        expectations.forEach { (code, expectedText) ->
            val info = ErrorMapper.fromHttpCode(code)
            assertEquals(code, info.code)
            assertTrue(info.message.contains(expectedText, ignoreCase = true))
        }
    }

    @Test
    fun mapsTimeoutAndOfflineWithoutExposingCauseDetails() {
        val timeout = ErrorMapper.fromThrowable(
            IllegalStateException("wrapper", SocketTimeoutException("host internal.invalid")),
        )
        val offline = ErrorMapper.fromThrowable(
            IllegalStateException("wrapper", IOException("failed to connect internal.invalid")),
        )

        assertEquals("El servidor tardó demasiado en responder.", timeout.message)
        assertEquals("Sin conexión a internet.", offline.message)
        assertFalse(timeout.message.contains("internal.invalid"))
        assertFalse(offline.message.contains("internal.invalid"))
    }

    @Test
    fun neverShowsTechnicalOrServerDetailsInUiMessage() {
        val technical = "NullPointerException at com.backend.UserService:42"

        assertNull(ErrorMapper.safeBackendMessage(422, technical))
        assertEquals(
            "Revisa los datos marcados e inténtalo nuevamente.",
            ErrorMapper.fromHttpCode(422, technical).message,
        )
        assertFalse(
            ErrorMapper.fromHttpCode(500, technical).message.contains("NullPointer"),
        )
        assertEquals(
            "El servicio presenta un problema temporal. Inténtalo más tarde.",
            ErrorMapper.fromHttpCode(500, "Internal server error: FirebaseException").message,
        )
    }

    @Test
    fun parsesValidationEnvelopeAndMapsBackendFieldNamesForForms() {
        val parsed = ApiErrorParser.parse(
            """
            {
              "ok": false,
              "message": "Revisa los campos indicados.",
              "errors": {
                "full_name": ["Ingresa tu nombre completo."],
                "password_confirmation": ["Las contraseñas no coinciden."],
                "supervisor_type": ["Selecciona un tipo válido."],
                "age": ["La edad debe estar entre 13 y 120."]
              }
            }
            """.trimIndent().toResponseBody("application/json".toMediaType()),
        )

        assertEquals("Revisa los campos indicados.", parsed.message)
        assertEquals("Ingresa tu nombre completo.", parsed.fieldErrors["fullName"])
        assertEquals(
            "Las contraseñas no coinciden.",
            parsed.fieldErrors["passwordConfirmation"],
        )
        assertEquals("Selecciona un tipo válido.", parsed.fieldErrors["supervisorType"])
        assertEquals("La edad debe estar entre 13 y 120.", parsed.fieldErrors["age"])
    }

    @Test
    fun malformedOrTechnicalValidationBodyFallsBackSafely() {
        val malformed = ApiErrorParser.parse(
            "<html>stack trace</html>".toResponseBody("text/html".toMediaType()),
        )
        val technical = ApiErrorParser.parse(
            """
            {
              "message": "SQLException at com.backend.Repository",
              "errors": {
                "email": ["FirebaseException: provider failed"]
              }
            }
            """.trimIndent().toResponseBody("application/json".toMediaType()),
        )

        assertNull(malformed.message)
        assertTrue(malformed.fieldErrors.isEmpty())
        assertNull(technical.message)
        assertEquals("Revisa este dato.", technical.fieldErrors["email"])
    }

    @Test
    fun parserUsesHttpCodeAndNeverTrustsServerOrAuthTechnicalMessages() {
        val server = ApiErrorParser.parse(
            """
            {
              "ok": false,
              "message": "Database unavailable in production",
              "errors": {"email": ["should not be rendered"]}
            }
            """.trimIndent().toResponseBody("application/json".toMediaType()),
            statusCode = 500,
        )
        val auth = ApiErrorParser.parse(
            """{"ok":false,"message":"Token revoked by Firebase","errors":{}}"""
                .toResponseBody("application/json".toMediaType()),
            statusCode = 401,
        )

        assertNull(server.message)
        assertTrue(server.fieldErrors.isEmpty())
        assertNull(auth.message)
        assertEquals(
            "El servicio presenta un problema temporal. Inténtalo más tarde.",
            ErrorMapper.fromHttpCode(500, server.message).message,
        )
        assertEquals(
            "Tu sesión expiró. Inicia sesión nuevamente.",
            ErrorMapper.fromHttpCode(401, auth.message).message,
        )
    }

    @Test
    fun parserAcceptsFinalValidationShapesAndMapsSnakeCaseGenerically() {
        val parsed = ApiErrorParser.parse(
            """
            {
              "ok": false,
              "message": "Revisa los campos indicados.",
              "errors": {
                "starts_at": ["Indica una fecha y hora válidas."],
                "consent_text": "Confirma el texto de consentimiento.",
                "scope": {"message":"Selecciona al menos un permiso."}
              }
            }
            """.trimIndent().toResponseBody("application/json".toMediaType()),
            statusCode = 422,
        )

        assertEquals("Indica una fecha y hora válidas.", parsed.fieldErrors["startsAt"])
        assertEquals("Confirma el texto de consentimiento.", parsed.fieldErrors["consentText"])
        assertEquals("Selecciona al menos un permiso.", parsed.fieldErrors["scope"])
    }
}
