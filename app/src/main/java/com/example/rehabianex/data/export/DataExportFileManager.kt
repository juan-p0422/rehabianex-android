package com.example.rehabianex.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DataExportFileManager(
    context: Context,
) {
    private val applicationContext = context.applicationContext
    private val exportDirectory = File(applicationContext.cacheDir, EXPORT_DIRECTORY)

    suspend fun createPrivateExport(json: String): Intent = withContext(Dispatchers.IO) {
        clearExports()
        check(exportDirectory.exists() || exportDirectory.mkdirs()) {
            "No fue posible preparar el archivo temporal."
        }
        val file = File(exportDirectory, exportFileName(ZonedDateTime.now()))
        file.outputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(json)
        }
        val uri = FileProvider.getUriForFile(
            applicationContext,
            "${applicationContext.packageName}.fileprovider",
            file,
        )
        Intent(Intent.ACTION_SEND).apply {
            type = JSON_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, "Exportación de datos de RehabiAnex")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun clearExports() {
        if (!exportDirectory.exists()) return
        exportDirectory.listFiles()?.forEach { file ->
            if (file.isFile && file.extension.equals("json", ignoreCase = true)) {
                file.delete()
            }
        }
    }

    internal fun exportFileName(dateTime: ZonedDateTime): String =
        "rehabianex-data-${dateTime.format(FILE_TIMESTAMP)}.json"

    private companion object {
        const val EXPORT_DIRECTORY = "exports"
        const val JSON_MIME_TYPE = "application/json"
        val FILE_TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
