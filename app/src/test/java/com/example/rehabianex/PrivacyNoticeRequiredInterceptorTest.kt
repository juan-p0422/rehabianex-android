package com.example.rehabianex

import com.example.rehabianex.core.network.PrivacyNoticeRequiredInterceptor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyNoticeRequiredInterceptorTest {
    @Test
    fun `detects only the documented privacy precondition response`() {
        assertTrue(
            PrivacyNoticeRequiredInterceptor.isPrivacyNoticeReadRequired(
                428,
                """{"code":"privacy_notice_read_required","message":"Confirma el aviso."}""",
            ),
        )
        assertFalse(
            PrivacyNoticeRequiredInterceptor.isPrivacyNoticeReadRequired(
                428,
                """{"code":"another_precondition"}""",
            ),
        )
        assertFalse(
            PrivacyNoticeRequiredInterceptor.isPrivacyNoticeReadRequired(
                401,
                """{"code":"privacy_notice_read_required"}""",
            ),
        )
        assertFalse(
            PrivacyNoticeRequiredInterceptor.isPrivacyNoticeReadRequired(428, "not-json"),
        )
    }
}
