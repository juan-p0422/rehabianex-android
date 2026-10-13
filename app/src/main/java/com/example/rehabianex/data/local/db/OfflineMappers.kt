package com.example.rehabianex.data.local.db

import com.example.rehabianex.data.model.AchievementDto
import com.example.rehabianex.data.model.AgendaEventDto
import com.example.rehabianex.data.model.CreatePatientNoteRequest
import com.example.rehabianex.data.model.LocalResourceDto
import com.example.rehabianex.data.model.NotificationSettingsData
import com.example.rehabianex.data.model.NotificationSettingsDto
import com.example.rehabianex.data.model.PatientAchievementDto
import com.example.rehabianex.data.model.PatientNoteDto
import com.example.rehabianex.data.model.SupportContactDto
import com.example.rehabianex.data.model.SessionProfileDto
import com.example.rehabianex.data.model.resolvedPatientDisplayName
import java.time.OffsetDateTime
import java.util.UUID

fun PatientNoteDto.toEntity(ownerUid: String, syncedAt: Long): PatientNoteEntity? {
    val id = noteId.cleanId() ?: documentId.cleanId() ?: return null
    return PatientNoteEntity(
        cacheKey = ownerKey(ownerUid, id),
        serverId = id,
        ownerUid = ownerUid,
        mood = mood,
        moodScore = moodScore,
        anxietyLevel = anxietyLevel,
        cravingLevel = cravingLevel,
        energyLevel = energyLevel,
        sleepQuality = sleepQuality,
        hadRelapse = hadRelapse,
        triggers = triggers.orEmpty(),
        copingActions = copingActions.orEmpty(),
        noteText = noteText,
        aiRiskLevel = aiRiskLevel,
        aiRiskScore = aiRiskScore,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        clientMutationId = clientMutationId,
        syncState = PatientNoteSyncState.Synced,
        pendingSync = false,
        syncAttempts = 0,
        lastSyncError = null,
        lastSyncedAt = syncedAt,
    )
}

fun CreatePatientNoteRequest.toPendingEntity(
    ownerUid: String,
    nowIso: String = OffsetDateTime.now().toString(),
): PatientNoteEntity {
    val mutationId = clientMutationId
        ?.takeIf { runCatching { UUID.fromString(it) }.isSuccess }
        ?: UUID.randomUUID().toString()
    val localId = "local-$mutationId"
    return PatientNoteEntity(
        cacheKey = ownerKey(ownerUid, localId),
        serverId = null,
        ownerUid = ownerUid,
        mood = mood,
        moodScore = moodScore,
        anxietyLevel = anxietyLevel,
        cravingLevel = cravingLevel,
        energyLevel = energyLevel,
        sleepQuality = sleepQuality,
        hadRelapse = hadRelapse,
        triggers = triggers,
        copingActions = copingActions,
        noteText = noteText,
        aiRiskLevel = null,
        aiRiskScore = null,
        createdAt = nowIso,
        updatedAt = nowIso,
        deletedAt = null,
        clientMutationId = mutationId,
        syncState = PatientNoteSyncState.Pending,
        pendingSync = true,
        syncAttempts = 0,
        lastSyncError = null,
        lastSyncedAt = null,
    )
}

fun PatientNoteEntity.toDto(): PatientNoteDto = PatientNoteDto(
    documentId = serverId ?: cacheKey.substringAfter(':', cacheKey),
    noteId = serverId ?: cacheKey.substringAfter(':', cacheKey),
    patientUid = ownerUid,
    mood = mood,
    moodScore = moodScore,
    anxietyLevel = anxietyLevel,
    cravingLevel = cravingLevel,
    energyLevel = energyLevel,
    sleepQuality = sleepQuality,
    hadRelapse = hadRelapse,
    triggers = triggers,
    copingActions = copingActions,
    noteText = noteText,
    aiRiskLevel = aiRiskLevel,
    aiRiskScore = aiRiskScore,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    clientMutationId = clientMutationId,
)

fun PatientNoteEntity.toCreateRequest(): CreatePatientNoteRequest = CreatePatientNoteRequest(
    mood = mood.orEmpty(),
    moodScore = moodScore ?: 5,
    anxietyLevel = anxietyLevel ?: 0,
    cravingLevel = cravingLevel ?: 0,
    energyLevel = energyLevel ?: 5,
    sleepQuality = sleepQuality ?: 5,
    hadRelapse = hadRelapse ?: false,
    triggers = triggers,
    copingActions = copingActions,
    noteText = noteText.orEmpty(),
    clientMutationId = clientMutationId,
)

