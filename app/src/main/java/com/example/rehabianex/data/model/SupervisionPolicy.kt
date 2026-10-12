package com.example.rehabianex.data.model

data class ConsentScopeOption(
    val code: String,
    val label: String,
    val description: String,
)

object SupervisionPolicy {
    val consentScopes = listOf(
        ConsentScopeOption(
            code = "patient_notes",
            label = "Notas de seguimiento",
            description = "Notas diarias, niveles registrados y señales de riesgo.",
        ),
        ConsentScopeOption(
            code = "agenda_events",
            label = "Agenda",
            description = "Eventos y recordatorios de recuperación.",
        ),
        ConsentScopeOption(
            code = "support_contacts",
            label = "Contactos de apoyo",
            description = "Contactos que decidiste registrar para tu red de apoyo.",
        ),
        ConsentScopeOption(
            code = "ai_chat_summary",
            label = "Resumen para asistente IA",
            description = "Contexto autorizado para consultas nuevas del supervisor.",
        ),
        ConsentScopeOption(
            code = "patient_achievements",
            label = "Logros",
            description = "Avances y logros de recuperación.",
        ),
        ConsentScopeOption(
            code = "patient_phone",
            label = "Teléfono",
            description = "Número telefónico guardado en tu perfil.",
        ),
    )

    val consentScopeCodes: Set<String> = consentScopes.mapTo(linkedSetOf()) { it.code }

    fun normalizeScopes(scopes: Iterable<String>): List<String> {
        val requested = scopes.map { it.trim() }.toSet()
        return consentScopes.map(ConsentScopeOption::code).filter(requested::contains)
    }
}
