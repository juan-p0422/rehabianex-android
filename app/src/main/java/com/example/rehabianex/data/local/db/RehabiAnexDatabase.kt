package com.example.rehabianex.data.local.db

import android.content.Context
import com.example.rehabianex.BuildConfig
import com.example.rehabianex.data.local.security.DatabasePassphraseProvider
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.io.FileInputStream
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        PatientNoteEntity::class,
        AgendaEventEntity::class,
        SupportContactEntity::class,
        NotificationSettingsEntity::class,
        LocalResourceEntity::class,
        AchievementEntity::class,
        PatientAchievementEntity::class,
        CacheSyncStateEntity::class,
        PatientSummaryEntity::class,
        PatientProfileEntity::class,
        SupervisorProfileSnapshotEntity::class,
        SupervisorDashboardSummaryEntity::class,
        AdminProfileSnapshotEntity::class,
        AdminDashboardSummaryEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(RoomConverters::class)
abstract class RehabiAnexDatabase : RoomDatabase() {
    abstract fun patientNoteDao(): PatientNoteDao
    abstract fun agendaEventDao(): AgendaEventDao
    abstract fun supportContactDao(): SupportContactDao
    abstract fun notificationSettingsDao(): NotificationSettingsDao
    abstract fun localResourceDao(): LocalResourceDao
    abstract fun achievementDao(): AchievementDao
    abstract fun patientAchievementDao(): PatientAchievementDao
    abstract fun cacheSyncStateDao(): CacheSyncStateDao
    abstract fun patientSummaryDao(): PatientSummaryDao
    abstract fun patientProfileDao(): PatientProfileDao
    abstract fun supervisorProfileSnapshotDao(): SupervisorProfileSnapshotDao
    abstract fun supervisorDashboardSummaryDao(): SupervisorDashboardSummaryDao
    abstract fun adminProfileSnapshotDao(): AdminProfileSnapshotDao
    abstract fun adminDashboardSummaryDao(): AdminDashboardSummaryDao

