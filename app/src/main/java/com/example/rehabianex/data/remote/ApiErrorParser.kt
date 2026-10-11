package com.example.rehabianex.data.remote

import com.example.rehabianex.core.error.ErrorMapper
import com.google.gson.JsonParser
import okhttp3.ResponseBody

data class ParsedApiError(
    val message: String?,
    val fieldErrors: Map<String, String>,
)

object ApiErrorParser {
    fun parse(
        errorBody: ResponseBody?,
        statusCode: Int? = 422,
    ): ParsedApiError {
        val rawBody = runCatching { errorBody?.string() }.getOrNull()
            ?: return ParsedApiError(null, emptyMap())

        return runCatching {
            val element = JsonParser.parseString(rawBody)
            if (!element.isJsonObject) return@runCatching ParsedApiError(null, emptyMap())
            val root = element.asJsonObject
            val message = root.get("message")
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?.let { ErrorMapper.safeBackendMessage(statusCode, it) }

            val errors = if (statusCode == 422) {
                root.get("errors")
                    ?.takeIf { it.isJsonObject }
                    ?.asJsonObject
                    ?.entrySet()
                    ?.asSequence()
                    ?.take(MAX_FIELD_ERRORS)
                    ?.associate { (field, value) ->
                        field.toUiField() to (
                            ErrorMapper.safeBackendMessage(422, value.firstMessage())
                                ?: "Revisa este dato."
                            )
                    }
                    .orEmpty()
            } else {
                emptyMap()
            }

            ParsedApiError(message, errors)
        }.getOrElse {
            ParsedApiError(null, emptyMap())
        }
    }

    private fun String.toUiField(): String {
        val safeField = filter { it.isLetterOrDigit() || it == '_' }
            .take(MAX_FIELD_NAME_LENGTH)
            .ifBlank { "field" }
        return when (safeField) {
            "full_name" -> "fullName"
            "supervisor_type" -> "supervisorType"
            "password_confirmation" -> "passwordConfirmation"
            "daily_check_in_time" -> "dailyCheckInTime"
            "daily_note_time" -> "dailyNoteTime"
            "sober_day_time" -> "soberDayTime"
            "achievement_time" -> "achievementTime"
            "motivational_time" -> "motivationalTime"
            "patient_uid" -> "patientUid"
            "supervisor_uid" -> "supervisorUid"
            "privacy_notice_accepted", "privacy_notice_version" -> "privacy"
            else -> safeField.split('_')
            .filter(String::isNotBlank)
            .mapIndexed { index, part ->
                if (index == 0) part else part.replaceFirstChar { it.uppercaseChar() }
            }
            .joinToString("")
            .ifBlank { "field" }
        }
    }

    private fun com.google.gson.JsonElement.firstMessage(): String? = when {
        isJsonPrimitive -> asString
        isJsonArray -> asJsonArray
            .firstOrNull { it.isJsonPrimitive }
            ?.asString
        isJsonObject -> asJsonObject.get("message")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
        else -> null
    }

    private const val MAX_FIELD_ERRORS = 30
    private const val MAX_FIELD_NAME_LENGTH = 80
}
