package com.example.rehabianex

import com.example.rehabianex.data.model.MeResponse
import com.example.rehabianex.data.model.PrivacyNoticeDto
import com.example.rehabianex.data.model.PrivacyNoticeReadDto
import com.example.rehabianex.data.model.PrivacyNoticeReadResponse
import com.example.rehabianex.data.model.PrivacyNoticeResponse
import com.example.rehabianex.data.model.SessionProfileDto
import com.example.rehabianex.data.remote.ApiService
import com.example.rehabianex.data.repository.PrivacyNoticeConfirmResult
import com.example.rehabianex.data.repository.PrivacyNoticeRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class PrivacyNoticeRepositoryTest {
    @Test
    fun legacyProfileWithoutConfirmationRequiresTheNotice() = runTest {
        val repository = PrivacyNoticeRepository(
            apiService = apiService { method ->
                when (method) {
                    "privacyNotice" -> Response.success(noticeResponse())
                    "me" -> Response.success(MeResponse(profile = SessionProfileDto()))
                    else -> error("Unexpected call: $method")
                }
            },
            fallbackContent = { "fallback" },
        )

        val result = repository.load(requireServerStatus = true)

        assertTrue(result.serverStatusChecked)
        assertFalse(result.currentReadConfirmedByServer)
    }

    @Test
    fun currentConfirmationRequiresServerProfileAndMatchingVersion() = runTest {
        val repository = PrivacyNoticeRepository(
            apiService = apiService { method ->
                when (method) {
                    "privacyNotice" -> Response.success(noticeResponse())
                    "me" -> Response.success(
                        MeResponse(
                            profile = SessionProfileDto(
                                privacyNoticeRead = PrivacyNoticeReadDto(
                                    privacyNoticeVersion = "2026-10-18",
                                    confirmedAt = "2026-10-18T12:00:00-06:00",
                                    status = "confirmed",
                                    isCurrent = true,
                                ),
                            ),
                        ),
                    )
                    else -> error("Unexpected call: $method")
                }
            },
            fallbackContent = { "fallback" },
        )

        val result = repository.load(requireServerStatus = true)

        assertTrue(result.serverStatusChecked)
        assertTrue(result.currentReadConfirmedByServer)
    }

    @Test
    fun offlineFallbackNeverGrantsProtectedAccess() = runTest {
        val repository = PrivacyNoticeRepository(
            apiService = apiService { throw IllegalStateException("offline") },
            fallbackContent = { "Aviso local completo" },
        )

        val result = repository.load(requireServerStatus = true)

        assertFalse(result.serverStatusChecked)
        assertFalse(result.currentReadConfirmedByServer)
        assertTrue(result.notice.content!!.contains("Aviso local"))
    }

    @Test
    fun failedConfirmationDoesNotProduceSuccess() = runTest {
        val repository = PrivacyNoticeRepository(
            apiService = apiService { method ->
                if (method == "confirmPrivacyNoticeRead") {
                    Response.error<PrivacyNoticeReadResponse>(
                        503,
                        "{\"message\":\"Fallo sintético\"}"
                            .toResponseBody("application/json".toMediaType()),
                    )
                } else {
                    error("Unexpected call: $method")
                }
            },
            fallbackContent = { "fallback" },
        )

        val result = repository.confirmRead("2026-10-18")

        assertTrue(result is PrivacyNoticeConfirmResult.Failure)
    }

    @Test
    fun successfulConfirmationRequiresConfirmedServerBody() = runTest {
        val repository = PrivacyNoticeRepository(
            apiService = apiService { method ->
                if (method == "confirmPrivacyNoticeRead") {
                    Response.success(
                        PrivacyNoticeReadResponse(
                            ok = true,
                            confirmation = PrivacyNoticeReadDto(
                                privacyNoticeVersion = "2026-10-18",
                                status = "confirmed",
                            ),
                        ),
                    )
                } else {
                    error("Unexpected call: $method")
                }
            },
            fallbackContent = { "fallback" },
        )

        assertTrue(repository.confirmRead("2026-10-18") is PrivacyNoticeConfirmResult.Success)
    }

    @Test
    fun successfulHttpResponseWithDifferentVersionDoesNotGrantAccess() = runTest {
        val repository = PrivacyNoticeRepository(
            apiService = apiService { method ->
                if (method == "confirmPrivacyNoticeRead") {
                    Response.success(
                        PrivacyNoticeReadResponse(
                            ok = true,
                            confirmation = PrivacyNoticeReadDto(
                                privacyNoticeVersion = "2026-08-01",
                                status = "confirmed",
                            ),
                        ),
                    )
                } else {
                    error("Unexpected call: $method")
                }
            },
            fallbackContent = { "fallback" },
        )

        assertTrue(repository.confirmRead("2026-10-18") is PrivacyNoticeConfirmResult.Failure)
    }

    private fun noticeResponse() = PrivacyNoticeResponse(
        ok = true,
        notice = PrivacyNoticeDto(
            title = "Aviso",
            version = "2026-10-18",
            publishedOn = "2026-10-18",
            content = "Contenido",
        ),
    )

    private fun apiService(handler: (String) -> Any): ApiService = Proxy.newProxyInstance(
        ApiService::class.java.classLoader,
        arrayOf(ApiService::class.java),
    ) { _, method, _ -> handler(method.name) } as ApiService
}
