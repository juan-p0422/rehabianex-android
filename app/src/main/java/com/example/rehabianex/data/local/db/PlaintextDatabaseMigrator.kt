package com.example.rehabianex.data.local.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase as FrameworkSQLiteDatabase
import java.io.File
import java.io.FileInputStream
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherSQLiteDatabase

internal class PlaintextDatabaseMigrator(
    private val context: Context,
    databaseName: String,
) {
    private val databaseFile = context.getDatabasePath(databaseName)
    private val encryptedTemporary = File(databaseFile.path + ENCRYPTED_TEMPORARY_SUFFIX)
    private val plaintextBackup = File(databaseFile.path + PLAINTEXT_BACKUP_SUFFIX)

    fun migrateIfNeeded(passphrase: ByteArray) {
        recoverInterruptedReplacement(passphrase)
        if (!databaseFile.hasPlaintextSqliteHeader()) return

        checkpointPlaintextDatabase()
        deleteDatabaseFiles(encryptedTemporary)
        val expected = inspectPlaintext(databaseFile)
        exportToEncryptedDatabase(databaseFile, encryptedTemporary, passphrase, expected.version)
        validateEncrypted(encryptedTemporary, passphrase, expected)
        replaceValidatedDatabase(passphrase, expected)
    }

    private fun recoverInterruptedReplacement(passphrase: ByteArray) {
        if (!databaseFile.exists() && plaintextBackup.exists()) {
            if (!plaintextBackup.renameTo(databaseFile)) {
                throw IllegalStateException("No fue posible recuperar la base local anterior.")
            }
            moveSidecar(plaintextBackup, databaseFile, "-wal")
            moveSidecar(plaintextBackup, databaseFile, "-shm")
            moveSidecar(plaintextBackup, databaseFile, "-journal")
        }
        if (databaseFile.exists() && !databaseFile.hasPlaintextSqliteHeader() && plaintextBackup.exists()) {
            validateEncrypted(databaseFile, passphrase, null)
            deleteDatabaseFiles(plaintextBackup)
        }
        if (encryptedTemporary.exists()) deleteDatabaseFiles(encryptedTemporary)
    }

    private fun checkpointPlaintextDatabase() {
        FrameworkSQLiteDatabase.openDatabase(
            databaseFile.path,
            null,
            FrameworkSQLiteDatabase.OPEN_READWRITE,
        ).use { database ->
            database.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { cursor ->
                while (cursor.moveToNext()) Unit
            }
        }
    }

    private fun inspectPlaintext(file: File): DatabaseSnapshot =
        FrameworkSQLiteDatabase.openDatabase(
            file.path,
            null,
            FrameworkSQLiteDatabase.OPEN_READONLY,
        ).use { database ->
            val integrity = database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
            }
            if (!integrity) {
                throw IllegalStateException("La base local anterior no superó la validación de integridad.")
            }
            DatabaseSnapshot(database.version, database.tableRowCounts())
        }

    private fun exportToEncryptedDatabase(
        source: File,
        target: File,
        passphrase: ByteArray,
        version: Int,
    ) {
        val targetPath = target.path.replace("'", "''")
        val keyHex = passphrase.joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
        val database = CipherSQLiteDatabase.openDatabase(
            source.path,
            ByteArray(0),
            null,
            CipherSQLiteDatabase.OPEN_READWRITE,
            null,
        )
        try {
            // A transaction pins ATTACH and sqlcipher_export to the same pooled
            // connection. Without it, Android may execute the SELECT on a
            // different connection where the attached schema does not exist.
            database.beginTransaction()
            try {
                database.rawExecSQL("ATTACH DATABASE '$targetPath' AS encrypted KEY \"x'$keyHex'\"")
                database.rawQuery(
                    "SELECT sqlcipher_export('encrypted')",
                    *emptyArray<Any>(),
                ).use { cursor ->
                    check(cursor.moveToFirst()) { "La exportación cifrada no produjo resultado." }
                }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        } finally {
            database.close()
        }
        CipherSQLiteDatabase.openDatabase(
            target.path,
            passphrase.copyOf(),
            null,
            CipherSQLiteDatabase.OPEN_READWRITE,
            null,
        ).use { encrypted ->
            encrypted.rawExecSQL("PRAGMA user_version = $version")
        }
    }

    private fun validateEncrypted(
        file: File,
        passphrase: ByteArray,
        expected: DatabaseSnapshot?,
    ) {
        val database = CipherSQLiteDatabase.openDatabase(
            file.path,
            passphrase.copyOf(),
            null,
            CipherSQLiteDatabase.OPEN_READONLY,
            null,
        )
        try {
            val integrity = database.rawQuery(
                "PRAGMA integrity_check",
                emptyArray<String>(),
            ).use { cursor ->
                cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
            }
            check(integrity) { "La copia cifrada no superó la validación de integridad." }
            if (expected != null) {
                check(database.version == expected.version) {
                    "La copia cifrada no conserva la versión del esquema."
                }
                check(database.tableRowCounts() == expected.rowCounts) {
                    "La copia cifrada no conserva todos los registros locales."
                }
            }
        } finally {
            database.close()
        }
    }

    private fun replaceValidatedDatabase(passphrase: ByteArray, expected: DatabaseSnapshot) {
        deleteDatabaseFiles(plaintextBackup)
        if (!databaseFile.renameTo(plaintextBackup)) {
            throw IllegalStateException("No fue posible preparar el reemplazo seguro de la base local.")
        }
        moveSidecar(databaseFile, plaintextBackup, "-wal")
        moveSidecar(databaseFile, plaintextBackup, "-shm")
        moveSidecar(databaseFile, plaintextBackup, "-journal")
        try {
            if (!encryptedTemporary.renameTo(databaseFile)) {
                throw IllegalStateException("No fue posible activar la base local cifrada.")
            }
            validateEncrypted(databaseFile, passphrase, expected)
            deleteDatabaseFiles(plaintextBackup)
        } catch (error: Throwable) {
            deleteDatabaseFiles(databaseFile)
            if (!plaintextBackup.renameTo(databaseFile)) {
                throw IllegalStateException(
                    "Falló la migración y no fue posible restaurar la base local anterior.",
                    error,
                )
            }
            moveSidecar(plaintextBackup, databaseFile, "-wal")
            moveSidecar(plaintextBackup, databaseFile, "-shm")
            moveSidecar(plaintextBackup, databaseFile, "-journal")
            throw IllegalStateException("La migración cifrada falló; se restauró la base anterior.", error)
        } finally {
            deleteDatabaseFiles(encryptedTemporary)
        }
    }

    private fun moveSidecar(fromBase: File, toBase: File, suffix: String) {
        val source = File(fromBase.path + suffix)
        if (!source.exists()) return
        val target = File(toBase.path + suffix)
        if (!source.renameTo(target)) {
            throw IllegalStateException("No fue posible conservar un archivo auxiliar de la base local.")
        }
    }

    private fun deleteDatabaseFiles(base: File) {
        listOf(base, File(base.path + "-wal"), File(base.path + "-shm"), File(base.path + "-journal"))
            .forEach { file ->
                if (file.exists() && !file.delete()) {
                    throw IllegalStateException("No fue posible retirar un temporal de migración local.")
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

    private fun FrameworkSQLiteDatabase.tableRowCounts(): Map<String, Long> =
        rawQuery(TABLES_QUERY, null).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val table = cursor.getString(0)
                    rawQuery("SELECT COUNT(*) FROM ${quoteIdentifier(table)}", null).use { count ->
                        check(count.moveToFirst())
                        put(table, count.getLong(0))
                    }
                }
            }
        }

    private fun CipherSQLiteDatabase.tableRowCounts(): Map<String, Long> =
        rawQuery(TABLES_QUERY, emptyArray<String>()).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val table = cursor.getString(0)
                    rawQuery(
                        "SELECT COUNT(*) FROM ${quoteIdentifier(table)}",
                        emptyArray<String>(),
                    ).use { count ->
                        check(count.moveToFirst())
                        put(table, count.getLong(0))
                    }
                }
            }
        }

    private fun quoteIdentifier(identifier: String): String =
        "\"${identifier.replace("\"", "\"\"")}\""

    private data class DatabaseSnapshot(val version: Int, val rowCounts: Map<String, Long>)

    private companion object {
        const val ENCRYPTED_TEMPORARY_SUFFIX = ".encrypted-migration"
        const val PLAINTEXT_BACKUP_SUFFIX = ".plaintext-backup"
        const val TABLES_QUERY =
            "SELECT name FROM sqlite_master WHERE type='table' " +
                "AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' ORDER BY name"
        val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}
