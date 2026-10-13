package com.example.rehabianex

import com.example.rehabianex.data.local.db.toDto
import com.example.rehabianex.data.local.db.toEntity
import com.example.rehabianex.data.model.LocalResourceDto
import com.example.rehabianex.data.model.SupportContact
import com.example.rehabianex.ui.screens.patient.selectHighestPriorityContact
import com.example.rehabianex.ui.screens.patient.shouldPreserveLegacySupportPriority
import com.example.rehabianex.ui.screens.patient.supportPriorityDescription
import com.example.rehabianex.ui.screens.patient.supportPriorityDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Stage6SupportToolsTest {
    @Test
    fun urgentSupportSelectsPriorityFiveBeforeLowerValues() {
        val contacts = listOf(
            contact("lower", "Contacto B", 1),
            contact("highest", "Contacto A", 5),
            contact("middle", "Contacto C", 3),
        )

        assertEquals("highest", selectHighestPriorityContact(contacts)?.id)
    }

    @Test
    fun urgentSupportUsesStableNameOrderForEqualPriorities() {
        val contacts = listOf(
            contact("z", "Zeta", 5),
            contact("a", "Ana", 5),
        )

        assertEquals("a", selectHighestPriorityContact(contacts)?.id)
    }

    @Test
    fun urgentSupportHasNoRecipientWhenNetworkIsEmpty() {
        assertNull(selectHighestPriorityContact(emptyList()))
    }

    @Test
    fun legacyPriorityDoesNotOverrideValidHighestPriority() {
        val contacts = listOf(
            contact("legacy", "Histórico", 10),
            contact("valid", "Actual", 5),
        )

        assertEquals("valid", selectHighestPriorityContact(contacts)?.id)
        assertEquals(
            "Prioridad histórica 10; selecciona 1–5 para cambiarla",
            supportPriorityDescription(10),
        )
    }

    @Test
    fun urgentSupportPrefersCurrentScaleOverLegacyPriority() {
        val contacts = listOf(
            contact("legacy", "Contacto histórico", 10),
            contact("current", "Contacto actual", 5),
        )

        assertEquals("current", selectHighestPriorityContact(contacts)?.id)
        assertEquals(
            "Prioridad histórica 10; selecciona 1–5 para cambiarla",
            supportPriorityDescription(10),
        )
    }

    @Test
    fun unchangedLegacyPriorityIsPreservedButExplicitCurrentPriorityIsValidated() {
        assertEquals(
            true,
            shouldPreserveLegacySupportPriority("contact-1", 8, "8"),
        )
        assertEquals(
            false,
            shouldPreserveLegacySupportPriority("contact-1", 8, "5"),
        )
        assertEquals(
            false,
            shouldPreserveLegacySupportPriority(null, 8, "8"),
        )
    }

    @Test
    fun directoryMetadataSurvivesOfflineRoundTrip() {
        val dto = LocalResourceDto(
            resourceId = "resource-1",
            name = "Recurso sintético",
            sourceName = "Fuente sintética",
            sourceUrl = "https://example.invalid/fuente",
            reviewedAt = "2026-10-12T12:00:00Z",
            updatedAt = "2026-10-12T12:05:00Z",
        )

        val cached = requireNotNull(dto.toEntity(syncedAt = 10L)).toDto()

        assertEquals(dto.sourceName, cached.sourceName)
        assertEquals(dto.sourceUrl, cached.sourceUrl)
        assertEquals(dto.reviewedAt, cached.reviewedAt)
        assertEquals(dto.updatedAt, cached.updatedAt)
    }

    private fun contact(id: String, name: String, priority: Int) = SupportContact(
        id = id,
        name = name,
        relationship = "Apoyo",
        phone = "+525500000000",
        priority = priority,
        canReceiveAlerts = false,
    )
}
