package com.example.rehabianex

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.rehabianex.data.local.SharedPreferencesPrivacyNoticeReadStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyNoticeReadStoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: SharedPreferencesPrivacyNoticeReadStore

    @Before
    fun setUp() {
        store = SharedPreferencesPrivacyNoticeReadStore(context)
        store.clearOwner(OWNER_A)
        store.clearOwner(OWNER_B)
    }

    @After
    fun tearDown() {
        store.clearOwner(OWNER_A)
        store.clearOwner(OWNER_B)
    }

    @Test
    fun evidenceIsPersistentIsolatedByOwnerAndCanBePurged() {
        store.saveConfirmedVersion(OWNER_A, VERSION)

        val reopenedStore = SharedPreferencesPrivacyNoticeReadStore(context)
        assertEquals(VERSION, reopenedStore.confirmedVersion(OWNER_A))
        assertNull(reopenedStore.confirmedVersion(OWNER_B))

        reopenedStore.clearOwner(OWNER_A)
        assertNull(store.confirmedVersion(OWNER_A))
    }

    private companion object {
        const val OWNER_A = "synthetic-privacy-owner-a"
        const val OWNER_B = "synthetic-privacy-owner-b"
        const val VERSION = "2026-10-18"
    }
}
