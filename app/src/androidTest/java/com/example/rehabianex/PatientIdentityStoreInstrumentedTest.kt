package com.example.rehabianex

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.rehabianex.data.local.PatientIdentityProtectionException
import com.example.rehabianex.data.local.PatientIdentityStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PatientIdentityStoreInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences by lazy {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    @Before
    fun clearPreferences() {
        preferences.edit().clear().commit()
    }

    @After
    fun cleanPreferences() {
        preferences.edit().clear().commit()
    }

    @Test
    fun legacyPlaintextIsMigratedWithoutLosingIdentity() {
        preferences.edit()
            .putString("patient_owner-legacy_original_name", "Nombre sintético")
            .putString("patient_owner-legacy_nickname", "Apodo sintético")
            .commit()

        val store = PatientIdentityStore(context)
        val identity = store.load("owner-legacy")

        assertEquals("Nombre sintético", identity.originalName)
        assertEquals("Apodo sintético", identity.nickname)
        assertFalse(preferences.all.keys.any { it.contains("owner-legacy") })
        assertTrue(preferences.all.values.all { value ->
            value is String && value.startsWith("v1:") &&
                !value.contains("Nombre sintético") && !value.contains("Apodo sintético")
        })

        val reopened = PatientIdentityStore(context).load("owner-legacy")
        assertEquals(identity, reopened)
    }

    @Test
    fun damagedProtectedValueIsNotSilentlyReplaced() {
        val store = PatientIdentityStore(context)
        store.ensureOriginalName("owner-protected", "Nombre sintético")
        val protectedKey = preferences.all.keys.single { it.endsWith("original_name") }
        val damagedValue = "v1:AAAAAAAAAAAAAAAA:AAAAAAAAAAAAAAAAAAAAAA=="
        preferences.edit().putString(protectedKey, damagedValue).commit()

        assertThrows(PatientIdentityProtectionException::class.java) {
            store.load("owner-protected")
        }
        assertEquals(damagedValue, preferences.getString(protectedKey, null))
    }

    private companion object {
        const val PREFERENCES_NAME = "patient_identity"
    }
}
