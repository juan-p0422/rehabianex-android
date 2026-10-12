package com.example.rehabianex

import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.core.session.UserSession
import com.example.rehabianex.data.model.MeResponse
import com.example.rehabianex.data.model.PatientNoteDto
import com.example.rehabianex.data.model.PatientNotesResponse
import com.example.rehabianex.data.model.SessionProfileDto
import com.example.rehabianex.data.remote.ApiService
import com.example.rehabianex.data.repository.PatientDataResult
import com.example.rehabianex.data.repository.PatientRepository
import java.lang.reflect.Proxy
import java.time.OffsetDateTime
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class PatientNotesRepositoryTest {
    @Test
    fun patientHome_readsDataFallbackFromOfficialNotesEndpoint() = runTest {
        val calls = mutableListOf<String>()
        val todayNote = PatientNoteDto(
            noteId = "note-today",
            patientUid = PATIENT_UID,
            mood = "ansioso",
            anxietyLevel = 8,
            cravingLevel = 5,
            createdAt = OffsetDateTime.now().toString(),
        )
        val repository = repositoryWithNotes(
            response = Response.success(
                PatientNotesResponse(
                    patientUid = PATIENT_UID,
                    notes = null,
                    data = listOf(todayNote),
                ),
            ),
            calls = calls,
        )

        val result = repository.loadHomeData()

        val content = (result as PatientDataResult.Success).data
        assertEquals("Paciente", content.firstName)
        assertEquals("Completado", content.dailyStatus)
        assertEquals("Ansiedad alta", content.recentState)
        assertTrue("patientNotesByPatient" in calls)
    }

    @Test
    fun patientMetrics_prefersNotesAndKeepsExistingCalculations() = runTest {
        val now = ZonedDateTime.now()
        val officialNotes = listOf(
            PatientNoteDto(
                noteId = "newest",
                patientUid = PATIENT_UID,
                mood = "ansioso",
                anxietyLevel = 8,
                cravingLevel = 4,
                energyLevel = 6,
                sleepQuality = 4,
                hadRelapse = true,
                createdAt = now.toString(),
            ),
            PatientNoteDto(
                noteId = "older",
                patientUid = PATIENT_UID,
                mood = "tranquilo",
                anxietyLevel = 6,
                cravingLevel = 6,
                energyLevel = 8,
                sleepQuality = 6,
                hadRelapse = false,
                createdAt = now.minusDays(1).toString(),
            ),
        )
        val ignoredFallback = listOf(
            PatientNoteDto(
                noteId = "must-not-be-used",
                anxietyLevel = 0,
                cravingLevel = 0,
                energyLevel = 0,
                sleepQuality = 0,
                hadRelapse = false,
                createdAt = now.minusDays(2).toString(),
            ),
        )
        val repository = repositoryWithNotes(
            Response.success(
                PatientNotesResponse(
                    patientUid = PATIENT_UID,
                    notes = officialNotes,
                    data = ignoredFallback,
                ),
            ),
        )

        val result = repository.loadMetricsData()

        val metrics = (result as PatientDataResult.Success).data
        assertEquals("4/5", metrics.weeklyAnxiety)
        assertEquals("3/5", metrics.weeklyCraving)
        assertEquals("4/5", metrics.averageEnergy)
        assertEquals("3/5", metrics.averageSleep)
        assertEquals(2, metrics.recordsCount)
        assertEquals(1, metrics.relapseCount)
        assertEquals(listOf(0.6f, 0.8f), metrics.chartValues)
        assertEquals("Atención: hubo recaída registrada", metrics.riskSummary)
    }

    @Test
    fun patientMetrics_weekMeansSevenCalendarDaysNotSevenRecords() = runTest {
        val now = OffsetDateTime.now()
        val notes = (1..9).map { index ->
            PatientNoteDto(
                noteId = "same-day-$index",
                patientUid = PATIENT_UID,
                mood = "tranquilo",
                anxietyLevel = 2,
                cravingLevel = 0,
                energyLevel = 6,
                sleepQuality = 6,
                hadRelapse = false,
                createdAt = now.minusHours(index.toLong()).toString(),
            )
        }
        val repository = repositoryWithNotes(
            Response.success(PatientNotesResponse(patientUid = PATIENT_UID, notes = notes)),
        )

        val metrics = (repository.loadMetricsData() as PatientDataResult.Success).data

        assertEquals(9, metrics.weekly.recordsCount)
        assertTrue(metrics.weekly.dateRange.contains("–"))
    }

    @Test
    fun patientMetrics_doesNotConvertMissingScaleToZero() = runTest {
        val note = PatientNoteDto(
            noteId = "missing-scales",
            patientUid = PATIENT_UID,
            mood = "neutral",
            anxietyLevel = null,
            cravingLevel = null,
            createdAt = OffsetDateTime.now().toString(),
        )
        val repository = repositoryWithNotes(
            Response.success(PatientNotesResponse(patientUid = PATIENT_UID, notes = listOf(note))),
        )

        val metrics = (repository.loadMetricsData() as PatientDataResult.Success).data

        assertEquals("Sin respuesta", metrics.weekly.anxiety)
        assertEquals("Sin respuesta", metrics.weekly.craving)
        assertTrue(metrics.weekly.chartValues.isEmpty())
        assertEquals("Sin datos suficientes", metrics.weekly.riskSummary)
    }

    @Test
    fun patientMetrics_usesInclusiveSevenDayBoundaryInMexicoTimezone() = runTest {
        val originalZone = TimeZone.getDefault()
        val mexicoZone = ZoneId.of("America/Mexico_City")
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(mexicoZone))
            val today = LocalDate.now(mexicoZone)
            fun note(id: String, daysAgo: Long) = PatientNoteDto(
                noteId = id,
                patientUid = PATIENT_UID,
                mood = "neutral",
                anxietyLevel = 0,
                cravingLevel = 0,
                createdAt = today.minusDays(daysAgo)
                    .atTime(23, 30)
                    .atZone(mexicoZone)
                    .withZoneSameInstant(ZoneId.of("UTC"))
                    .toOffsetDateTime()
                    .toString(),
            )
            val repository = repositoryWithNotes(
                Response.success(
                    PatientNotesResponse(
                        patientUid = PATIENT_UID,
                        notes = listOf(note("included", 6), note("excluded", 7)),
                    ),
                ),
            )

            val metrics = (repository.loadMetricsData() as PatientDataResult.Success).data

            assertEquals(1, metrics.weekly.recordsCount)
            assertEquals("0/5", metrics.weekly.anxiety)
        } finally {
            TimeZone.setDefault(originalZone)
        }
    }

    @Test
    fun patientHome_preserves403404And500FromOfficialEndpoint() = runTest {
        val expectedMessages = mapOf(
            403 to "No tienes permiso para realizar esta acción.",
            404 to "No se encontró la información solicitada.",
            500 to "El servicio presenta un problema temporal. Inténtalo más tarde.",
        )

        expectedMessages.forEach { (statusCode, expectedMessage) ->
            val errorResponse = Response.error<PatientNotesResponse>(
                statusCode,
                "{}".toResponseBody("application/json".toMediaType()),
            )

            val result = repositoryWithNotes(errorResponse).loadHomeData()

            val failure = result as PatientDataResult.Failure
            assertEquals(statusCode, failure.statusCode)
            assertEquals(expectedMessage, failure.message)
        }
    }

    private fun repositoryWithNotes(
        response: Response<PatientNotesResponse>,
        calls: MutableList<String> = mutableListOf(),
    ): PatientRepository {
        val apiService = Proxy.newProxyInstance(
            ApiService::class.java.classLoader,
            arrayOf(ApiService::class.java),
        ) { _, method, _ ->
            calls += method.name
            when (method.name) {
                "me" -> Response.success(
                    MeResponse(
                        profile = SessionProfileDto(
                            uid = PATIENT_UID,
                            fullName = "Paciente Prueba",
                            role = "patient",
                        ),
                    ),
                )
                "patientNotesByPatient" -> response
                else -> error("Unexpected ApiService call: ${method.name}")
            }
        } as ApiService

        return PatientRepository(
            apiService = apiService,
            sessionManager = PatientSessionManager(),
        )
    }

    private class PatientSessionManager : SessionManager {
        override fun saveSession(session: UserSession) = Unit

        override fun getIdToken(): String = "header.payload.signature"

        override fun getRefreshToken(): String = "refresh-token"

        override fun getUid(): String = PATIENT_UID

        override fun getEmail(): String = "patient@example.com"

        override fun getRole(): String = "patient"

        override fun getDisplayName(): String = "Paciente Prueba"

        override fun clear() = Unit
    }

    private companion object {
        const val PATIENT_UID = "patient-001"
    }
}