    companion object {
        @Volatile
        private var instance: RehabiAnexDatabase? = null

        fun getInstance(context: Context): RehabiAnexDatabase = instance ?: synchronized(this) {
            instance ?: buildEncryptedDatabase(
                context = context.applicationContext,
                databaseName = DATABASE_NAME,
                isDebug = BuildConfig.DEBUG,
            ).also { instance = it }
        }

        fun buildEncryptedDatabaseForTest(
            context: Context,
            databaseName: String,
        ): RehabiAnexDatabase = buildEncryptedDatabase(
            context = context.applicationContext,
            databaseName = databaseName,
            isDebug = true,
        )

        private fun buildEncryptedDatabase(
            context: Context,
            databaseName: String,
            isDebug: Boolean,
        ): RehabiAnexDatabase {
            Logger.setTarget(NoopTarget())
            System.loadLibrary(SQLCIPHER_LIBRARY)
            val databaseFile = context.getDatabasePath(databaseName)
            when (
                legacyDatabaseAction(
                    databaseExists = databaseFile.exists(),
                    hasPlaintextHeader = databaseFile.hasPlaintextSqliteHeader(),
                    isDebug = isDebug,
                )
            ) {
                LegacyDatabaseAction.Continue -> Unit
                LegacyDatabaseAction.DeletePlaintextDebugDatabase -> {
                    deleteDebugPlaintextDatabase(context, databaseName)
                }
                LegacyDatabaseAction.RejectPlaintextReleaseDatabase -> {
                    throw IllegalStateException(
                        "La base local heredada no está cifrada; release requiere una migración explícita.",
                    )
                }
            }

            val databaseExistsAfterPolicy = context.getDatabasePath(databaseName).exists()
            val passphrase = DatabasePassphraseProvider(context).getOrCreate(
                databaseAlreadyExists = databaseExistsAfterPolicy,
            )
            val factory = SupportOpenHelperFactory(passphrase.copyOf())
            passphrase.fill(0)
            return Room.databaseBuilder(
                context,
                RehabiAnexDatabase::class.java,
                databaseName,
            )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
                .also { encryptedDatabase ->
                    // Fuerza apertura, validación de clave y migraciones antes de exponer la instancia.
                    encryptedDatabase.openHelper.writableDatabase
                }
        }

        private fun deleteDebugPlaintextDatabase(context: Context, databaseName: String) {
            context.deleteDatabase(databaseName)
            val databaseFile = context.getDatabasePath(databaseName)
            listOf(
                databaseFile,
                File(databaseFile.path + "-wal"),
                File(databaseFile.path + "-shm"),
                File(databaseFile.path + "-journal"),
            ).forEach { file ->
                if (file.exists() && !file.delete()) {
                    throw IllegalStateException(
                        "No fue posible retirar la base local de pruebas sin cifrar.",
                    )
                }
            }
        }

        private fun File.hasPlaintextSqliteHeader(): Boolean {
            if (!isFile || length() < SQLITE_HEADER.size) return false
            val header = ByteArray(SQLITE_HEADER.size)
            return FileInputStream(this).use { input ->
                input.read(header) == header.size && header.contentEquals(SQLITE_HEADER)
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE patient_notes ADD COLUMN clientMutationId TEXT")
                db.execSQL(
                    "ALTER TABLE patient_notes ADD COLUMN syncState TEXT NOT NULL DEFAULT 'synced'",
                )
                db.execSQL(
                    "UPDATE patient_notes SET syncState = CASE " +
                        "WHEN pendingSync = 1 THEN 'pending' ELSE 'synced' END",
                )
                db.execSQL(
                    "UPDATE patient_notes SET clientMutationId = " +
                        "substr(cacheKey, instr(cacheKey, ':local-') + 7) " +
                        "WHERE pendingSync = 1 AND cacheKey LIKE '%:local-%'",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_patient_notes_clientMutationId " +
                        "ON patient_notes (clientMutationId)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_patient_notes_syncState " +
                        "ON patient_notes (syncState)",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS patient_profile (
                        ownerUid TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        nickname TEXT,
                        fullName TEXT,
                        privacyMode INTEGER NOT NULL,
                        isAnonymous INTEGER NOT NULL,
                        sobrietyStartDate TEXT,
                        photoUrl TEXT,
                        lastSyncedAt INTEGER NOT NULL,
                        isStale INTEGER NOT NULL,
                        PRIMARY KEY(ownerUid)
                    )
                    """.trimIndent(),
                )
                // El snapshot v2 no registraba privacidad; se elimina para no
                // conservar un nombre real cuyo permiso de caché se desconoce.
                db.execSQL("DELETE FROM patient_summary")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS supervisor_profile_snapshot (
                        ownerUid TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        supervisorType TEXT NOT NULL,
                        supervisorCode TEXT NOT NULL,
                        authorized INTEGER NOT NULL,
                        verified INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        lastSyncedAt INTEGER NOT NULL,
                        isStale INTEGER NOT NULL,
                        PRIMARY KEY(ownerUid)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS supervisor_dashboard_summary (
                        ownerUid TEXT NOT NULL,
                        patientsCount INTEGER NOT NULL,
                        pendingRequestsCount INTEGER NOT NULL,
                        openInterventionsCount INTEGER NOT NULL,
                        lastSyncedAt INTEGER NOT NULL,
                        PRIMARY KEY(ownerUid)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS admin_profile_snapshot (
                        ownerUid TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        email TEXT,
                        status TEXT NOT NULL,
                        lastSyncedAt INTEGER NOT NULL,
                        PRIMARY KEY(ownerUid)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS admin_dashboard_summary (
                        ownerUid TEXT NOT NULL,
                        pendingSupervisors INTEGER NOT NULL,
                        activeSupervisors INTEGER NOT NULL,
                        suspendedSupervisors INTEGER NOT NULL,
                        lastSyncedAt INTEGER NOT NULL,
                        PRIMARY KEY(ownerUid)
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE agenda_events ADD COLUMN attendanceReported INTEGER")
                db.execSQL("ALTER TABLE agenda_events ADD COLUMN attendanceReportedAt TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_resources ADD COLUMN sourceName TEXT")
                db.execSQL("ALTER TABLE local_resources ADD COLUMN sourceUrl TEXT")
                db.execSQL("ALTER TABLE local_resources ADD COLUMN reviewedAt TEXT")
            }
        }

        private const val DATABASE_NAME = "rehabianex-offline.db"
        private const val SQLCIPHER_LIBRARY = "sqlcipher"
        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}