fun SessionProfileDto.toSafeProfileEntity(
    ownerUid: String,
    sessionDisplayName: String?,
    syncedAt: Long,
): PatientProfileEntity {
    val anonymous = isAnonymous == true
    val privateMode = privacyMode == true || anonymous
    val safeNickname = nickname.cleanProfileText()
    val allowedFullName = fullName.cleanProfileText().takeUnless { privateMode }
    val allowedSessionName = sessionDisplayName.cleanProfileText().takeUnless { privateMode }
    val displayName = resolvedPatientDisplayName(allowedSessionName).cleanProfileText()
        ?: "Usuario RehabiAnex"
    return PatientProfileEntity(
        ownerUid = ownerUid,
        displayName = displayName,
        nickname = safeNickname,
        fullName = allowedFullName,
        privacyMode = privateMode,
        isAnonymous = anonymous,
        sobrietyStartDate = sobrietyStartDate.cleanProfileText(),
        photoUrl = photoUrl.cleanProfileText(),
        lastSyncedAt = syncedAt,
        isStale = false,
    )
}

fun PatientProfileEntity.toSessionProfileDto(): SessionProfileDto = SessionProfileDto(
    uid = ownerUid,
    fullName = fullName,
    displayName = displayName,
    safeDisplayName = displayName,
    nickname = nickname,
    role = "patient",
    status = "active",
    isAnonymous = isAnonymous,
    privacyMode = privacyMode,
    photoUrl = photoUrl,
    sobrietyStartDate = sobrietyStartDate,
)

fun AgendaEventDto.toEntity(ownerUid: String, syncedAt: Long): AgendaEventEntity? {
    val id = eventId.cleanId() ?: documentId.cleanId() ?: return null
    return AgendaEventEntity(
        cacheKey = ownerKey(ownerUid, id),
        serverId = id,
        ownerUid = ownerUid,
        type = type,
        title = title,
        startsAt = startsAt,
        endsAt = endsAt,
        location = location,
        status = status,
        attendanceReported = attendanceReported,
        attendanceReportedAt = attendanceReportedAt,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        lastSyncedAt = syncedAt,
    )
}

