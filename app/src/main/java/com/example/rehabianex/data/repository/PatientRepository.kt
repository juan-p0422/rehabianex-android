package com.example.rehabianex.data.repository

import com.example.rehabianex.core.error.ErrorMapper
import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.core.session.SessionInvalidationBus
import com.example.rehabianex.core.session.SessionRefreshCoordinator
import com.example.rehabianex.core.session.SessionRefreshResult
import com.example.rehabianex.data.local.PatientIdentityStorage
import com.example.rehabianex.data.local.NetworkStatusProvider
import com.example.rehabianex.data.local.db.CacheSyncStateEntity
import com.example.rehabianex.data.local.db.PatientSummaryEntity
import com.example.rehabianex.data.local.db.PatientProfileEntity
import com.example.rehabianex.data.local.db.PatientNoteSyncState
import com.example.rehabianex.data.local.db.RehabiAnexDatabase
import com.example.rehabianex.data.local.db.cacheStateKey
import com.example.rehabianex.data.local.db.toDto
import com.example.rehabianex.data.local.db.toEntity
import com.example.rehabianex.data.local.db.toPendingEntity
import com.example.rehabianex.data.local.db.toCreateRequest
import com.example.rehabianex.data.local.db.toSafeProfileEntity
import com.example.rehabianex.data.local.db.toSessionProfileDto
import com.example.rehabianex.data.local.db.toUi
import com.example.rehabianex.data.model.AgendaEvent
import com.example.rehabianex.data.model.AgendaEventDto
import com.example.rehabianex.data.model.AchievementDto
import com.example.rehabianex.data.model.AchievementItem
import com.example.rehabianex.data.model.AchievementsData
import com.example.rehabianex.data.model.ANONYMOUS_PATIENT_DISPLAY_NAME
import com.example.rehabianex.data.model.ConsentDto
import com.example.rehabianex.data.model.CreateAgendaEventRequest
import com.example.rehabianex.data.model.CreatePatientNoteRequest
import com.example.rehabianex.data.model.CreateConsentRequest
import com.example.rehabianex.data.model.CreateSupervisionRequest
import com.example.rehabianex.data.model.CreateSupportContactRequest
import com.example.rehabianex.data.model.LocalResource
import com.example.rehabianex.data.model.LocalResourceDto
import com.example.rehabianex.data.model.MetricsData
import com.example.rehabianex.data.model.NotificationSettingsData
import com.example.rehabianex.data.model.NotificationSettingsDto
import com.example.rehabianex.data.model.OfflineStatus
import com.example.rehabianex.data.model.PatientAchievementDto
import com.example.rehabianex.data.model.PatientHomeData
import com.example.rehabianex.data.model.PatientNoteDto
import com.example.rehabianex.data.model.PatientNotesResponse
import com.example.rehabianex.data.model.PatientProfileData
import com.example.rehabianex.data.model.PatientSupervisionState
import com.example.rehabianex.data.model.ResourceListResponse
import com.example.rehabianex.data.model.SaveNotificationSettingsRequest
import com.example.rehabianex.data.model.SessionProfileDto
import com.example.rehabianex.data.model.SupportContact
import com.example.rehabianex.data.model.SupportContactDto
import com.example.rehabianex.data.model.SupervisionRequestDto
import com.example.rehabianex.data.model.SupervisorCodePreview
import com.example.rehabianex.data.model.UpdateAgendaEventRequest
import com.example.rehabianex.data.model.UpdateConsentRequest
import com.example.rehabianex.data.model.UpdatePatientRequest
import com.example.rehabianex.data.model.UpdateSupervisionRequest
import com.example.rehabianex.data.model.isPatientPrivacyRestricted
import com.example.rehabianex.data.model.resolvedPatientDisplayName
import com.example.rehabianex.data.remote.ApiErrorParser
import com.example.rehabianex.data.remote.ApiService
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import retrofit2.Response

