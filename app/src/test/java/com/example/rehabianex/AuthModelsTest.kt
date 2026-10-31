package com.example.rehabianex

import com.example.rehabianex.data.model.AuthResponse
import com.example.rehabianex.data.model.RegisterRequest
import com.example.rehabianex.data.remote.ApiErrorParser
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthModelsTest {
    private val gson = Gson()

    @Test
    fun authResponse_readsTopLevelSession() {
        val response = gson.fromJson(
            """
            {
              "tokens": {
                "id_token": "header.payload.signature",
                "refresh_token": "refresh-token"
              },
              "auth": {
                "uid": "patient-001",
                "role": "patient"
              }
            }
            """.trimIndent(),
            AuthResponse::class.java,
        )

        assertEquals("header.payload.signature", response.resolvedTokens()?.idToken)
        assertEquals("patient-001", response.resolvedAuth()?.uid)
        assertEquals("patient", response.resolvedAuth()?.role)
    }

    @Test
    fun authResponse_prefersTokensGuaranteedInsideAuth() {
        val response = gson.fromJson(
            """
            {
              "auth": {
                "uid": "patient-001",
                "role": "patient",
                "id_token": "auth.id.token",
                "refresh_token": "auth-refresh"
              },
              "tokens": {
                "id_token": "legacy.id.token",
                "refresh_token": "legacy-refresh"
              }
            }
            """.trimIndent(),
            AuthResponse::class.java,
        )

        assertEquals("auth.id.token", response.resolvedTokens()?.idToken)
        assertEquals("auth-refresh", response.resolvedTokens()?.refreshToken)
    }

    @Test
    fun authResponse_readsDataWrappedSession() {
        val response = gson.fromJson(
            """
            {
              "data": {
                "tokens": {
                  "id_token": "header.payload.signature",
                  "refresh_token": "refresh-token"
                },
                "auth": {
                  "uid": "supervisor-001",
                  "role": "supervisor"
                }
              }
            }
            """.trimIndent(),
            AuthResponse::class.java,
        )

        assertEquals("supervisor-001", response.resolvedAuth()?.uid)
        assertEquals("supervisor", response.resolvedAuth()?.role)
    }

    @Test
    fun authResponse_mapsDisplayNameAndPreferredProfile() {
        val response = gson.fromJson(
            """
            {
              "auth": {
                "uid": "patient-001",
                "role": "patient",
                "display_name": "Nombre desde auth",
                "safe_display_name": "Paciente anónimo",
                "full_name": "Nombre legado"
              },
              "profile": {
                "uid": "patient-001",
                "role": "patient",
                "full_name": "Nombre desde perfil",
                "display_name": "Paciente anónimo",
                "safe_display_name": "Paciente anónimo",
                "is_anonymous": true,
                "privacy_mode": true,
                "authorized": true,
                "verified": true
              }
            }
            """.trimIndent(),
            AuthResponse::class.java,
        )

        assertEquals("Nombre desde auth", response.resolvedAuth()?.displayName)
        assertEquals("Paciente anónimo", response.resolvedAuth()?.safeDisplayName)
        assertEquals("Nombre legado", response.resolvedAuth()?.fullName)
        assertEquals("Nombre desde perfil", response.resolvedProfile()?.fullName)
        assertEquals("Paciente anónimo", response.resolvedProfile()?.displayName)
        assertEquals("Paciente anónimo", response.resolvedProfile()?.safeDisplayName)
        assertTrue(response.resolvedProfile()?.isAnonymous == true)
        assertTrue(response.resolvedProfile()?.privacyMode == true)
        assertTrue(response.resolvedProfile()?.authorized == true)
        assertTrue(response.resolvedProfile()?.verified == true)
    }

    @Test
    fun patientRegistration_omitsSupervisorOnlyField() {
        val json = gson.toJson(
            RegisterRequest(
                email = "paciente@example.com",
                password = "UnaClaveSegura123!",
                fullName = "Paciente Prueba",
                role = "patient",
                phone = "+524497654321",
                gender = "female",
                age = 29,
                privacyNoticeAccepted = true,
                privacyNoticeVersion = "2026-10-30",
            ),
        )

        assertTrue(json.contains("\"full_name\":\"Paciente Prueba\""))
        assertTrue(json.contains("\"gender\":\"female\""))
        assertTrue(json.contains("\"privacy_notice_accepted\":true"))
        assertTrue(json.contains("\"privacy_notice_version\":\"2026-10-30\""))
        assertFalse(json.contains("supervisor_type"))
    }

    @Test
    fun apiErrorParser_mapsLaravelValidationErrors() {
        val body = """
            {
              "message": "The given data was invalid.",
              "errors": {
                "email": ["Este correo ya está registrado."],
                "full_name": ["El nombre es obligatorio."]
              }
            }
        """.trimIndent().toResponseBody("application/json".toMediaType())

        val result = ApiErrorParser.parse(body)

        assertEquals("Este correo ya está registrado.", result.fieldErrors["email"])
        assertEquals("El nombre es obligatorio.", result.fieldErrors["fullName"])
    }
}
