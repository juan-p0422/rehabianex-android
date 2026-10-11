package com.example.rehabianex

import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.core.session.UserSession
import com.example.rehabianex.data.model.MutationResponse
import com.example.rehabianex.data.model.NotificationSettingsData
import com.example.rehabianex.data.model.NotificationSettingsDto
import com.example.rehabianex.data.model.ResourceListResponse
import com.example.rehabianex.data.model.SaveNotificationSettingsRequest
import com.example.rehabianex.data.remote.ApiService
import com.example.rehabianex.data.repository.PatientDataResult
import com.example.rehabianex.data.repository.PatientRepository
import com.example.rehabianex.data.repository.SupervisorDataResult
import com.example.rehabianex.data.repository.SupervisorRepository
import com.google.gson.Gson
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class NotificationSettingsContractTest {
    @Test
    fun patient_loadsOfficialAliasesAndUpdatesExistingRemoteSettings() = runTest {
        val calls = mutableListOf<String>()
        val savedRequests = mutableListOf<SaveNotificationSettingsRequest>()
        val officialDto = NotificationSettingsDto(
            settingsId = SETTINGS_ID,
            notificationSettingId = SETTINGS_ID,
            patientUid = PATIENT_UID,
            userUid = PATIENT_UID,
            dailyCheckInEnabled = true,
            dailyCheckInTime = "21:00",
            dailyNoteEnabled = null,
            dailyNoteTime = null,
            cravingAlertsEnabled = false,
            supervisorAlertsEnabled = true,
            supportContactAlertsEnabled = false,
            timezone = NOTIFICATION_TIMEZONE,
        )
        val apiService = apiService { method, arguments ->
            calls += method
            when (method) {
                "notificationSettings" -> Response.success(
                    ResourceListResponse(data = listOf(officialDto)),
                )
                "updateNotificationSettings" -> {
                    assertEquals(SETTINGS_ID, arguments.orEmpty().filterIsInstance<String>().single())
                    arguments.orEmpty()
                        .filterIsInstance<SaveNotificationSettingsRequest>()
                        .single()
                        .also(savedRequests::add)
                    Response.success(MutationResponse(data = officialDto))
                }
                else -> error("Unexpected ApiService call: $method")
            }
        }
        val repository = PatientRepository(apiService, TestSessionManager("patient", PATIENT_UID))

        val loaded = (repository.loadNotificationSettings() as PatientDataResult.Success).data
        val saved = repository.saveNotificationSettings(loaded)

        assertEquals(SETTINGS_ID, loaded.id)
        assertTrue(loaded.dailyNoteEnabled)
        assertEquals("21:00", loaded.dailyNoteTime)
        assertTrue(saved is PatientDataResult.Success)
        assertTrue("updateNotificationSettings" in calls)
        assertFalse("createNotificationSettings" in calls)
        assertEquals(NOTIFICATION_TIMEZONE, savedRequests.single().timezone)
        assertEquals(savedRequests.single().dailyNoteEnabled, savedRequests.single().dailyCheckInEnabled)
        assertEquals(savedRequests.single().dailyNoteTime, savedRequests.single().dailyCheckInTime)
        assertFalse(savedRequests.single().cravingAlertsEnabled)
        assertTrue(savedRequests.single().supervisorAlertsEnabled)
        assertFalse(savedRequests.single().supportContactAlertsEnabled)
    }

    @Test
    fun patient_usesIdempotentPostWhenNoRemoteIdIsAvailable() = runTest {
        val calls = mutableListOf<String>()
        val requests = mutableListOf<SaveNotificationSettingsRequest>()
        val apiService = apiService { method, arguments ->
            calls += method
            when (method) {
                "createNotificationSettings" -> {
                    arguments.orEmpty()
                        .filterIsInstance<SaveNotificationSettingsRequest>()
                        .single()
                        .also(requests::add)
                    Response.success(
                        MutationResponse(
                            id = SETTINGS_ID,
                            data = NotificationSettingsDto(settingsId = SETTINGS_ID),
                        ),
                    )
                }
                else -> error("Unexpected ApiService call: $method")
            }
        }
        val repository = PatientRepository(apiService, TestSessionManager("patient", PATIENT_UID))

        val result = repository.saveNotificationSettings(defaultSettings(id = null))

        assertTrue(result is PatientDataResult.Success)
        assertEquals(listOf("createNotificationSettings"), calls)
        val requestJson = Gson().toJson(requests.single())
        assertFalse(requestJson.contains("patient_uid"))
        assertFalse(requestJson.contains("supervisor_uid"))
        assertFalse(requestJson.contains("user_uid"))
        assertEquals(NOTIFICATION_TIMEZONE, requests.single().timezone)
    }

    @Test
    fun patient_handlesRemoteValidationErrorWithFriendlyMessage() = runTest {
        val apiService = apiService { method, _ ->
            when (method) {
                "updateNotificationSettings" -> Response.error<MutationResponse<NotificationSettingsDto>>(
                    422,
                    """{"message":"invalid payload schema"}"""
                        .toResponseBody("application/json".toMediaType()),
                )
                else -> error("Unexpected ApiService call: $method")
            }
        }
        val repository = PatientRepository(apiService, TestSessionManager("patient", PATIENT_UID))

        val result = repository.saveNotificationSettings(defaultSettings(id = SETTINGS_ID))

        val failure = result as PatientDataResult.Failure
        assertEquals(422, failure.statusCode)
        assertEquals("invalid payload schema", failure.message)
    }

    @Test
    fun supervisor_keepsNotificationSettingsLocalWithoutCallingBackend() = runTest {
        val calls = mutableListOf<String>()
        val apiService = apiService { method, _ ->
            calls += method
            error("Supervisor notification settings must remain local")
        }
        val repository = SupervisorRepository(
            apiService = apiService,
            sessionManager = TestSessionManager("supervisor", SUPERVISOR_UID),
            notificationSettingsStore = null,
        )

        val loaded = (repository.loadNotificationSettings() as SupervisorDataResult.Success).data
        val saved = repository.saveNotificationSettings(
            loaded.copy(dailyNoteEnabled = false, dailyNoteTime = "07:30"),
        )

        assertTrue(saved is SupervisorDataResult.Success)
        assertTrue(loaded.id?.startsWith("local_") == true)
        assertTrue(calls.isEmpty())
    }

    private fun apiService(
        handler: (String, Array<out Any?>?) -> Any,
    ): ApiService {
        return Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java),
        ) { _, method, arguments ->
            handler(method.name, arguments)
        } as ApiService
    }

    private class TestSessionManager(
        private val role: String,
        private val uid: String,
    ) : SessionManager {
        override fun saveSession(session: UserSession) = Unit

        override fun getIdToken(): String = "header.payload.signature"

        override fun getRefreshToken(): String = "refresh-token"

        override fun getUid(): String = uid

        override fun getEmail(): String = "$role@example.com"

        override fun getRole(): String = role

        override fun getDisplayName(): String = "Usuario Prueba"

        override fun clear() = Unit
    }

    private companion object {
        const val PATIENT_UID = "patient-001"
        const val SUPERVISOR_UID = "supervisor-001"
        const val SETTINGS_ID = "settings-001"
        const val NOTIFICATION_TIMEZONE = "America/Mexico_City"

        fun defaultSettings(id: String?): NotificationSettingsData {
            return NotificationSettingsData(
                id = id,
                dailyNoteEnabled = true,
                dailyNoteTime = "20:30",
                soberDayEnabled = true,
                soberDayTime = "08:00",
                achievementEnabled = true,
                achievementTime = "09:00",
                eventRemindersEnabled = true,
                motivationalEnabled = true,
                motivationalTime = "20:00",
            )
        }
    }
}