fun AgendaEventEntity.toDto(): AgendaEventDto = AgendaEventDto(
    documentId = serverId,
    eventId = serverId,
    patientUid = ownerUid,
    type = type,
    title = title,
    startsAt = startsAt,
    endsAt = endsAt,
    location = location,
    status = status,
    attendanceReported = attendanceReported,
    attendanceReportedAt = attendanceReportedAt,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun SupportContactDto.toEntity(ownerUid: String, syncedAt: Long): SupportContactEntity? {
    val resolvedId = supportContactId.cleanId() ?: documentId.cleanId() ?: id.cleanId() ?: return null
    return SupportContactEntity(
        cacheKey = ownerKey(ownerUid, resolvedId),
        serverId = resolvedId,
        ownerUid = ownerUid,
        name = name,
        relationship = relationship,
        phone = phone,
        priority = priority,
        canReceiveAlerts = canReceiveAlerts,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        lastSyncedAt = syncedAt,
    )
}

fun SupportContactEntity.toDto(): SupportContactDto = SupportContactDto(
    id = serverId,
    documentId = serverId,
    supportContactId = serverId,
    patientUid = ownerUid,
    name = name,
    relationship = relationship,
    phone = phone,
    priority = priority,
    canReceiveAlerts = canReceiveAlerts,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun NotificationSettingsDto.toEntity(ownerUid: String, syncedAt: Long): NotificationSettingsEntity {
    val resolvedId = settingsId.cleanId()
        ?: notificationSettingId.cleanId()
        ?: documentId.cleanId()
        ?: id.cleanId()
    return NotificationSettingsEntity(
        cacheKey = ownerKey(ownerUid, resolvedId ?: "settings"),
        serverId = resolvedId,
        ownerUid = ownerUid,
        dailyNoteEnabled = dailyNoteEnabled ?: dailyCheckInEnabled ?: true,
        dailyNoteTime = dailyNoteTime.cleanTime() ?: dailyCheckInTime.cleanTime() ?: "20:00",
        soberDayEnabled = soberDayEnabled ?: true,
        soberDayTime = soberDayTime.cleanTime() ?: "09:00",
        achievementEnabled = achievementEnabled ?: true,
        achievementTime = achievementTime.cleanTime() ?: "09:30",
        eventRemindersEnabled = eventRemindersEnabled ?: true,
        motivationalEnabled = motivationalEnabled ?: true,
        motivationalTime = motivationalTime.cleanTime() ?: "18:00",
        cravingAlertsEnabled = cravingAlertsEnabled ?: true,
        supervisorAlertsEnabled = supervisorAlertsEnabled ?: true,
        supportContactAlertsEnabled = supportContactAlertsEnabled ?: true,
        timezone = timezone?.takeIf(String::isNotBlank) ?: "America/Mexico_City",
        lastSyncedAt = syncedAt,
    )
}

fun NotificationSettingsEntity.toUi(): NotificationSettingsData = NotificationSettingsData(
    id = serverId,
    dailyNoteEnabled = dailyNoteEnabled,
    dailyNoteTime = dailyNoteTime,
    soberDayEnabled = soberDayEnabled,
    soberDayTime = soberDayTime,
    achievementEnabled = achievementEnabled,
    achievementTime = achievementTime,
    eventRemindersEnabled = eventRemindersEnabled,
    motivationalEnabled = motivationalEnabled,
    motivationalTime = motivationalTime,
    cravingAlertsEnabled = cravingAlertsEnabled,
    supervisorAlertsEnabled = supervisorAlertsEnabled,
    supportContactAlertsEnabled = supportContactAlertsEnabled,
)

fun NotificationSettingsData.toEntity(
    ownerUid: String,
    syncedAt: Long,
): NotificationSettingsEntity = NotificationSettingsEntity(
    cacheKey = ownerKey(ownerUid, id ?: "settings"),
    serverId = id,
    ownerUid = ownerUid,
    dailyNoteEnabled = dailyNoteEnabled,
    dailyNoteTime = dailyNoteTime,
    soberDayEnabled = soberDayEnabled,
    soberDayTime = soberDayTime,
    achievementEnabled = achievementEnabled,
    achievementTime = achievementTime,
    eventRemindersEnabled = eventRemindersEnabled,
    motivationalEnabled = motivationalEnabled,
    motivationalTime = motivationalTime,
    cravingAlertsEnabled = cravingAlertsEnabled,
    supervisorAlertsEnabled = supervisorAlertsEnabled,
    supportContactAlertsEnabled = supportContactAlertsEnabled,
    timezone = "America/Mexico_City",
    lastSyncedAt = syncedAt,
)

fun LocalResourceDto.toEntity(syncedAt: Long): LocalResourceEntity? {
    val id = resourceId.cleanId() ?: documentId.cleanId() ?: return null
    return LocalResourceEntity(
        resourceId = id,
        name = name,
        type = type,
        city = city,
        address = address,
        phone = phone,
        meetingSchedule = meetingSchedule.orEmpty(),
        tags = tags.orEmpty(),
        sourceName = sourceName,
        sourceUrl = sourceUrl,
        reviewedAt = reviewedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        lastSyncedAt = syncedAt,
    )
}

fun LocalResourceEntity.toDto(): LocalResourceDto = LocalResourceDto(
    documentId = resourceId,
    resourceId = resourceId,
    name = name,
    type = type,
    city = city,
    address = address,
    phone = phone,
    meetingSchedule = meetingSchedule,
    tags = tags,
    sourceName = sourceName,
    sourceUrl = sourceUrl,
    reviewedAt = reviewedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun AchievementDto.toEntity(syncedAt: Long): AchievementEntity? {
    val id = achievementId.cleanId() ?: documentId.cleanId() ?: return null
    return AchievementEntity(
        achievementId = id,
        title = title,
        description = description,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        lastSyncedAt = syncedAt,
    )
}

fun AchievementEntity.toDto(): AchievementDto = AchievementDto(
    documentId = achievementId,
    achievementId = achievementId,
    title = title,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun PatientAchievementDto.toEntity(ownerUid: String, syncedAt: Long): PatientAchievementEntity? {
    val id = patientAchievementId.cleanId() ?: documentId.cleanId() ?: return null
    return PatientAchievementEntity(
        cacheKey = ownerKey(ownerUid, id),
        serverId = id,
        ownerUid = ownerUid,
        achievementId = achievementId,
        awardedAt = awardedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        lastSyncedAt = syncedAt,
    )
}

fun PatientAchievementEntity.toDto(): PatientAchievementDto = PatientAchievementDto(
    documentId = serverId,
    patientAchievementId = serverId,
    patientUid = ownerUid,
    achievementId = achievementId,
    awardedAt = awardedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun cacheStateKey(ownerUid: String, resource: String): String = ownerKey(ownerUid, resource)

private fun ownerKey(ownerUid: String, resourceId: String): String = "$ownerUid:$resourceId"

private fun String?.cleanId(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun String?.cleanTime(): String? = this?.trim()?.takeIf {
    Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(it)
}

private fun String?.cleanProfileText(): String? = this
    ?.filterNot { it.isISOControl() }
    ?.replace("<", "")
    ?.replace(">", "")
    ?.trim()
    ?.take(500)
    ?.takeIf { it.isNotBlank() }