sealed interface PatientDataResult<out T> {
    data class Success<T>(
        val data: T,
        val offlineStatus: OfflineStatus = OfflineStatus(),
    ) : PatientDataResult<T>
    data class Failure(
        val message: String,
        val statusCode: Int? = null,
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : PatientDataResult<Nothing>
}

class PatientRepository(
    private val apiService: ApiService,
    private val sessionManager: SessionManager,
    private val identityStore: PatientIdentityStorage? = null,
    private val database: RehabiAnexDatabase? = null,
    private val networkStatusProvider: NetworkStatusProvider? = null,
    private val schedulePendingSync: ((String) -> Unit)? = null,
    private val sessionRefreshCoordinator: SessionRefreshCoordinator =
        SessionRefreshCoordinator(apiService, sessionManager),
) {
    suspend fun saveDailyNote(
        request: CreatePatientNoteRequest,
    ): PatientDataResult<String> {
        val uid = runCatching { sessionUid() }.getOrElse {
            return PatientDataResult.Failure("No hay una sesión activa. Inicia sesión nuevamente.")
        }
        if (database != null && networkStatusProvider?.isOnline() == false) {
            database.patientNoteDao().upsert(request.toPendingEntity(uid))
            schedulePendingSync?.invoke(uid)
            return PatientDataResult.Success(
                data = "Registro guardado en este dispositivo. Se sincronizará al recuperar conexión.",
                offlineStatus = OfflineStatus(
                    isOffline = true,
                    pendingSyncCount = database.patientNoteDao().pendingCount(uid),
                    warning = "Cambio pendiente de sincronizar.",
                ),
            )
        }

        return safeRequest(
            defaultUnexpectedMessage = "Ocurrió un error inesperado al guardar el registro.",
        ) {
        val currentProfile = loadProfileOrNull()
        val response = authorizedCall {
            apiService.createPatientNote(
                patientUid = uid,
                request = request,
            )
        }

        if (response.isSuccessful) {
            response.body()?.resolvedNote()?.let { note ->
                cacheNotes(
                    uid,
                    listOf(note.withRequestFallback(request, uid)),
                )
            }
            val baseMessage = response.body()?.message.cleanText()
                ?: "Registro guardado correctamente."
            val profileMessage = updatePatientAfterDailyNote(request, currentProfile)
            listOfNotNull(baseMessage, profileMessage).joinToString(" ")
        } else {
            throw EarlyFailureException(
                response.toPatientFailure(),
            )
        }
        }
    }

    suspend fun loadCachedHomeData(): PatientDataResult.Success<PatientHomeData>? {
        val uid = sessionUid()
        val notes = cachedNotes(uid)
        val profile = cachedProfile(uid)
        if (notes.isEmpty() && profile == null) return null
        return PatientDataResult.Success(
            data = buildHomeData(profile, notes),
            offlineStatus = cachedStatus(uid, NOTES_RESOURCE, isRefreshing = true),
        )
    }

    suspend fun loadHomeData(): PatientDataResult<PatientHomeData> {
        val cached = loadCachedHomeData()
        val remote = safeRequest {
        val uid = sessionUid()
        syncPendingPatientNotes(uid)
        val profile = loadProfileOrNull()
        val notes = authorizedCall {
            apiService.patientNotesByPatient(uid)
        }
            .readNotes()
            .getOrReturnFailure()
            .sortedByDescending { it.createdAt.orEmpty() }
        cacheProfile(profile)
        cacheNotes(uid, notes)
        refreshNoteTombstones(uid)
        buildHomeData(profile, mergedCachedNotes(uid, notes))
        }
        return remote.withOfflineFallback(cached)
    }

    suspend fun loadCachedMetricsData(): PatientDataResult.Success<MetricsData>? {
        val uid = sessionUid()
        val notes = cachedNotes(uid)
        val profile = cachedProfile(uid)
        if (notes.isEmpty() && profile == null) return null
        return PatientDataResult.Success(
            data = buildMetricsData(profile, notes),
            offlineStatus = cachedStatus(uid, NOTES_RESOURCE, isRefreshing = true),
        )
    }

    suspend fun loadMetricsData(): PatientDataResult<MetricsData> {
        val cached = loadCachedMetricsData()
        val remote = safeRequest {
        val uid = sessionUid()
        syncPendingPatientNotes(uid)
        val profile = loadProfileOrNull()
        val notes = authorizedCall {
            apiService.patientNotesByPatient(uid)
        }
            .readNotes()
            .getOrReturnFailure()
            .sortedByDescending { it.createdAt.orEmpty() }
        cacheProfile(profile)
        cacheNotes(uid, notes)
        refreshNoteTombstones(uid)
        buildMetricsData(profile, mergedCachedNotes(uid, notes))
        }
        return remote.withOfflineFallback(cached)
    }

    suspend fun loadCachedAchievementsData(): PatientDataResult.Success<AchievementsData>? {
        val db = database ?: return null
        val uid = sessionUid()
        val catalog = db.achievementDao().active().map { it.toDto() }
        val awarded = db.patientAchievementDao().activeForOwner(uid).map { it.toDto() }
        val profile = cachedProfile(uid)
        if (catalog.isEmpty() && awarded.isEmpty() && profile == null &&
            !hasCacheState(uid, ACHIEVEMENTS_RESOURCE)
        ) return null
        return PatientDataResult.Success(
            data = buildAchievementsData(profile, catalog, awarded),
            offlineStatus = cachedStatus(uid, ACHIEVEMENTS_RESOURCE, isRefreshing = true),
        )
    }

    suspend fun loadAchievementsData(): PatientDataResult<AchievementsData> {
        val cached = loadCachedAchievementsData()
        val remote = safeRequest {
        val uid = sessionUid()
        val profile = loadProfileOrNull()
        val catalog = authorizedCall {
            apiService.achievements(includeDeleted = true)
        }.readItems().getOrReturnFailure()
        val awarded = authorizedCall {
            apiService.patientAchievements(patientUid = uid, includeDeleted = true)
        }.readItems().getOrReturnFailure()
        cacheProfile(profile)
        cacheAchievements(uid, catalog, awarded)
        buildAchievementsData(profile, catalog, awarded)
        }
        return remote.withOfflineFallback(cached)
    }

    suspend fun loadCachedProfileData(): PatientDataResult.Success<PatientProfileData>? {
        val db = database ?: return null
        val uid = sessionManager.getUid().cleanText() ?: return null
        db.patientProfileDao().markStale(uid)
        val cached = db.patientProfileDao().get(uid) ?: return null
        return PatientDataResult.Success(
            data = cachedProfileData(cached),
            offlineStatus = OfflineStatus(
                isRefreshing = true,
                lastSyncedAt = cached.lastSyncedAt,
            ),
        )
    }

    suspend fun loadProfileData(): PatientDataResult<PatientProfileData> {
        val result = safeRequest {
        val profile = readMeProfile()
        val supervisorUid = profile.supervisorUid.safeIdentifierOrNull().orEmpty()
        val supervisorName = loadSupervisorName(supervisorUid)
        val consent = loadCurrentConsentOrNull(supervisorUid)
        val pendingRequests = loadPendingSupervisionRequests()
        val supervisionState = profile.supervisionState(consent, pendingRequests)
        val identity = identityForProfile(profile)
        val registeredName = identity.first
        val nickname = identity.second
        val consentActive = consent.isActiveConsent()
        val displayName = profile.resolvedPatientDisplayName(registeredName).cleanText()
            ?: ANONYMOUS_PATIENT_DISPLAY_NAME
        val privacyText = when {
            profile.isAnonymous == true -> "Modo anónimo activo"
            profile.privacyMode == true -> "Modo de privacidad activo"
            !nickname.isNullOrBlank() -> "Nickname visible para tu cuenta"
            else -> "Nombre visible para tu cuenta"
        }
        PatientProfileData(
            initials = displayName.initials(),
            fullName = displayName,
            registeredName = registeredName,
            nickname = nickname,
            email = profile.email.cleanText() ?: "Sin correo registrado",
            phone = profile.phone.cleanText() ?: "Sin teléfono registrado",
            supervisorText = supervisionState.description(supervisorName, consentActive),
            supervisorUid = supervisorUid,
            consentId = consent?.resourceId(),
            consentActive = consentActive,
            privacyText = privacyText,
            isAnonymous = profile.isAnonymous == true,
            supervisionState = supervisionState,
            privacyMode = profile.privacyMode == true || profile.isAnonymous == true,
            sobrietyStartDate = profile.sobrietyStartDate.cleanText(),
            photoUrl = profile.photoUrl.cleanText(),
            isStale = false,
        )
        }
        if (result is PatientDataResult.Failure && result.statusCode in setOf(401, 403)) {
            invalidatePatientProfileCache(sessionManager.getUid())
        }
        return result
    }

    private fun cachedProfileData(entity: PatientProfileEntity): PatientProfileData {
        val displayName = entity.displayName.cleanText() ?: "Usuario RehabiAnex"
        val privacyText = when {
            entity.isAnonymous -> "Modo anónimo activo"
            !entity.nickname.isNullOrBlank() -> "Nickname visible para tu cuenta"
            else -> "Nombre visible para tu cuenta"
        }
        return PatientProfileData(
            initials = displayName.initials(),
            fullName = displayName,
            registeredName = entity.fullName.cleanText() ?: displayName,
            nickname = entity.nickname.cleanText(),
            email = sessionManager.getEmail().cleanText() ?: "Sin correo registrado",
            phone = "Se actualizará al conectar",
            supervisorText = "Actualizando el estado de supervisión…",
            supervisorUid = "",
            consentId = null,
            consentActive = false,
            privacyText = privacyText,
            isAnonymous = entity.isAnonymous,
            privacyMode = entity.privacyMode,
            sobrietyStartDate = entity.sobrietyStartDate,
            photoUrl = entity.photoUrl,
            isStale = true,
        )
    }

    private fun identityForProfile(profile: SessionProfileDto): Pair<String, String?> {
        val patientUid = profile.uid.cleanText() ?: sessionUid()
        val privacyRestricted = profile.isAnonymous == true || profile.privacyMode == true
        if (privacyRestricted) {
            if (profile.isAnonymous == true) {
                identityStore?.clearOwner(patientUid)
            } else {
                identityStore?.clearOriginalName(patientUid)
            }
            val safeNickname = profile.nickname.cleanText()
            val safeDisplayName = profile.resolvedPatientDisplayName().cleanText()
                ?: ANONYMOUS_PATIENT_DISPLAY_NAME
            return safeDisplayName to safeNickname
        }
        val storedIdentity = identityStore?.load(patientUid)
        val remoteName = profile.fullName.cleanText()
        val remoteNickname = profile.nickname.cleanText()
        val sessionName = sessionManager.getDisplayName().cleanText()
        val fallbackOriginalName = storedIdentity?.originalName
            ?: sessionName?.takeUnless { it.isAnonymousDisplayName() }
            ?: remoteName?.takeUnless { profile.isAnonymous == true || it.isAnonymousDisplayName() }
            ?: "Usuario RehabiAnex"
        val originalName = identityStore?.ensureOriginalName(patientUid, fallbackOriginalName)
            ?: fallbackOriginalName

        return originalName to (remoteNickname ?: storedIdentity?.nickname)
    }


    private fun String.isAnonymousDisplayName(): Boolean {
        val value = lowercase(Locale.getDefault())
        return value.contains("paciente anónimo") ||
            value.contains("paciente anonimo")
    }

    suspend fun loadCachedSupportContacts(): PatientDataResult.Success<List<SupportContact>>? {
        val db = database ?: return null
        val uid = sessionUid()
        val cached = db.supportContactDao().activeForOwner(uid)
        if (cached.isEmpty() && !hasCacheState(uid, CONTACTS_RESOURCE)) return null
        return PatientDataResult.Success(
            data = cached.mapNotNull { it.toDto().toSupportContact() }
                .sortedWith(compareBy<SupportContact> { it.priority }.thenBy { it.name.lowercase() }),
            offlineStatus = cachedStatus(uid, CONTACTS_RESOURCE, isRefreshing = true),
        )
    }

    suspend fun loadSupportContacts(): PatientDataResult<List<SupportContact>> {
        val cached = loadCachedSupportContacts()
        val remote = safeRequest {
        val uid = sessionUid()
        val contacts = authorizedCall {
            apiService.supportContacts(patientUid = uid, includeDeleted = true)
        }
            .readItems()
            .getOrReturnFailure()
        cacheContacts(uid, contacts)
        contacts
            .mapNotNull { it.toSupportContact() }
            .sortedWith(compareBy<SupportContact> { it.priority }.thenBy { it.name.lowercase() })
        }
        return remote.withOfflineFallback(cached)
    }

    suspend fun saveSupportContact(
        request: CreateSupportContactRequest,
        contactId: String? = null,
    ): PatientDataResult<String> = safeRequest(
        defaultUnexpectedMessage = "Ocurrió un error inesperado al guardar el contacto.",
    ) {
        val cleanContactId = contactId.safeIdentifierOrNull()
        val response = if (cleanContactId == null) {
            authorizedCall {
                apiService.createSupportContact(request)
            }
        } else {
            authorizedCall {
                apiService.updateSupportContact(
                    contactId = cleanContactId,
                    request = request,
                )
            }
        }

        if (response.isSuccessful) {
            response.body()?.data?.let { cacheContacts(sessionUid(), listOf(it)) }
            response.body()?.message.cleanText()
                ?: if (cleanContactId == null) {
                    "Contacto de apoyo guardado correctamente."
                } else {
                    "Contacto de apoyo actualizado correctamente."
                }
        } else {
            throw EarlyFailureException(
                response.toPatientFailure(),
            )
        }
    }

    suspend fun loadCachedLocalResources(
        city: String? = null,
        type: String? = null,
    ): PatientDataResult.Success<List<LocalResource>>? {
        val db = database ?: return null
        val uid = sessionUid()
        val resources = db.localResourceDao().active()
            .map { it.toDto() }
            .filter { city.isNullOrBlank() || it.city.equals(city, ignoreCase = true) }
            .filter { type.isNullOrBlank() || it.type.equals(type, ignoreCase = true) }
            .mapNotNull { it.toLocalResource() }
            .sortedWith(compareBy<LocalResource> { it.typeLabel }.thenBy { it.name.lowercase() })
        if (resources.isEmpty() && !hasCacheState(uid, LOCAL_RESOURCES_RESOURCE)) return null
        return PatientDataResult.Success(
            data = resources,
            offlineStatus = cachedStatus(uid, LOCAL_RESOURCES_RESOURCE, isRefreshing = true),
        )
    }

    suspend fun loadLocalResources(
        city: String? = null,
        type: String? = null,
    ): PatientDataResult<List<LocalResource>> {
        val cached = loadCachedLocalResources(city, type)
        val remote = safeRequest {
        authorizedCall {
            apiService.localResources(
                city = city?.cleanText(),
                type = type?.cleanText(),
                includeDeleted = true,
            )
        }
            .readItems()
            .getOrReturnFailure()
            .also { cacheLocalResources(it) }
            .mapNotNull { it.toLocalResource() }
            .sortedWith(compareBy<LocalResource> { it.typeLabel }.thenBy { it.name.lowercase() })
        }
        return remote.withOfflineFallback(cached)
    }

    suspend fun loadCachedAgendaEvents(): PatientDataResult.Success<List<AgendaEvent>>? {
        val db = database ?: return null
        val uid = sessionUid()
        val events = db.agendaEventDao().activeForOwner(uid)
            .mapNotNull { it.toDto().toAgendaEvent() }
            .sortedBy { it.startsAt }
        if (events.isEmpty() && !hasCacheState(uid, AGENDA_RESOURCE)) return null
        return PatientDataResult.Success(
            data = events,
            offlineStatus = cachedStatus(uid, AGENDA_RESOURCE, isRefreshing = true),
        )
    }

    suspend fun loadAgendaEvents(): PatientDataResult<List<AgendaEvent>> {
        val cached = loadCachedAgendaEvents()
        val remote = safeRequest {
        val uid = sessionUid()
        val events = authorizedCall {
            apiService.agendaEvents(patientUid = uid, includeDeleted = true)
        }
            .readItems()
            .getOrReturnFailure()
        cacheAgenda(uid, events)
        events
            .mapNotNull { it.toAgendaEvent() }
            .sortedBy { it.startsAt }
        }
        return remote.withOfflineFallback(cached)
    }

    suspend fun saveAgendaEvent(
        request: CreateAgendaEventRequest,
    ): PatientDataResult<String> = safeRequest(
        defaultUnexpectedMessage = "Ocurrió un error inesperado al guardar el evento.",
    ) {
        val response = authorizedCall {
            apiService.createAgendaEvent(request)
        }

        if (response.isSuccessful) {
            response.body()?.data?.let { cacheAgenda(sessionUid(), listOf(it)) }
            response.body()?.message.cleanText()
                ?: "Evento guardado correctamente."
        } else {
            throw EarlyFailureException(
                response.toPatientFailure(),
            )
        }
    }

    suspend fun updateAgendaEventStatus(
        eventId: String,
        status: String,
    ): PatientDataResult<String> = safeRequest(
        defaultUnexpectedMessage = "OcurriÃ³ un error inesperado al actualizar el evento.",
    ) {
        val cleanEventId = eventId.safeResourceId("evento")
        val cleanStatus = status.takeIf { it in VALID_EVENT_STATUSES }
            ?: throw EarlyFailureException(
                PatientDataResult.Failure("Estado de evento no vÃ¡lido."),
            )
        val response = authorizedCall {
            apiService.updateAgendaEvent(
                eventId = cleanEventId,
                request = UpdateAgendaEventRequest(cleanStatus),
            )
        }

        if (response.isSuccessful) {
            response.body()?.data?.let { cacheAgenda(sessionUid(), listOf(it)) }
            response.body()?.message.cleanText()
                ?: "Evento actualizado correctamente."
        } else {
            throw EarlyFailureException(
                response.toPatientFailure(),
            )
        }
    }

    suspend fun deleteAgendaEvent(
        eventId: String,
    ): PatientDataResult<String> = safeRequest(
        defaultUnexpectedMessage = "OcurriÃ³ un error inesperado al eliminar el evento.",
    ) {
        val cleanEventId = eventId.safeResourceId("evento")
        val response = authorizedCall {
            apiService.deleteAgendaEvent(cleanEventId)
        }

        if (response.isSuccessful) {
            response.body()?.message.cleanText()
                ?: "Evento eliminado correctamente."
        } else {
            throw EarlyFailureException(
                response.toPatientFailure(),
            )
        }
    }

    suspend fun requestSupervisorLink(
        supervisorUidInput: String,
    ): PatientDataResult<String> = safeRequest(
        defaultUnexpectedMessage = "Ocurrió un error inesperado al enviar la solicitud.",
    ) {
        val supervisorUid = supervisorUidInput.safeIdentifier()
        val profile = readMeProfile()
        val currentSupervisorUid = profile.supervisorUid.safeIdentifierOrNull()
        val currentConsent = currentSupervisorUid?.let { loadCurrentConsentOrNull(it) }
        profile.supervisorLinkBlockMessage(currentConsent)?.let { message ->
            throw EarlyFailureException(PatientDataResult.Failure(message))
        }
        clearStalePendingLinkRequests(supervisorUid)
        if (loadPendingSupervisionRequests().any {
                it.supervisorUid.cleanText() == supervisorUid && it.isLinkRequest()
            }
        ) {
            throw EarlyFailureException(
                PatientDataResult.Failure("Ya tienes una solicitud pendiente. Espera la respuesta del supervisor antes de enviar otra."),
            )
        }

        val requestResponse = authorizedCall {
            apiService.createSupervisionRequest(
                CreateSupervisionRequest(
                    supervisorUid = supervisorUid,
                    message = "Solicito apoyo para seguimiento autorizado en RehabiAnex.",
                    type = SUPERVISION_REQUEST_TYPE_LINK,
                ),
            )
        }

        if (!requestResponse.isSuccessful) {
            throw EarlyFailureException(
                requestResponse.toPatientFailure(),
            )
        }

        refreshPatientSupervision()

        requestResponse.body()?.message.cleanText()
            ?: "Solicitud enviada. Quedará pendiente hasta que el supervisor la acepte."
    }

    suspend fun resolveSupervisorCode(
        codeInput: String,
    ): PatientDataResult<SupervisorCodePreview> = safeRequest(
        defaultUnexpectedMessage = "No fue posible verificar el código del supervisor.",
    ) {
        val code = codeInput
            .cleanText()
            ?.uppercase(Locale.ROOT)
            ?: throw EarlyFailureException(
                PatientDataResult.Failure("Escribe un código de supervisor válido."),
            )
        if (!SUPERVISOR_CODE_REGEX.matches(code)) {
            throw EarlyFailureException(
                PatientDataResult.Failure("El código debe usar el formato RA-XXXXXXXX.", 422),
            )
        }

        val response = authorizedCall {
            apiService.resolveSupervisorCode(code)
        }
        if (response.isSuccessful) {
        val supervisor = response.body()?.resolvedSupervisor()
                ?: throw EarlyFailureException(
                    PatientDataResult.Failure("Supervisor no disponible.", 404),
                )
            val supervisorUid = supervisor.uid.safeIdentifierOrNull()
            if (supervisorUid == null || supervisor.available != true) {
                throw EarlyFailureException(
                    PatientDataResult.Failure("Supervisor no disponible.", 404),
                )
            }

            SupervisorCodePreview(
                code = code,
                supervisorUid = supervisorUid,
                fullName = supervisor.fullName.cleanText() ?: "Supervisor RehabiAnex",
                supervisorType = supervisor.supervisorType.supervisorTypePreviewLabel(),
                available = true,
            )
        } else if (response.code() == 404) {
            throw EarlyFailureException(
                PatientDataResult.Failure(
                    "Supervisor no encontrado o no disponible.",
                    response.code(),
                ),
            )
        } else {
            throw EarlyFailureException(
                response.toPatientFailure(),
            )
        }
    }

    suspend fun setSupervisorConsent(
        enabled: Boolean,
    ): PatientDataResult<String> = safeRequest(
        defaultUnexpectedMessage = "Ocurrió un error inesperado al actualizar el consentimiento.",
    ) {
        val profile = readMeProfile()
        val supervisorUid = profile.supervisorUid.safeIdentifierOrNull()
            ?: throw EarlyFailureException(
                PatientDataResult.Failure("Primero vincula un supervisor para administrar consentimiento."),
            )
        val currentConsent = loadCurrentConsentOrNull(supervisorUid)
        val currentConsentId = currentConsent?.resourceId()

        val response = if (currentConsentId != null) {
            authorizedCall {
                apiService.updateConsent(
                    consentId = currentConsentId,
                    request = UpdateConsentRequest(
                        status = if (enabled) CONSENT_STATUS_ACTIVE else CONSENT_STATUS_PAUSED,
                    ),
                )
            }
        } else {
            if (!enabled) {
                return@safeRequest "No tienes un consentimiento activo para revocar."
            }
            authorizedCall {
                apiService.createConsent(
                    CreateConsentRequest(
                        supervisorUid = supervisorUid,
                        type = "supervision_and_alerts",
                        explicitConsent = true,
                        consentText = "Autorizo que mi supervisor consulte mis registros de recuperación según los alcances seleccionados.",
                        scope = DEFAULT_CONSENT_SCOPE,
                    ),
                )
            }
        }

        if (response.isSuccessful) {
            refreshPatientSupervision()
            if (enabled) {
                "Consentimiento activado correctamente."
            } else {
                "Acceso puesto en privado. Tu supervisor sabrá que pausaste el seguimiento, pero no podrá consultar tus registros."
            }
        } else {
            throw EarlyFailureException(
                response.toPatientFailure(),
            )
        }
    }

    suspend fun revokeSupervisorConsent(): PatientDataResult<String> = safeRequest(
        defaultUnexpectedMessage = "Ocurrió un error inesperado al revocar el consentimiento.",
    ) {
        val profile = readMeProfile()
        val supervisorUid = profile.supervisorUid.safeIdentifierOrNull()
            ?: throw EarlyFailureException(
                PatientDataResult.Failure("No tienes un supervisor vinculado con consentimiento para revocar."),
            )
        val currentConsentId = loadCurrentConsentOrNull(supervisorUid)?.resourceId()
            ?: throw EarlyFailureException(
                PatientDataResult.Failure("No existe un consentimiento vigente para revocar."),
            )
        val response = authorizedCall {
            apiService.updateConsent(
                consentId = currentConsentId,
                request = UpdateConsentRequest(explicitConsent = false),
            )
        }

        if (!response.isSuccessful) {
            throw EarlyFailureException(
                response.toPatientFailure(),
            )
        }

        refreshPatientSupervision()
        "Consentimiento revocado. Tu supervisor ya no puede consultar los datos compartidos."
    }

    suspend fun requestSupervisorUnlink(): PatientDataResult<String> = safeRequest(
        defaultUnexpectedMessage = "No fue posible enviar la solicitud de desvinculación.",
    ) {
        val profile = readMeProfile()
        val supervisorUid = profile.supervisorUid.safeIdentifierOrNull()
            ?: throw EarlyFailureException(
                PatientDataResult.Failure("No tienes un supervisor vinculado para solicitar desvinculación."),
            )
        if (loadPendingSupervisionRequests().any {
                it.supervisorUid.cleanText() == supervisorUid && it.isUnlinkRequest()
            }
        ) {
            throw EarlyFailureException(
                PatientDataResult.Failure("Ya tienes una solicitud de desvinculación pendiente. Espera la respuesta del supervisor antes de enviar otra."),
            )
        }

        val response = authorizedCall {
            apiService.createSupervisionRequest(
                CreateSupervisionRequest(
                    supervisorUid = null,
                    message = "Solicito desvincularme del seguimiento en RehabiAnex.",
                    type = SUPERVISION_REQUEST_TYPE_UNLINK,
                ),
            )
        }

        if (response.isSuccessful) {
            refreshPatientSupervision()
            response.body()?.message.cleanText()
                ?: "Solicitud de desvinculación enviada. Tu supervisor podrá aceptarla o rechazarla."
        } else {
            throw EarlyFailureException(
                response.toPatientFailure(),
            )
        }
    }

    suspend fun updatePrivacyMode(
        displayName: String,
        anonymous: Boolean,
        useNickname: Boolean,
    ): PatientDataResult<String> {
        if (networkStatusProvider?.isOnline() == false) {
            return PatientDataResult.Failure(
                "Necesitas conexión para actualizar la privacidad del perfil.",
            )
        }
        return safeRequest(
            defaultUnexpectedMessage = "Ocurrió un error inesperado al actualizar privacidad.",
        ) {
        val cleanName = displayName.cleanText()
        if (!anonymous && cleanName.isNullOrBlank()) {
            throw EarlyFailureException(
                PatientDataResult.Failure("Escribe un nombre o nickname válido."),
            )
        }
        val patientUid = sessionUid()
        val cachedBeforeUpdate = cachedProfile(patientUid)
        val requestedFullName = cleanName
            ?.takeUnless { useNickname || anonymous || it.isAnonymousDisplayName() }
        val response = authorizedCall {
            apiService.updatePatient(
                patientUid = patientUid,
                request = UpdatePatientRequest(
                    fullName = requestedFullName,
                    nickname = when {
                        anonymous -> null
                        useNickname -> cleanName
                        else -> ""
                    },
                    isAnonymous = anonymous,
                    privacyMode = anonymous || useNickname,
                ),
            )
        }

        if (response.isSuccessful) {
            val remoteProfile = response.body()?.data
            val sourceProfile = remoteProfile ?: cachedBeforeUpdate ?: SessionProfileDto(uid = patientUid)
            val persistedProfile = sourceProfile.copy(
                uid = remoteProfile?.uid.cleanText() ?: patientUid,
                fullName = when {
                    anonymous || useNickname -> null
                    !remoteProfile?.fullName.cleanText().isNullOrBlank() -> remoteProfile?.fullName
                    !requestedFullName.isNullOrBlank() -> requestedFullName
                    else -> cachedBeforeUpdate?.fullName
                },
                displayName = remoteProfile?.displayName
                    ?: if (anonymous) ANONYMOUS_PATIENT_DISPLAY_NAME else cleanName,
                safeDisplayName = remoteProfile?.safeDisplayName
                    ?: if (anonymous) ANONYMOUS_PATIENT_DISPLAY_NAME else cleanName,
                nickname = if (useNickname) cleanName else null,
                isAnonymous = anonymous,
                privacyMode = anonymous || useNickname,
                sobrietyStartDate = remoteProfile?.sobrietyStartDate
                    ?: cachedBeforeUpdate?.sobrietyStartDate,
                photoUrl = remoteProfile?.photoUrl ?: cachedBeforeUpdate?.photoUrl,
            )
            when {
                anonymous -> identityStore?.clearOwner(patientUid)
                useNickname -> {
                    identityStore?.clearOriginalName(patientUid)
                    identityStore?.saveNickname(patientUid, cleanName.orEmpty())
                }
                else -> {
                    identityStore?.clearNickname(patientUid)
                    persistedProfile.fullName.cleanText()?.let { visibleName ->
                        identityStore?.ensureOriginalName(patientUid, visibleName)
                    }
                }
            }
            cacheProfile(persistedProfile)
            "Privacidad actualizada correctamente."
        } else {
            if (response.code() in setOf(401, 403)) {
                invalidatePatientProfileCache(patientUid)
            }
            throw EarlyFailureException(
                response.toPatientFailure(
                    fallbackMessage = if (response.code() == 422) {
                        "Revisa los datos del perfil e inténtalo nuevamente."
                    } else {
                        null
                    },
                ),
            )
        }
    }

    suspend fun updateOwnProfile(
        fullName: String,
        phone: String,
    ): PatientDataResult<String> {
        if (networkStatusProvider?.isOnline() == false) {
            return PatientDataResult.Failure(
                "Necesitas conexión para editar nombre o teléfono. El cambio no se guardó en la cola.",
            )
        }
        return safeRequest(
            defaultUnexpectedMessage = "Ocurrió un error inesperado al actualizar el perfil.",
        ) {
            val patientUid = sessionUid()
            val response = authorizedCall {
                apiService.updatePatient(
                    patientUid = patientUid,
                    request = UpdatePatientRequest(
                        fullName = fullName,
                        phone = phone,
                    ),
                )
            }
            if (!response.isSuccessful) {
                throw EarlyFailureException(response.toPatientFailure())
            }

            val profile = response.body()?.data ?: SessionProfileDto(
                uid = patientUid,
                role = "patient",
                fullName = fullName,
                phone = phone,
                email = sessionManager.getEmail(),
            )
            cacheProfile(profile)
            response.body()?.message.cleanText() ?: "Perfil actualizado correctamente."
        }
    }

    /**
     * El login llama esta purga antes de persistir una sesión privada. Reemplaza
     * cualquier snapshot identificable del mismo UID sin tocar tokens ni notas.
     */
    suspend fun restrictPrivateIdentityFromLogin(
        patientUid: String,
        profile: SessionProfileDto?,
        safeDisplayName: String,
        isAnonymous: Boolean,
    ) {
        if (isAnonymous) {
            identityStore?.clearOwner(patientUid)
        } else {
            identityStore?.clearOriginalName(patientUid)
            profile?.nickname.cleanText()?.let { identityStore?.saveNickname(patientUid, it) }
        }
        val db = database ?: return
        val previous = db.patientProfileDao().get(patientUid)
        val now = System.currentTimeMillis()
        val restricted = (profile ?: previous?.toSessionProfileDto() ?: SessionProfileDto())
            .copy(
                uid = patientUid,
                fullName = null,
                displayName = safeDisplayName,
                safeDisplayName = safeDisplayName,
                nickname = if (isAnonymous) null else profile?.nickname,
                isAnonymous = isAnonymous,
                privacyMode = true,
            )
            .toSafeProfileEntity(patientUid, safeDisplayName, now)
            .copy(isStale = true)
        db.patientProfileDao().clearOwner(patientUid)
        db.patientSummaryDao().clearOwner(patientUid)
        db.patientProfileDao().upsert(restricted)
        db.patientSummaryDao().upsert(
            PatientSummaryEntity(
                ownerUid = patientUid,
                fullName = restricted.displayName,
                sobrietyStartDate = restricted.sobrietyStartDate,
                lastSyncedAt = now,
            ),
        )
    }

    suspend fun loadCachedNotificationSettings(): PatientDataResult.Success<NotificationSettingsData>? {
        val db = database ?: return null
        val uid = sessionUid()
        val cached = db.notificationSettingsDao().forOwner(uid) ?: return null
        return PatientDataResult.Success(
            data = cached.toUi(),
            offlineStatus = cachedStatus(uid, NOTIFICATIONS_RESOURCE, isRefreshing = true),
        )
    }

    suspend fun loadNotificationSettings(): PatientDataResult<NotificationSettingsData> {
        val cached = loadCachedNotificationSettings()
        val remote = safeRequest {
        val uid = sessionUid()
        val response = authorizedCall {
            apiService.notificationSettings()
        }

        if (!response.isSuccessful) {
            throw EarlyFailureException(
                response.toPatientFailure(),
            )
        }

        val dto = response.body()
            ?.data
            ?.maxByOrNull { it.updatedAt.cleanText() ?: it.createdAt.cleanText() ?: "" }
        val settings = dto.toNotificationSettingsData()
        val syncedAt = System.currentTimeMillis()
        database?.notificationSettingsDao()?.upsert(
            dto?.toEntity(uid, syncedAt) ?: settings.toEntity(uid, syncedAt),
        )
        markSynced(uid, NOTIFICATIONS_RESOURCE, syncedAt)
        settings
        }
        return remote.withOfflineFallback(cached)
    }

    suspend fun saveNotificationSettings(
        settings: NotificationSettingsData,
    ): PatientDataResult<NotificationSettingsData> = safeRequest(
        defaultUnexpectedMessage = "Ocurrió un error inesperado al guardar las notificaciones.",
    ) {
        val request = settings.toSaveRequest()
        val response = if (settings.id.isNullOrBlank()) {
            authorizedCall {
                apiService.createNotificationSettings(request)
            }
        } else {
            authorizedCall {
                apiService.updateNotificationSettings(
                    settingsId = settings.id,
                    request = request,
                )
            }
        }

        if (response.isSuccessful) {
            val body = response.body()
            val saved = body?.data.toNotificationSettingsData(
                settings.copy(id = body?.id.cleanText() ?: settings.id),
            )
            val uid = sessionUid()
            val syncedAt = System.currentTimeMillis()
            database?.notificationSettingsDao()?.upsert(
                body?.data?.toEntity(uid, syncedAt) ?: saved.toEntity(uid, syncedAt),
            )
            markSynced(uid, NOTIFICATIONS_RESOURCE, syncedAt)
            saved
        } else {
            throw EarlyFailureException(
                response.toPatientFailure(
                    fallbackMessage = if (response.code() == 422) {
                        "Revisa que las horas de notificación sean válidas e inténtalo nuevamente."
                    } else {
                        null
                    },
                ),
            )
        }
    }

    private suspend fun loadProfileOrNull(): SessionProfileDto? {
        return runCatching { readMeProfile() }.getOrNull()
    }

    private fun loadSupervisorName(supervisorUid: String): String? {
        return if (supervisorUid.isBlank()) null else "Supervisor vinculado"
    }

    private suspend fun loadCurrentConsentOrNull(supervisorUid: String): ConsentDto? {
        if (supervisorUid.isBlank()) return null

        val response = authorizedCall {
            apiService.consents(
                patientUid = sessionUid(),
                supervisorUid = supervisorUid,
            )
        }
        if (!response.isSuccessful) {
            if (response.code() == 404) return null
            throw EarlyFailureException(
                response.toPatientFailure(),
            )
        }

        val matchingConsents = response.body()
            ?.data
            .orEmpty()
            .filter { consent ->
                consent.supervisorUid == supervisorUid &&
                    consent.type.cleanText() == "supervision_and_alerts"
            }
            .ifEmpty {
                response.body()?.data.orEmpty()
                    .filter { it.supervisorUid == supervisorUid }
            }

        return matchingConsents.maxWithOrNull(
            compareBy<ConsentDto> { it.updatedAt.cleanText().orEmpty() }
                .thenBy { it.consentStatePriority() },
        )
    }

    private suspend fun readMeProfile(): SessionProfileDto {
        val response = authorizedCall { apiService.me() }

        if (response.isSuccessful) {
            val profile = response.body()?.profile ?: throw EarlyFailureException(
                PatientDataResult.Failure("El servidor no devolvió el perfil de la cuenta."),
            )
            cacheProfile(profile)
            return profile
        }

        throw EarlyFailureException(
            response.toPatientFailure(),
        )
    }

    private suspend fun clearStalePendingLinkRequests(targetSupervisorUid: String) {
        val patientUid = sessionUid()
        val pendingResponse = authorizedCall {
            apiService.supervisionRequests(status = "pending", limit = 100)
        }
        if (!pendingResponse.isSuccessful) return

        val staleRequestIds = pendingResponse.body()?.data.orEmpty()
            .filter { request ->
                request.patientUid.cleanText() == patientUid &&
                    request.isLinkRequest() &&
                    request.supervisorUid.cleanText() != targetSupervisorUid
            }
            .mapNotNull { request -> request.resourceId() }

        if (staleRequestIds.isEmpty()) return

        var cancellationFailure: PatientDataResult.Failure? = null
        staleRequestIds.forEach { requestId ->
            val cancellationResponse = authorizedCall {
                apiService.updateSupervisionRequest(
                    requestId = requestId,
                    request = UpdateSupervisionRequest(status = CANCELLED_REQUEST_STATUS),
                )
            }
            if (!cancellationResponse.isSuccessful && cancellationResponse.code() != 404) {
                cancellationFailure = cancellationFailure ?: cancellationResponse.toPatientFailure(
                    fallbackMessage = cancellationResponse.supervisionCancellationFallbackMessage(),
                )
            }
        }

        val refreshedResponse = authorizedCall {
            apiService.supervisionRequests(status = "pending", limit = 100)
        }
        if (!refreshedResponse.isSuccessful) {
            throw EarlyFailureException(
                refreshedResponse.toPatientFailure(),
            )
        }

        cancellationFailure?.let { failure ->
            throw EarlyFailureException(failure)
        }
    }

    private suspend fun loadPendingSupervisionRequests(): List<SupervisionRequestDto> {
        val response = authorizedCall {
            apiService.supervisionRequests(status = "pending", limit = 100)
        }
        if (!response.isSuccessful) {
            throw EarlyFailureException(
                response.toPatientFailure(),
            )
        }
        val patientUid = sessionUid()
        return response.body()?.data.orEmpty()
            .filter { it.patientUid.cleanText() == patientUid }
    }

    private suspend fun refreshPatientSupervision() {
        val profile = readMeProfile()
        loadPendingSupervisionRequests()
        profile.supervisorUid.safeIdentifierOrNull()?.let { loadCurrentConsentOrNull(it) }
    }

    private fun SupervisionRequestDto.isUnlinkRequest(): Boolean {
        val officialType = type.cleanText()?.lowercase(Locale.ROOT)
            ?: requestType.cleanText()?.lowercase(Locale.ROOT)
        if (officialType != null) return officialType == SUPERVISION_REQUEST_TYPE_UNLINK

        // Compatibilidad exclusiva para documentos históricos sin type.
        val legacyMessage = message.cleanText()?.lowercase(Locale.getDefault()).orEmpty()
        return legacyMessage.contains("solicitud_desvinculacion") ||
            legacyMessage.contains("desvincul") ||
            legacyMessage.contains("cortar seguimiento")
    }

    private fun SupervisionRequestDto.isLinkRequest(): Boolean = !isUnlinkRequest()

    private fun SessionProfileDto.supervisorLinkBlockMessage(currentConsent: ConsentDto?): String? {
        val currentSupervisorUid = supervisorUid.safeIdentifierOrNull() ?: return null
        val status = supervisionStatus.cleanText()?.lowercase(Locale.getDefault())
        val consentIsActive = currentConsent.isActiveConsent()

        return when {
            status in ACTIVE_SUPERVISION_STATUSES && consentIsActive ->
                "Ya tienes un supervisor vinculado. Si necesitas cambiarlo, administra tu consentimiento desde Perfil."
            status in ACTIVE_SUPERVISION_STATUSES ->
                "Ya tienes un supervisor vinculado. Administra su consentimiento desde Perfil."
            status in PENDING_SUPERVISION_STATUSES ->
                "Ya tienes una solicitud pendiente. Espera la respuesta del supervisor antes de enviar otra."
            status in REJECTED_SUPERVISION_STATUSES -> null
            currentSupervisorUid.isNotBlank() ->
                "Ya existe una vinculación de supervisor en tu cuenta. Revísala desde Perfil antes de enviar otra solicitud."
            else -> null
        }
    }

    private suspend fun updatePatientAfterDailyNote(
        request: CreatePatientNoteRequest,
        currentProfile: SessionProfileDto?,
    ): String? {
        val sobrietyStartDate = when {
            request.hadRelapse -> nowIso()
            currentProfile?.sobrietyStartDate.isNullOrBlank() -> todayAtStartIso()
            else -> null
        }
        val primaryRisks = request.triggers
            .takeIf { it.isNotEmpty() }
            ?.let { currentProfile?.primaryRisks.mergePrimaryRisks(it) }

        if (sobrietyStartDate == null && primaryRisks == null) {
            return null
        }

        val response = authorizedCall {
            apiService.updatePatient(
                patientUid = sessionUid(),
                request = UpdatePatientRequest(
                    sobrietyStartDate = sobrietyStartDate,
                    primaryRisks = primaryRisks,
                ),
            )
        }

        return if (response.isSuccessful) {
            "Tu progreso también se actualizó en el perfil."
        } else {
            "La nota se guardó, pero no se pudo actualizar tu perfil: ${response.patientUpdateErrorMessage()}"
        }
    }

    private fun sessionUid(): String {
        return sessionManager.getUid()
            ?.takeIf { it.isNotBlank() }
            ?: throw MissingSessionException()
    }

    private fun String.safeIdentifier(): String {
        val cleanValue = cleanText()?.take(128)
            ?: throw EarlyFailureException(
                PatientDataResult.Failure("Escribe el código del supervisor."),
            )

        val hasUnsafeCharacters = cleanValue.any { char ->
            char.isWhitespace() || char == '/' || char == '\\' || char == '?' || char == '#'
        }
        if (hasUnsafeCharacters) {
            throw EarlyFailureException(
                PatientDataResult.Failure("El código del supervisor contiene caracteres no permitidos."),
            )
        }

        return cleanValue
    }

    private fun String?.safeIdentifierOrNull(): String? {
        val cleanValue = cleanText()?.take(128) ?: return null
        val hasUnsafeCharacters = cleanValue.any { char ->
            char.isWhitespace() || char == '/' || char == '\\' || char == '?' || char == '#'
        }

        return cleanValue.takeUnless { hasUnsafeCharacters }
    }

    private fun String?.supervisorTypePreviewLabel(): String {
        return when (cleanText()?.lowercase(Locale.getDefault())) {
            "clinical_psychologist" -> "Psicología clínica"
            "addiction_specialist" -> "Especialista en adicciones"
            "social_worker" -> "Trabajo social"
            "support_sponsor" -> "Padrino o madrina de apoyo"
            null -> "Supervisor autorizado"
            else -> cleanText()
                ?.replace("_", " ")
                ?.replace("-", " ")
                ?.capitalizeFirst()
                ?: "Supervisor autorizado"
        }
    }

    private fun String.safeResourceId(label: String): String {
        val cleanValue = cleanText()?.take(160)
            ?: throw EarlyFailureException(
                PatientDataResult.Failure("No se encontró el identificador del $label."),
            )

        val hasUnsafeCharacters = cleanValue.any { char ->
            char.isWhitespace() || char == '/' || char == '\\' || char == '?' || char == '#'
        }
        if (hasUnsafeCharacters) {
            throw EarlyFailureException(
                PatientDataResult.Failure("El identificador del $label contiene caracteres no permitidos."),
            )
        }

        return cleanValue
    }

    suspend fun syncPendingPatientNotes(ownerUid: String): PatientNoteSyncBatchResult {
        val db = database ?: return PatientNoteSyncBatchResult()
        val currentUid = sessionManager.getUid()?.takeIf { it.isNotBlank() }
        if (currentUid != ownerUid || sessionManager.getRole() != "patient") {
            return PatientNoteSyncBatchResult(ownerMismatch = true)
        }

        db.patientNoteDao().recoverInterruptedSync(
            ownerUid,
            "La sincronización anterior se interrumpió y se intentará nuevamente.",
        )
        val ready = db.patientNoteDao().readyForSync(ownerUid)
        if (networkStatusProvider?.isOnline() == false) {
            return PatientNoteSyncBatchResult(retryableCount = ready.size)
        }

        var result = PatientNoteSyncBatchResult()
        ready.filter { canAutomaticallyRetry(it.syncState, it.clientMutationId) }.forEach { pending ->
            val request = pending.toCreateRequest()
            val syncing = pending.copy(
                syncState = PatientNoteSyncState.Syncing,
                pendingSync = true,
                syncAttempts = pending.syncAttempts + 1,
                lastSyncError = null,
                updatedAt = nowIso(),
            )
            db.patientNoteDao().upsert(syncing)

            try {
                val response = authorizedCall {
                    apiService.createPatientNote(ownerUid, request)
                }
                if (response.isSuccessful) {
                    val remote = response.body()?.resolvedNote()
                    if (remote?.noteId.cleanText() != null || remote?.documentId.cleanText() != null) {
                        db.patientNoteDao().deleteByCacheKey(pending.cacheKey)
                        cacheNotes(ownerUid, listOf(remote!!.withRequestFallback(request, ownerUid)))
                        try {
                            updatePatientAfterDailyNote(request, loadProfileOrNull())
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            // La nota ya fue confirmada; el perfil se revalidará en el siguiente refresh.
                        }
                        result = result.copy(syncedCount = result.syncedCount + 1)
                    } else {
                        db.patientNoteDao().upsert(
                            syncing.copy(
                                syncState = PatientNoteSyncState.Ambiguous,
                                lastSyncError = "El servidor recibió la solicitud, pero no confirmó la nota. Se verificará con la misma clave segura.",
                            ),
                        )
                        result = result.copy(
                            ambiguousCount = result.ambiguousCount + 1,
                            idempotentAmbiguousCount = result.idempotentAmbiguousCount +
                                if (pending.clientMutationId.isNullOrBlank()) 0 else 1,
                        )
                    }
                } else {
                    val failure = response.toPatientFailure()
                    when (classifyNoteSyncHttpStatus(response.code())) {
                        NoteSyncDisposition.Retryable -> {
                            db.patientNoteDao().upsert(
                                syncing.copy(
                                    syncState = PatientNoteSyncState.RetryableError,
                                    lastSyncError = failure.message,
                                ),
                            )
                            result = result.copy(retryableCount = result.retryableCount + 1)
                        }
                        NoteSyncDisposition.Permanent,
                        NoteSyncDisposition.Ambiguous,
                        -> {
                            db.patientNoteDao().upsert(
                                syncing.copy(
                                    syncState = PatientNoteSyncState.PermanentError,
                                    lastSyncError = failure.message,
                                ),
                            )
                            result = result.copy(permanentErrorCount = result.permanentErrorCount + 1)
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                when (classifyNoteSyncFailure(error, !pending.clientMutationId.isNullOrBlank())) {
                    NoteSyncDisposition.Retryable -> {
                        db.patientNoteDao().upsert(
                            syncing.copy(
                                syncState = PatientNoteSyncState.RetryableError,
                                lastSyncError = "No se pudo sincronizar todavía. Se intentará nuevamente con conexión.",
                            ),
                        )
                        result = result.copy(retryableCount = result.retryableCount + 1)
                    }
                    NoteSyncDisposition.Ambiguous -> {
                        db.patientNoteDao().upsert(
                            syncing.copy(
                                syncState = PatientNoteSyncState.Ambiguous,
                                lastSyncError = "No se pudo confirmar el envío. Revisa la nota antes de reintentar.",
                            ),
                        )
                        result = result.copy(ambiguousCount = result.ambiguousCount + 1)
                    }
                    NoteSyncDisposition.Permanent -> Unit
                }
            }
        }
        return result
    }

    private suspend fun cachedNotes(uid: String): List<PatientNoteDto> {
        return database?.patientNoteDao()?.activeForOwner(uid)
            .orEmpty()
            .map { it.toDto() }
            .sortedByDescending { it.createdAt.orEmpty() }
    }

    private suspend fun mergedCachedNotes(
        uid: String,
        remote: List<PatientNoteDto>,
    ): List<PatientNoteDto> {
        if (database == null) return remote
        return cachedNotes(uid)
    }

    private suspend fun cacheNotes(uid: String, notes: List<PatientNoteDto>) {
        val db = database ?: return
        val syncedAt = System.currentTimeMillis()
        val entities = notes.mapNotNull { it.toEntity(uid, syncedAt) }
        if (entities.isNotEmpty()) db.patientNoteDao().upsert(entities)
        markSynced(uid, NOTES_RESOURCE, syncedAt)
    }

    private suspend fun refreshNoteTombstones(uid: String) {
        if (database == null) return
        try {
            val response = authorizedCall {
                apiService.patientNotesForSync(patientUid = uid, includeDeleted = true)
            }
            if (response.isSuccessful) {
                cacheNotes(uid, response.body()?.data.orEmpty())
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // El listado oficial de UI ya se obtuvo; el feed de tombstones se reintenta en otro refresh.
        }
    }

    private suspend fun cacheContacts(uid: String, contacts: List<SupportContactDto>) {
        val db = database ?: return
        val syncedAt = System.currentTimeMillis()
        val entities = contacts.mapNotNull { it.toEntity(uid, syncedAt) }
        if (entities.isNotEmpty()) db.supportContactDao().upsert(entities)
        markSynced(uid, CONTACTS_RESOURCE, syncedAt)
    }

    private suspend fun cacheAgenda(uid: String, events: List<AgendaEventDto>) {
        val db = database ?: return
        val syncedAt = System.currentTimeMillis()
        val entities = events.mapNotNull { it.toEntity(uid, syncedAt) }
        if (entities.isNotEmpty()) db.agendaEventDao().upsert(entities)
        markSynced(uid, AGENDA_RESOURCE, syncedAt)
    }

    private suspend fun cacheLocalResources(resources: List<LocalResourceDto>) {
        val db = database ?: return
        val syncedAt = System.currentTimeMillis()
        val entities = resources.mapNotNull { it.toEntity(syncedAt) }
        if (entities.isNotEmpty()) db.localResourceDao().upsert(entities)
        markSynced(sessionUid(), LOCAL_RESOURCES_RESOURCE, syncedAt)
    }

    private suspend fun cacheAchievements(
        uid: String,
        catalog: List<AchievementDto>,
        awarded: List<PatientAchievementDto>,
    ) {
        val db = database ?: return
        val syncedAt = System.currentTimeMillis()
        val catalogEntities = catalog.mapNotNull { it.toEntity(syncedAt) }
        val awardedEntities = awarded.mapNotNull { it.toEntity(uid, syncedAt) }
        if (catalogEntities.isNotEmpty()) db.achievementDao().upsert(catalogEntities)
        if (awardedEntities.isNotEmpty()) db.patientAchievementDao().upsert(awardedEntities)
        markSynced(uid, ACHIEVEMENTS_RESOURCE, syncedAt)
    }

    private suspend fun cacheProfile(profile: SessionProfileDto?) {
        val uid = profile?.uid.cleanText() ?: sessionManager.getUid().cleanText() ?: return
        val safeProfile = (profile ?: SessionProfileDto(uid = uid)).toSafeProfileEntity(
            ownerUid = uid,
            sessionDisplayName = sessionManager.getDisplayName(),
            syncedAt = System.currentTimeMillis(),
        )
        persistPatientSessionDisplayName(safeProfile.displayName)
        val db = database ?: return
        if (safeProfile.isAnonymous || safeProfile.privacyMode) {
            db.patientProfileDao().clearOwner(uid)
            db.patientSummaryDao().clearOwner(uid)
        }
        db.patientProfileDao().upsert(safeProfile)
        db.patientSummaryDao().upsert(
            PatientSummaryEntity(
                ownerUid = uid,
                fullName = safeProfile.displayName,
                sobrietyStartDate = safeProfile.sobrietyStartDate,
                lastSyncedAt = safeProfile.lastSyncedAt,
            ),
        )
        if (safeProfile.isAnonymous || safeProfile.privacyMode) {
            identityStore?.clearOriginalName(uid)
        }
    }

    private fun persistPatientSessionDisplayName(displayName: String) {
        val session = sessionManager.getCurrentSession() ?: return
        if (session.role != "patient" || session.displayName == displayName) return
        sessionManager.saveSession(session.copy(displayName = displayName))
    }

    private suspend fun cachedProfile(uid: String): SessionProfileDto? {
        database?.patientProfileDao()?.get(uid)?.let { return it.toSessionProfileDto() }
        val summary = database?.patientSummaryDao()?.get(uid) ?: return null
        return SessionProfileDto(
            uid = uid,
            fullName = summary.fullName,
            sobrietyStartDate = summary.sobrietyStartDate,
            role = "patient",
            status = "active",
        )
    }

    private fun buildHomeData(
        profile: SessionProfileDto?,
        notes: List<PatientNoteDto>,
    ): PatientHomeData {
        val latestNote = notes.firstOrNull()
        return PatientHomeData(
            firstName = profile.firstName(),
            soberDays = profile.soberDaysText(),
            dailyStatus = if (notes.hasTodayNote()) "Completado" else "Pendiente",
            recentState = latestNote?.recentState() ?: "Sin registro",
        )
    }

    private fun buildMetricsData(
        profile: SessionProfileDto?,
        notes: List<PatientNoteDto>,
    ): MetricsData {
        val recentNotes = notes.take(7)
        return MetricsData(
            soberDays = profile.soberDaysText(),
            weeklyAnxiety = recentNotes.averageScale5 { anxietyLevel },
            weeklyCraving = recentNotes.averageScale5 { cravingLevel },
            averageEnergy = recentNotes.averageScale5 { energyLevel },
            averageSleep = recentNotes.averageScale5 { sleepQuality },
            predominantMood = recentNotes.predominantMood(),
            chartValues = recentNotes.take(5).reversed()
                .map { ((it.anxietyLevel ?: 0).coerceIn(0, 10) / 10f) },
            insight = recentNotes.insight(),
            recordsCount = recentNotes.size,
            relapseCount = recentNotes.count { it.hadRelapse == true },
            riskSummary = recentNotes.riskSummary(),
        )
    }

    private fun buildAchievementsData(
        profile: SessionProfileDto?,
        catalog: List<AchievementDto>,
        awarded: List<PatientAchievementDto>,
    ): AchievementsData {
        val soberDays = profile.soberDaysValue()
        val items = (soberDays.toSobrietyAchievementItems() + awarded.toAchievementItems(catalog))
            .distinctBy { it.id }
        return AchievementsData(
            soberDays = profile.soberDaysText(),
            achievements = items,
            nextHint = soberDays.nextAchievementHint(),
        )
    }

    private suspend fun cachedStatus(
        uid: String,
        resource: String,
        isRefreshing: Boolean,
    ): OfflineStatus {
        val lastSyncedAt = database?.cacheSyncStateDao()
            ?.get(cacheStateKey(uid, resource))
            ?.lastSyncedAt
        val pending = if (resource == NOTES_RESOURCE) {
            database?.patientNoteDao()?.pendingCount(uid) ?: 0
        } else {
            0
        }
        return OfflineStatus(
            isOffline = false,
            isRefreshing = isRefreshing,
            pendingSyncCount = pending,
            lastSyncedAt = lastSyncedAt,
            warning = if (resource == NOTES_RESOURCE) {
                database?.patientNoteDao()?.latestActionRequired(uid)?.lastSyncError
            } else {
                null
            },
        )
    }

    private suspend fun markSynced(uid: String, resource: String, syncedAt: Long) {
        database?.cacheSyncStateDao()?.upsert(
            CacheSyncStateEntity(
                cacheKey = cacheStateKey(uid, resource),
                ownerUid = uid,
                resource = resource,
                lastSyncedAt = syncedAt,
            ),
        )
    }

    private suspend fun hasCacheState(uid: String, resource: String): Boolean {
        return database?.cacheSyncStateDao()?.get(cacheStateKey(uid, resource)) != null
    }

    private fun <T> PatientDataResult<T>.withOfflineFallback(
        cached: PatientDataResult.Success<T>?,
    ): PatientDataResult<T> = when (this) {
        is PatientDataResult.Success -> copy(
            offlineStatus = (cached?.offlineStatus ?: OfflineStatus()).copy(
                isOffline = false,
                isRefreshing = false,
                warning = cached?.offlineStatus?.warning,
            ),
        )
        is PatientDataResult.Failure -> if (
            statusCode == null || statusCode == 429 || statusCode in 500..599
        ) {
            cached?.copy(
                offlineStatus = cached.offlineStatus.copy(
                    isOffline = true,
                    isRefreshing = false,
                    warning = message,
                ),
            ) ?: this
        } else {
            this
        }
    }

    private fun PatientNoteDto.withRequestFallback(
        request: CreatePatientNoteRequest,
        uid: String,
    ): PatientNoteDto = copy(
        patientUid = patientUid ?: uid,
        mood = mood ?: request.mood,
        moodScore = moodScore ?: request.moodScore,
        anxietyLevel = anxietyLevel ?: request.anxietyLevel,
        cravingLevel = cravingLevel ?: request.cravingLevel,
        energyLevel = energyLevel ?: request.energyLevel,
        sleepQuality = sleepQuality ?: request.sleepQuality,
        hadRelapse = hadRelapse ?: request.hadRelapse,
        triggers = triggers ?: request.triggers,
        copingActions = copingActions ?: request.copingActions,
        noteText = noteText ?: request.noteText,
        clientMutationId = clientMutationId ?: request.clientMutationId,
        createdAt = createdAt ?: nowIso(),
        updatedAt = updatedAt ?: nowIso(),
    )

    private suspend fun <T> authorizedCall(
        request: suspend () -> Response<T>,
    ): Response<T> {
        val rejectedIdToken = sessionManager.getIdToken()
        val firstResponse = request()
        if (firstResponse.code() != 401) {
            return firstResponse
        }

        when (val refresh = sessionRefreshCoordinator.refreshAfterUnauthorized(rejectedIdToken)) {
            SessionRefreshResult.Success -> Unit
            is SessionRefreshResult.Failure -> {
                if (refresh.invalidatesSession) {
                    invalidateSession()
                }
                throw EarlyFailureException(
                    PatientDataResult.Failure(
                        message = refresh.message,
                        statusCode = refresh.statusCode,
                    ),
                )
            }
        }

        val retryResponse = request()
        if (retryResponse.code() == 401) {
            invalidateSession()
            throw EarlyFailureException(
                PatientDataResult.Failure(
                    message = ErrorMapper.fromHttpCode(401).message,
                    statusCode = 401,
                ),
            )
        }
        return retryResponse
    }

    private suspend fun invalidateSession() {
        invalidatePatientProfileCache(sessionManager.getUid())
        sessionManager.clear()
        SessionInvalidationBus.notifySessionExpired()
    }

    private suspend fun invalidatePatientProfileCache(uid: String?) {
        if (uid.isNullOrBlank()) return
        runCatching { database?.patientProfileDao()?.clearOwner(uid) }
        runCatching { database?.patientSummaryDao()?.clearOwner(uid) }
        runCatching { identityStore?.clearOwner(uid) }
    }

    private suspend fun <T> safeRequest(
        defaultUnexpectedMessage: String = "Ocurrió un error inesperado al cargar tus datos.",
        block: suspend () -> T,
    ): PatientDataResult<T> {
        return try {
            PatientDataResult.Success(block())
        } catch (error: EarlyFailureException) {
            error.failure
        } catch (error: MissingSessionException) {
            PatientDataResult.Failure("No hay una sesión activa. Inicia sesión nuevamente.")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            PatientDataResult.Failure(
                ErrorMapper.fromThrowable(
                    error = error,
                    connectionMessage = "Sin conexión. Revisa tu red e inténtalo nuevamente.",
                    unexpectedMessage = defaultUnexpectedMessage,
                ).message,
            )
        }
    }

    private fun <T> Response<ResourceListResponse<T>>.readItems(): PatientDataResult<List<T>> {
        if (isSuccessful) {
            return PatientDataResult.Success(body()?.data.orEmpty())
        }

        return toPatientFailure()
    }

    private fun Response<PatientNotesResponse>.readNotes(): PatientDataResult<List<PatientNoteDto>> {
        if (isSuccessful) {
            return PatientDataResult.Success(body()?.resolvedNotes().orEmpty())
        }

        return toPatientFailure()
    }

    private fun Response<*>.toPatientFailure(
        fallbackMessage: String? = null,
    ): PatientDataResult.Failure {
        val parsedError = ApiErrorParser.parse(errorBody(), code())
        val message = ErrorMapper.safeBackendMessage(code(), parsedError.message)
            ?: fallbackMessage
            ?: ErrorMapper.fromHttpCode(code()).message
        return PatientDataResult.Failure(
            message = message,
            statusCode = code(),
            fieldErrors = parsedError.fieldErrors,
        )
    }

    private fun Response<*>.supervisionCancellationFallbackMessage(): String {
        return when (code()) {
            403 -> "No tienes permiso para cancelar esta solicitud."
            404 -> "La solicitud pendiente ya no está disponible."
            409 -> "La solicitud ya cambió de estado. Actualiza la información e inténtalo nuevamente."
            422 -> "No fue posible cancelar la solicitud pendiente. Revisa su estado e inténtalo nuevamente."
            else -> ErrorMapper.fromHttpCode(code()).message
        }
    }

    private fun Response<*>.patientUpdateErrorMessage(): String {
        val parsedMessage = ApiErrorParser.parse(errorBody(), code()).message
        return ErrorMapper.safeBackendMessage(code(), parsedMessage) ?: if (code() == 422) {
            "Revisa los datos del perfil e inténtalo nuevamente."
        } else {
            ErrorMapper.fromHttpCode(code()).message
        }
    }

    private fun <T> PatientDataResult<T>.getOrReturnFailure(): T {
        return when (this) {
            is PatientDataResult.Success -> data
            is PatientDataResult.Failure -> throw EarlyFailureException(this)
        }
    }

    private fun SessionProfileDto?.firstName(): String {
        val safeName = this?.resolvedPatientDisplayName(sessionManager.getDisplayName())
            ?.cleanText()
            ?: sessionManager.getDisplayName().cleanText()
            ?: "Usuario"
        if (this?.isPatientPrivacyRestricted() == true || safeName.isAnonymousDisplayName()) {
            return safeName
        }
        return safeName
            .split(Regex("\\s+"))
            .firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.capitalizeFirst()
            ?: "Usuario"
    }

    private fun SessionProfileDto?.soberDaysText(): String {
        return soberDaysValue().formatOneDecimal()
    }

    private fun SessionProfileDto?.soberDaysValue(): Double {
        val startDateTime = this?.sobrietyStartDate?.toZonedDateTimeOrNull() ?: return 0.0
        val minutes = ChronoUnit.MINUTES
            .between(startDateTime, ZonedDateTime.now(ZoneId.systemDefault()))
            .coerceAtLeast(0)

        return minutes / MINUTES_PER_DAY
    }

    private fun List<PatientNoteDto>.hasTodayNote(): Boolean {
        val today = LocalDate.now()
        return any { note ->
            note.createdAt?.toLocalDateOrNull() == today
        }
    }

    private fun todayAtStartIso(): String {
        return LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toOffsetDateTime()
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    private fun nowIso(): String {
        return ZonedDateTime.now(ZoneId.systemDefault())
            .toOffsetDateTime()
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    private fun List<String>?.mergePrimaryRisks(
        newRisks: List<String>,
    ): List<String> {
        return (orEmpty() + newRisks)
            .mapNotNull { risk ->
                risk.cleanText()
                    ?.lowercase(Locale.getDefault())
            }
            .distinct()
            .take(MAX_RISKS)
    }

    private fun PatientNoteDto.recentState(): String {
        anxietyLevel?.let { anxiety ->
            return when {
                anxiety >= 7 -> "Ansiedad alta"
                anxiety >= 4 -> "Ansiedad moderada"
                anxiety >= 1 -> "Ansiedad baja"
                else -> mood.cleanText()?.capitalizeFirst() ?: "Estable"
            }
        }

        return mood.cleanText()?.capitalizeFirst() ?: "Sin registro"
    }

    private fun List<PatientNoteDto>.averageScale5(
        valueSelector: PatientNoteDto.() -> Int?,
    ): String {
        if (isEmpty()) return "0/5"

        val values = mapNotNull { it.valueSelector() }
        if (values.isEmpty()) return "0/5"

        val average = values.average()
        return "${(average / 2).roundToInt().coerceIn(0, 5)}/5"
    }

    private fun List<PatientNoteDto>.predominantMood(): String {
        if (isEmpty()) return "Sin registros"

        return mapNotNull { it.mood.cleanText()?.capitalizeFirst() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: "Sin registros"
    }

    private fun List<PatientNoteDto>.insight(): String? {
        if (isEmpty()) return null

        val highAnxietyCount = count { (it.anxietyLevel ?: 0) >= 7 }
        val relapseCount = count { it.hadRelapse == true }

        return when {
            relapseCount > 0 -> "Hay registros con recaída. Considera contactar a tu red de apoyo."
            highAnxietyCount > 0 -> "Hay registros con ansiedad alta esta semana."
            size < 3 -> "Aún hay pocos registros para detectar un patrón."
            else -> "No se detecta un patrón crítico en los registros recientes."
        }
    }

    private fun List<PatientNoteDto>.riskSummary(): String {
        if (isEmpty()) return "Sin datos suficientes"

        val averageAnxiety = mapNotNull { it.anxietyLevel }.averageOrZero()
        val averageCraving = mapNotNull { it.cravingLevel }.averageOrZero()
        val relapseCount = count { it.hadRelapse == true }

        return when {
            relapseCount > 0 -> "Atención: hubo recaída registrada"
            averageAnxiety >= 7 || averageCraving >= 7 -> "Riesgo alto"
            averageAnxiety >= 4 || averageCraving >= 4 -> "Riesgo moderado"
            else -> "Riesgo bajo"
        }
    }

    private fun List<Int>.averageOrZero(): Double {
        return if (isEmpty()) 0.0 else average()
    }

    private fun Double.toSobrietyAchievementItems(): List<AchievementItem> {
        return SOBRIETY_MILESTONES.map { milestone ->
            val unlocked = this >= milestone.daysRequired
            AchievementItem(
                id = milestone.id,
                title = milestone.title,
                description = milestone.description,
                awardedAt = if (unlocked) "Desbloqueado" else "Pendiente",
                isUnlocked = unlocked,
                progressText = if (unlocked) {
                    "¡Logrado!"
                } else {
                    "Faltan ${(milestone.daysRequired - this).coerceAtLeast(0.0).formatOneDecimal()} días"
                },
            )
        }
    }

    private fun Double.nextAchievementHint(): String {
        val nextMilestone = SOBRIETY_MILESTONES.firstOrNull { this < it.daysRequired }
        return if (nextMilestone == null) {
            "Has desbloqueado todos los logros principales. Sigue cuidando tu proceso."
        } else {
            "Próximo logro: ${nextMilestone.title}."
        }
    }

    private fun List<PatientAchievementDto>.toAchievementItems(
        catalog: List<AchievementDto>,
    ): List<AchievementItem> {
        val catalogById = catalog.associateBy { it.achievementId }

        return mapNotNull { patientAchievement ->
            val achievementId = patientAchievement.achievementId ?: return@mapNotNull null
            val achievement = catalogById[achievementId]
            AchievementItem(
                id = patientAchievement.patientAchievementId
                    ?: patientAchievement.documentId
                    ?: achievementId,
                title = achievement?.title.cleanText() ?: achievementId,
                description = achievement?.description.cleanText() ?: "Logro desbloqueado",
                awardedAt = patientAchievement.awardedAt.cleanText() ?: "",
            )
        }
    }

    private fun SupportContactDto.toSupportContact(): SupportContact? {
        val cleanName = name.cleanText()
        if (cleanName.isNullOrBlank()) return null

        return SupportContact(
            id = supportContactResourceId() ?: cleanName,
            name = cleanName,
            relationship = relationship.cleanText() ?: "Contacto de apoyo",
            phone = phone.cleanText().orEmpty(),
            priority = priority?.coerceIn(1, 10) ?: 5,
            canReceiveAlerts = canReceiveAlerts ?: false,
            notes = notes.cleanText().orEmpty(),
        )
    }

    private fun SupportContactDto.supportContactResourceId(): String? {
        return documentId.cleanText()
            ?: supportContactId.cleanText()
            ?: id.cleanText()
    }

    private fun LocalResourceDto.toLocalResource(): LocalResource? {
        val cleanName = name.cleanText()
        if (cleanName.isNullOrBlank()) return null

        val cleanType = type.cleanText()?.lowercase(Locale.getDefault()) ?: "resource"
        val scheduleText = meetingSchedule
            .orEmpty()
            .mapNotNull { it.cleanText() }
            .take(3)
            .joinToString(" · ")
        val tagsText = tags
            .orEmpty()
            .mapNotNull { it.cleanText() }
            .take(3)
            .joinToString(" · ")

        val detailParts = listOfNotNull(
            scheduleText.takeIf { it.isNotBlank() }?.let { "Horario: $it" },
            address.cleanText(),
            tagsText.takeIf { it.isNotBlank() },
        )

        return LocalResource(
            id = resourceId ?: documentId ?: cleanName,
            name = cleanName,
            type = cleanType,
            typeLabel = cleanType.localResourceTypeLabel(),
            city = city.cleanText() ?: "Sin ciudad",
            detail = detailParts.joinToString("\n").ifBlank { "Recurso de apoyo" },
            phone = phone.cleanText().orEmpty(),
        )
    }

    private fun AgendaEventDto.toAgendaEvent(): AgendaEvent? {
        val cleanTitle = title.cleanText()
        if (cleanTitle.isNullOrBlank()) return null

        val startsAtDate = startsAt?.toZonedDateTimeOrNull()
        val endsAtDate = endsAt?.toZonedDateTimeOrNull()
        val timeText = when {
            startsAtDate != null && endsAtDate != null ->
                "${startsAtDate.format(TimeFormatter)} - ${endsAtDate.format(TimeFormatter)}"
            startsAtDate != null -> startsAtDate.format(TimeFormatter)
            else -> "Sin hora"
        }

        return AgendaEvent(
            id = documentId ?: cleanTitle,
            title = cleanTitle,
            timeText = timeText,
            detail = location.cleanText()
                ?: notes.cleanText()
                ?: type.cleanText()?.typeLabel()
                ?: "Evento programado",
            startsAt = startsAt.orEmpty(),
            endsAt = endsAt.orEmpty(),
            durationMinutes = if (startsAtDate != null && endsAtDate != null) {
                ChronoUnit.MINUTES.between(startsAtDate, endsAtDate).toInt().coerceIn(30, 120)
            } else {
                60
            },
            dateLabel = startsAtDate?.format(DateLabelFormatter) ?: "Sin fecha",
            dayNumber = startsAtDate?.dayOfMonth ?: 0,
            typeLabel = type.cleanText()?.typeLabel() ?: "Evento",
        )
    }

    private fun ConsentDto.resourceId(): String? {
        return documentId.cleanText()
            ?: consentId.cleanText()
    }

    private fun ConsentDto?.isActiveConsent(): Boolean {
        if (this == null) return false
        val normalizedStatus = status.cleanText()?.lowercase(Locale.ROOT)
        return explicitConsent == true &&
            (normalizedStatus == null || normalizedStatus == CONSENT_STATUS_ACTIVE) &&
            scope.orEmpty().any { !it.cleanText().isNullOrBlank() }
    }

    private fun ConsentDto.consentStatePriority(): Int {
        return when (status.cleanText()?.lowercase(Locale.ROOT)) {
            CONSENT_STATUS_ACTIVE -> 3
            CONSENT_STATUS_PAUSED -> 2
            "revoked" -> 1
            else -> 0
        }
    }

    private fun SessionProfileDto.supervisionState(
        consent: ConsentDto?,
        pendingRequests: List<SupervisionRequestDto>,
    ): PatientSupervisionState {
        val hasPendingUnlink = pendingRequests.any { it.isUnlinkRequest() }
        val hasPendingLink = pendingRequests.any { it.isLinkRequest() }
        val linkedSupervisor = supervisorUid.safeIdentifierOrNull()
        val normalizedStatus = supervisionStatus.cleanText()?.lowercase(Locale.ROOT)
        val consentStatus = consent?.status.cleanText()?.lowercase(Locale.ROOT)

        return when {
            hasPendingUnlink -> PatientSupervisionState.UnlinkPending
            linkedSupervisor != null &&
                normalizedStatus in ACTIVE_SUPERVISION_STATUSES &&
                (consentStatus == CONSENT_STATUS_REVOKED ||
                    (consentStatus == null && consent?.explicitConsent == false)) ->
                PatientSupervisionState.Revoked
            linkedSupervisor != null &&
                normalizedStatus in ACTIVE_SUPERVISION_STATUSES &&
                consentStatus == CONSENT_STATUS_PAUSED ->
                PatientSupervisionState.Paused
            linkedSupervisor != null && normalizedStatus in ACTIVE_SUPERVISION_STATUSES ->
                PatientSupervisionState.Active
            hasPendingLink -> PatientSupervisionState.LinkPending
            normalizedStatus in REJECTED_SUPERVISION_STATUSES ->
                PatientSupervisionState.LinkRejected
            linkedSupervisor == null && !supervisionEndedAt.cleanText().isNullOrBlank() ->
                PatientSupervisionState.Unlinked
            else -> PatientSupervisionState.NoSupervisor
        }
    }

    private fun PatientSupervisionState.description(
        supervisorName: String?,
        consentActive: Boolean,
    ): String {
        val name = supervisorName ?: "Supervisor"
        return when (this) {
            PatientSupervisionState.NoSupervisor -> "Sin supervisor vinculado"
            PatientSupervisionState.LinkPending -> "Solicitud pendiente de respuesta del supervisor"
            PatientSupervisionState.LinkRejected ->
                "Solicitud rechazada; puedes intentar con otro supervisor"
            PatientSupervisionState.Active -> if (consentActive) {
                "$name · supervisión activa"
            } else {
                "$name · supervisión activa; falta activar el consentimiento"
            }
            PatientSupervisionState.Paused -> "$name · acceso pausado"
            PatientSupervisionState.Revoked -> "$name · consentimiento revocado"
            PatientSupervisionState.UnlinkPending -> "$name · desvinculación pendiente"
            PatientSupervisionState.Unlinked -> "Desvinculado"
        }
    }

    private fun SupervisionRequestDto.resourceId(): String? {
        return documentId.cleanText()
            ?: requestId.cleanText()
    }

    private fun NotificationSettingsDto?.toNotificationSettingsData(
        fallback: NotificationSettingsData? = null,
    ): NotificationSettingsData {
        return NotificationSettingsData(
            id = this?.notificationSettingsResourceId() ?: fallback?.id,
            dailyNoteEnabled = this?.dailyNoteEnabled
                ?: this?.dailyCheckInEnabled
                ?: fallback?.dailyNoteEnabled
                ?: true,
            dailyNoteTime = (this?.dailyNoteTime ?: this?.dailyCheckInTime)
                .cleanTimeOrDefault(fallback?.dailyNoteTime ?: "20:00"),
            soberDayEnabled = this?.soberDayEnabled ?: fallback?.soberDayEnabled ?: true,
            soberDayTime = this?.soberDayTime.cleanTimeOrDefault(fallback?.soberDayTime ?: "09:00"),
            achievementEnabled = this?.achievementEnabled ?: fallback?.achievementEnabled ?: true,
            achievementTime = this?.achievementTime.cleanTimeOrDefault(fallback?.achievementTime ?: "09:30"),
            eventRemindersEnabled = this?.eventRemindersEnabled ?: fallback?.eventRemindersEnabled ?: true,
            motivationalEnabled = this?.motivationalEnabled ?: fallback?.motivationalEnabled ?: false,
            motivationalTime = this?.motivationalTime.cleanTimeOrDefault(fallback?.motivationalTime ?: "18:00"),
            cravingAlertsEnabled = this?.cravingAlertsEnabled
                ?: fallback?.cravingAlertsEnabled
                ?: true,
            supervisorAlertsEnabled = this?.supervisorAlertsEnabled
                ?: fallback?.supervisorAlertsEnabled
                ?: true,
            supportContactAlertsEnabled = this?.supportContactAlertsEnabled
                ?: fallback?.supportContactAlertsEnabled
                ?: true,
        )
    }

    private fun NotificationSettingsDto.notificationSettingsResourceId(): String? {
        return settingsId.cleanText()
            ?: notificationSettingId.cleanText()
            ?: documentId.cleanText()
            ?: id.cleanText()
    }

    private fun NotificationSettingsData.toSaveRequest(): SaveNotificationSettingsRequest {
        return SaveNotificationSettingsRequest(
            dailyCheckInEnabled = dailyNoteEnabled,
            dailyCheckInTime = dailyNoteTime.cleanTimeOrDefault("20:00"),
            dailyNoteEnabled = dailyNoteEnabled,
            dailyNoteTime = dailyNoteTime.cleanTimeOrDefault("20:00"),
            soberDayEnabled = soberDayEnabled,
            soberDayTime = soberDayTime.cleanTimeOrDefault("09:00"),
            achievementEnabled = achievementEnabled,
            achievementTime = achievementTime.cleanTimeOrDefault("09:30"),
            eventRemindersEnabled = eventRemindersEnabled,
            motivationalEnabled = motivationalEnabled,
            motivationalTime = motivationalTime.cleanTimeOrDefault("18:00"),
            cravingAlertsEnabled = cravingAlertsEnabled,
            supervisorAlertsEnabled = supervisorAlertsEnabled,
            supportContactAlertsEnabled = supportContactAlertsEnabled,
            timezone = NOTIFICATION_TIMEZONE,
        )
    }

    private fun String?.cleanTimeOrDefault(defaultValue: String): String {
        val cleanValue = cleanText()?.take(5) ?: return defaultValue
        return if (TIME_REGEX.matches(cleanValue)) cleanValue else defaultValue
    }

    private fun String?.cleanText(): String? {
        return this
            ?.filterNot { it.isISOControl() }
            ?.replace("<", "")
            ?.replace(">", "")
            ?.trim()
            ?.take(MAX_TEXT_LENGTH)
            ?.takeIf { it.isNotBlank() }
    }

    private fun String?.initials(): String {
        val parts = cleanText()
            ?.split(Regex("\\s+"))
            ?.filter { it.isNotBlank() }
            .orEmpty()

        return parts
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
            .joinToString("")
            .ifBlank { "?" }
    }

    private fun String.capitalizeFirst(): String {
        return replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase() else char.toString()
        }
    }

    private fun Double.formatOneDecimal(): String {
        return String.format(Locale.getDefault(), "%.1f", this.coerceAtLeast(0.0))
    }

    private fun String.sha1Prefix(length: Int): String {
        val bytes = MessageDigest
            .getInstance("SHA-1")
            .digest(toByteArray())

        return bytes.joinToString("") { byte -> "%02x".format(byte) }
            .take(length)
            .uppercase(Locale.getDefault())
    }

    private fun String.typeLabel(): String {
        when (this) {
            "support_meeting" -> return "Reunión"
            "appointment" -> return "Cita"
            "group" -> return "Grupo"
            "call" -> return "Llamada"
        }

        return replace("_", " ")
            .replace("-", " ")
            .capitalizeFirst()
    }

    private fun String.localResourceTypeLabel(): String {
        return when (this) {
            "support_group" -> "AA/NA"
            "clinic", "health_center" -> "Clínicas"
            "rehab_center", "annex", "anexo" -> "Anexos"
            "hotline", "line", "phone_line" -> "Líneas"
            else -> replace("_", " ")
                .replace("-", " ")
                .capitalizeFirst()
        }
    }

    private fun String.toLocalDateOrNull(): LocalDate? {
        return runCatching {
            OffsetDateTime.parse(this).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
        }.getOrNull()
            ?: runCatching { LocalDateTime.parse(this).toLocalDate() }.getOrNull()
            ?: runCatching { LocalDate.parse(this) }.getOrNull()
    }

    private fun String.toZonedDateTimeOrNull(): ZonedDateTime? {
        return runCatching {
            OffsetDateTime.parse(this).atZoneSameInstant(ZoneId.systemDefault())
        }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(this).atZone(ZoneId.systemDefault())
            }.getOrNull()
            ?: runCatching {
                LocalDate.parse(this).atStartOfDay(ZoneId.systemDefault())
            }.getOrNull()
    }

    private class MissingSessionException : Exception()

    private class EarlyFailureException(
        val failure: PatientDataResult.Failure,
    ) : Exception()

    private data class SobrietyMilestone(
        val id: String,
        val title: String,
        val description: String,
        val daysRequired: Double,
    )

    private companion object {
        const val MAX_TEXT_LENGTH = 160
        const val MAX_RISKS = 20
        const val MINUTES_PER_DAY = 1_440.0
        const val CANCELLED_REQUEST_STATUS = "cancelled"
        const val SUPERVISION_REQUEST_TYPE_LINK = "link"
        const val SUPERVISION_REQUEST_TYPE_UNLINK = "unlink"
        const val CONSENT_STATUS_ACTIVE = "active"
        const val CONSENT_STATUS_PAUSED = "paused"
        const val CONSENT_STATUS_REVOKED = "revoked"
        const val NOTIFICATION_TIMEZONE = "America/Mexico_City"
        const val NOTES_RESOURCE = "patient_notes"
        const val CONTACTS_RESOURCE = "support_contacts"
        const val AGENDA_RESOURCE = "agenda_events"
        const val LOCAL_RESOURCES_RESOURCE = "local_resources"
        const val ACHIEVEMENTS_RESOURCE = "achievements"
        const val NOTIFICATIONS_RESOURCE = "notification_settings"
        val SUPERVISOR_CODE_REGEX = Regex("^RA-[0-9A-F]{8}$")
        val VALID_ROLES = setOf("patient", "supervisor")
        val VALID_EVENT_STATUSES = setOf("scheduled", "completed", "cancelled")
        val ACTIVE_SUPERVISION_STATUSES = setOf("accepted", "active", "authorized")
        val PENDING_SUPERVISION_STATUSES = setOf("pending", "requested")
        val REJECTED_SUPERVISION_STATUSES = setOf("rejected", "denied")
        val TIME_REGEX = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
        val DEFAULT_CONSENT_SCOPE = listOf(
            "patient_notes",
            "agenda_events",
            "support_contacts",
            "ai_chat_summary",
        )
        val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val DateLabelFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(
            "EEE d MMM",
            Locale.forLanguageTag("es-MX"),
        )
        val SOBRIETY_MILESTONES = listOf(
            SobrietyMilestone(
                id = "sobriety_12_hours",
                title = "12 horas",
                description = "Primer medio día de avance.",
                daysRequired = 0.5,
            ),
            SobrietyMilestone(
                id = "sobriety_1_day",
                title = "1 día",
                description = "Un día completo cuidando tu proceso.",
                daysRequired = 1.0,
            ),
            SobrietyMilestone(
                id = "sobriety_3_days",
                title = "3 días",
                description = "Tres días de constancia.",
                daysRequired = 3.0,
            ),
            SobrietyMilestone(
                id = "sobriety_1_week",
                title = "1 semana",
                description = "Siete días de recuperación.",
                daysRequired = 7.0,
            ),
            SobrietyMilestone(
                id = "sobriety_10_days",
                title = "10 días",
                description = "Diez días construyendo estabilidad.",
                daysRequired = 10.0,
            ),
            SobrietyMilestone(
                id = "sobriety_15_days",
                title = "15 días",
                description = "Dos semanas y un poco más.",
                daysRequired = 15.0,
            ),
            SobrietyMilestone(
                id = "sobriety_30_days",
                title = "30 días",
                description = "Un mes de avance.",
                daysRequired = 30.0,
            ),
            SobrietyMilestone(
                id = "sobriety_60_days",
                title = "60 días",
                description = "Dos meses sosteniendo el proceso.",
                daysRequired = 60.0,
            ),
            SobrietyMilestone(
                id = "sobriety_90_days",
                title = "90 días",
                description = "Tres meses de recuperación.",
                daysRequired = 90.0,
            ),
            SobrietyMilestone(
                id = "sobriety_120_days",
                title = "120 días",
                description = "Cuatro meses fortaleciendo tu rutina.",
                daysRequired = 120.0,
            ),
            SobrietyMilestone(
                id = "sobriety_150_days",
                title = "150 días",
                description = "Cinco meses cuidando tu estabilidad.",
                daysRequired = 150.0,
            ),
            SobrietyMilestone(
                id = "sobriety_180_days",
                title = "6 meses",
                description = "Medio año de avance sostenido.",
                daysRequired = 180.0,
            ),
            SobrietyMilestone(
                id = "sobriety_210_days",
                title = "210 días",
                description = "Siete meses de constancia.",
                daysRequired = 210.0,
            ),
            SobrietyMilestone(
                id = "sobriety_240_days",
                title = "240 días",
                description = "Ocho meses de cuidado personal.",
                daysRequired = 240.0,
            ),
            SobrietyMilestone(
                id = "sobriety_270_days",
                title = "270 días",
                description = "Nueve meses tomando decisiones saludables.",
                daysRequired = 270.0,
            ),
            SobrietyMilestone(
                id = "sobriety_300_days",
                title = "300 días",
                description = "Trescientos días de compromiso.",
                daysRequired = 300.0,
            ),
            SobrietyMilestone(
                id = "sobriety_365_days",
                title = "1 año",
                description = "Un año completo de recuperación.",
                daysRequired = 365.0,
            ),
            SobrietyMilestone(
                id = "sobriety_18_months",
                title = "18 meses",
                description = "Año y medio sosteniendo tu proceso.",
                daysRequired = 548.0,
            ),
            SobrietyMilestone(
                id = "sobriety_2_years",
                title = "2 años",
                description = "Dos años construyendo una nueva etapa.",
                daysRequired = 730.0,
            ),
            SobrietyMilestone(
                id = "sobriety_1000_days",
                title = "1000 días",
                description = "Mil días de avance acumulado.",
                daysRequired = 1000.0,
            ),
        )
    }
}
