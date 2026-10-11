package com.example.rehabianex

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.rehabianex.core.session.OfflineDataCleanupCoordinator
import com.example.rehabianex.data.local.PatientIdentityStore
import com.example.rehabianex.data.local.SupervisorNotificationSettingsStore
import com.example.rehabianex.data.local.db.PatientSummaryEntity
import com.example.rehabianex.data.local.db.PatientProfileEntity
import com.example.rehabianex.data.local.db.RehabiAnexDatabase
import com.example.rehabianex.data.local.db.toPendingEntity
import com.example.rehabianex.data.model.CreatePatientNoteRequest
import java.io.FileInputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedRoomDatabaseInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: RehabiAnexDatabase? = null

    @Before
    fun cleanDatabase() {
        context.deleteDatabase(TEST_DATABASE_NAME)
    }

    @After
    fun closeDatabase() {
        database?.close()
        context.deleteDatabase(TEST_DATABASE_NAME)
    }

    @Test
    fun encryptedDatabaseOpensPersistsAndHasNoPlaintextSqliteHeader() = runBlocking {
        database = RehabiAnexDatabase.buildEncryptedDatabaseForTest(
            context,
            TEST_DATABASE_NAME,
        )
        database!!.patientSummaryDao().upsert(
            PatientSummaryEntity(
                ownerUid = "patient-encrypted",
                fullName = "Nombre sensible",
                sobrietyStartDate = "2026-08-01",
                lastSyncedAt = 1L,
            ),
        )
        database!!.close()
        database = null

        val header = ByteArray(SQLITE_HEADER.size)
        FileInputStream(context.getDatabasePath(TEST_DATABASE_NAME)).use { input ->
            assertEquals(header.size, input.read(header))
        }
        assertFalse(header.contentEquals(SQLITE_HEADER))

        database = RehabiAnexDatabase.buildEncryptedDatabaseForTest(
            context,
            TEST_DATABASE_NAME,
        )
        assertEquals(
            "Nombre sensible",
            database!!.patientSummaryDao().get("patient-encrypted")?.fullName,
        )
    }

    @Test
    fun plaintextDebugDatabaseIsReplacedByEncryptedDatabase() {
        val file = context.getDatabasePath(TEST_DATABASE_NAME)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { plainDatabase ->
            plainDatabase.execSQL("CREATE TABLE legacy_secret(value TEXT)")
        }
        val plainHeader = ByteArray(SQLITE_HEADER.size)
        FileInputStream(file).use { it.read(plainHeader) }
        assertArrayEquals(SQLITE_HEADER, plainHeader)

        database = RehabiAnexDatabase.buildEncryptedDatabaseForTest(
            context,
            TEST_DATABASE_NAME,
        )
        database!!.openHelper.writableDatabase
        val encryptedHeader = ByteArray(SQLITE_HEADER.size)
        FileInputStream(file).use { it.read(encryptedHeader) }
        assertFalse(encryptedHeader.contentEquals(SQLITE_HEADER))
    }

    @Test
    fun logoutAndUserSwitchPurgeOwnerData() = runBlocking {
        database = RehabiAnexDatabase.buildEncryptedDatabaseForTest(
            context,
            TEST_DATABASE_NAME,
        )
        val identityStore = PatientIdentityStore(context)
        val supervisorStore = SupervisorNotificationSettingsStore(context)
        val coordinator = OfflineDataCleanupCoordinator(
            context = context,
            database = database!!,
            patientIdentityStore = identityStore,
            supervisorNotificationSettingsStore = supervisorStore,
        )
        val pendingRequest = CreatePatientNoteRequest(
            mood = "neutral",
            moodScore = 5,
            anxietyLevel = 2,
            cravingLevel = 1,
            hadRelapse = false,
        )

        database!!.patientSummaryDao().upsert(
            PatientSummaryEntity("owner-a", "Propietario A", null, 1L),
        )
        database!!.patientProfileDao().upsert(profile("owner-a", "Propietario A"))
        database!!.patientNoteDao().upsert(pendingRequest.toPendingEntity("owner-a"))
        identityStore.ensureOriginalName("owner-a", "Propietario A")
        coordinator.clearAfterConfirmedLogout("owner-a", "patient")

        assertNull(database!!.patientSummaryDao().get("owner-a"))
        assertNull(database!!.patientProfileDao().get("owner-a"))
        assertEquals(0, database!!.patientNoteDao().pendingCount("owner-a"))
        assertNull(identityStore.load("owner-a").originalName)

        database!!.patientSummaryDao().upsert(
            PatientSummaryEntity("owner-b", "Propietario B", null, 2L),
        )
        database!!.patientProfileDao().upsert(profile("owner-b", "Propietario B"))
        coordinator.clearForUserSwitch("owner-b", "patient")
        assertNull(database!!.patientSummaryDao().get("owner-b"))
        assertNull(database!!.patientProfileDao().get("owner-b"))
    }

    private fun profile(ownerUid: String, displayName: String) = PatientProfileEntity(
        ownerUid = ownerUid,
        displayName = displayName,
        nickname = null,
        fullName = displayName,
        privacyMode = false,
        isAnonymous = false,
        sobrietyStartDate = null,
        photoUrl = null,
        lastSyncedAt = 1L,
        isStale = false,
    )

    private companion object {
        const val TEST_DATABASE_NAME = "rehabianex-encrypted-test.db"
        val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}
