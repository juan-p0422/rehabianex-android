package com.example.rehabianex.core.validation

const val MAX_AI_CHAT_QUESTION_LENGTH = 800

fun String.sanitizeAiChatQuestion(): String {
    return filterNot { it.isISOControl() }
        .replace(HTML_TAG_REGEX, " ")
        .replace(EMAIL_REGEX, PROTECTED_DATA_LABEL)
        .replace(PHONE_REGEX, PROTECTED_DATA_LABEL)
        .replace(LONG_IDENTIFIER_REGEX, PROTECTED_DATA_LABEL)
        .replace(ADDRESS_REGEX, PROTECTED_DATA_LABEL)
        .replace("<", "")
        .replace(">", "")
        .replace(Regex("\\s+"), " ")
        .trim()
}

private const val PROTECTED_DATA_LABEL = "[dato protegido]"
private val HTML_TAG_REGEX = Regex("<[^>]*>")
private val EMAIL_REGEX = Regex(
    pattern = "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
    option = RegexOption.IGNORE_CASE,
)
private val PHONE_REGEX = Regex("(?<![A-Z0-9])(?:\\+?\\d[\\d .()\\-]{7,}\\d)(?![A-Z0-9])")
private val LONG_IDENTIFIER_REGEX = Regex("\\b(?=[A-Z0-9_-]{20,}\\b)(?=[A-Z0-9_-]*[A-Z])(?=[A-Z0-9_-]*\\d)[A-Z0-9_-]+\\b", RegexOption.IGNORE_CASE)
private val ADDRESS_REGEX = Regex(
    pattern = "\\b(?:calle|avenida|av\\.?|boulevard|blvd\\.?|carretera|privada|domicilio|colonia|fraccionamiento)\\s+[^,.!?;\\n]{2,60}",
    option = RegexOption.IGNORE_CASE,
)
