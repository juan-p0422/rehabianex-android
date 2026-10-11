package com.example.rehabianex.data.repository

import com.example.rehabianex.core.error.ErrorMapper
import com.example.rehabianex.core.session.SessionManager
import com.example.rehabianex.core.session.SessionInvalidationBus
import com.example.rehabianex.core.session.SessionRefreshCoordinator
import com.example.rehabianex.core.session.SessionRefreshResult
import com.example.rehabianex.core.validation.MAX_AI_CHAT_QUESTION_LENGTH
import com.example.rehabianex.core.validation.sanitizeAiChatQuestion
import com.example.rehabianex.data.local.SupervisorNotificationSettingsStore
import com.example.rehabianex.data.local.db.SupervisorDashboardSummaryDao
import com.example.rehabianex.data.local.db.SupervisorDashboardSummaryEntity
import com.example.rehabianex.data.local.db.SupervisorProfileSnapshotDao
import com.example.rehabianex.data.local.db.SupervisorProfileSnapshotEntity
import com.example.rehabianex.data.model.ConsentDto
import com.example.rehabianex.data.model.NotificationSettingsData
import com.example.rehabianex.data.model.PatientNoteDto
import com.example.rehabianex.data.model.RespondSupervisionRequest
import com.example.rehabianex.data.model.ResourceListResponse
import com.example.rehabianex.data.model.SessionProfileDto
import com.example.rehabianex.data.model.SupervisionRequestDto
import com.example.rehabianex.data.model.SupervisorChatData
import com.example.rehabianex.data.model.SupervisorChatMessage
import com.example.rehabianex.data.model.SupervisorChatRequest
import com.example.rehabianex.data.model.SupervisorHomeData
import com.example.rehabianex.data.model.SupervisorNoteItem
import com.example.rehabianex.data.model.SupervisorPatientDetailData
import com.example.rehabianex.data.model.SupervisorPatientAccessState
import com.example.rehabianex.data.model.SupervisorPatientDto
import com.example.rehabianex.data.model.SupervisorPatientSummary
import com.example.rehabianex.data.model.SupervisorPatientsData
import com.example.rehabianex.data.model.SupervisorPendingRequest
import com.example.rehabianex.data.model.SupervisorRequestKind
import com.example.rehabianex.data.model.SupervisorProfileData
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

sealed interface SupervisorDataResult<out T> {
    data class Success<T>(val data: T) : SupervisorDataResult<T>
    data class Failure(
        val message: String,
        val statusCode: Int? = null,
        val fieldErrors: Map<String, String> = emptyMap(),
    ) : SupervisorDataResult<Nothing>
}

private const val ACTIVE_SUPERVISOR_STATUS = "active"

internal fun SessionProfileDto.isAuthorizedSupervisor(): Boolean {
    val normalizedStatus = status?.trim()?.lowercase(Locale.getDefault())
    return authorized == true &&
        verified == true &&
        normalizedStatus == ACTIVE_SUPERVISOR_STATUS
}

private data class PatientNotesAccess(
    val notes: List<PatientNoteDto>,
    val authorized: Boolean,
)

private data class ChatEligibility(
    val supervisorUid: String,
    val authorized: Boolean,
    val patientsCount: Int,
)

class SupervisorRepository(
    private val apiService: ApiService,
    private val sessionManager: SessionManager,
    private val notificationSettingsStore: SupervisorNotificationSettingsStore? = null,
    private val profileSnapshotDao: SupervisorProfileSnapshotDao? = null,
    private val dashboardSummaryDao: SupervisorDashboardSummaryDao? = null,
    private val now: () -> Long = System::currentTimeMillis,
    private val sessionRefreshCoordinator: SessionRefreshCoordinator =
        SessionRefreshCoordinator(apiService, sessionManager),
) {
    @Volatile
    private var chatEligibility: ChatEligibility? = null

    fun clearSessionMemory() {
        chatEligibility = null
    }

    suspend fun loadCachedHomeData(): SupervisorDataResult<SupervisorHomeData>? {
        val uid = sessionManager.getUid()?.takeIf(String::isNotBlank) ?: return null
        val profile = profileSnapshotDao?.get(uid) ?: return null
        val summary = dashboardSummaryDao?.get(uid)
        return SupervisorDataResult.Success(
            SupervisorHomeData(
                fullName = profile.displayName,
                supervisorType = profile.supervisorType,
                supervisorUid = uid,
                supervisorCode = profile.supervisorCode,
                authorized = profile.authorized && profile.verified && profile.status == ACTIVE_SUPERVISOR_STATUS,
                patients = emptyList(),
                pendingRequests = emptyList(),
                patientsCount = summary?.patientsCount ?: 0,
                pendingRequestsCount = summary?.pendingRequestsCount ?: 0,
                openInterventionsCount = summary?.openInterventionsCount ?: 0,
                isFromCache = true,
                isRefreshing = true,
                lastSyncedAt = maxOf(profile.lastSyncedAt, summary?.lastSyncedAt ?: 0L),
            ),
        )
    }

    suspend fun loadHomeData(): SupervisorDataResult<SupervisorHomeData> {
        val ownerUid = sessionManager.getUid()
        val result = safeRequest {
        val profile = readMeProfile()
        if (!profile.isAuthorizedSupervisor()) {
            val data = SupervisorHomeData(
                fullName = profile.fullName.cleanText() ?: "Supervisor",
                supervisorType = profile.supervisorTypeLabel(),
                supervisorUid = profile.uid.orSessionUid(),
                supervisorCode = profile.supervisorCodeOrFallback(),
                authorized = false,
                patients = emptyList(),
                pendingRequests = emptyList(),
            )
            cacheSupervisorSnapshot(profile, data, emptyList())
            return@safeRequest data
        }

        val supervisorUid = profile.uid.orSessionUid()
        val pendingRequests = loadPendingRequestsOrEmpty()
        val patients = loadSupervisorPatientSummariesWithoutNotes(supervisorUid, pendingRequests)

        val data = SupervisorHomeData(
            fullName = profile.fullName.cleanText() ?: "Supervisor",
            supervisorType = profile.supervisorTypeLabel(),
            supervisorUid = supervisorUid,
            supervisorCode = profile.supervisorCodeOrFallback(),
            authorized = true,
            patients = patients.take(3),
            pendingRequests = pendingRequests,
            patientsCount = patients.size,
        )
        cacheSupervisorSnapshot(profile, data, pendingRequests)
        data
        }
        clearSnapshotOnDenied(result, ownerUid)
        return result
    }

    suspend fun loadPatientsData(): SupervisorDataResult<SupervisorPatientsData> = safeRequest {
        val profile = readMeProfile()
        if (!profile.isAuthorizedSupervisor()) {
            return@safeRequest SupervisorPatientsData(
                patients = emptyList(),
                pendingRequests = emptyList(),
                supervisorUid = profile.uid.orSessionUid(),
                supervisorCode = profile.supervisorCodeOrFallback(),
                authorized = false,
            )
        }

        val supervisorUid = profile.uid.orSessionUid()
        val pendingRequests = loadPendingRequestsOrEmpty()
        val patients = loadSupervisorPatients(supervisorUid, pendingRequests)

        SupervisorPatientsData(
            patients = patients,
            pendingRequests = pendingRequests,
            supervisorUid = supervisorUid,
            supervisorCode = profile.supervisorCodeOrFallback(),
            authorized = true,
        )
    }

    suspend fun loadPatientDetail(
        patientUid: String,
    ): SupervisorDataResult<SupervisorPatientDetailData> = safeRequest {
        requireAuthorizedSupervisor()
        val supervisorUid = sessionUid()
        val notesAccess = loadPatientNotesAccess(patientUid)
        if (!notesAccess.authorized) {
            throw EarlyFailureException(
                SupervisorDataResult.Failure(
                    PATIENT_ACCESS_RESTRICTED_MESSAGE,
                    403,
                ),
            )
        }

        val patientsResponse = authorizedCall {
            apiService.supervisedPatients(supervisorUid)
        }
        if (!patientsResponse.isSuccessful) {
            throw EarlyFailureException(
                SupervisorDataResult.Failure(
                    patientsResponse.errorMessage(),
                    patientsResponse.code(),
                ),
            )
        }

        val patientDto = patientsResponse.body()?.patients.orEmpty()
            .firstOrNull { it.uid.cleanText(maxLength = 128) == patientUid }
            ?: throw EarlyFailureException(
                SupervisorDataResult.Failure(PATIENT_ACCESS_RESTRICTED_MESSAGE, 403),
            )
        if (patientDto.accessStateForSupervisor(supervisorUid, emptySet()) != SupervisorAccessState.Active) {
            throw EarlyFailureException(
                SupervisorDataResult.Failure(PATIENT_ACCESS_RESTRICTED_MESSAGE, 403),
            )
        }

        val notes = notesAccess.notes
        val patient = patientDto.toPatientSummary(notes)

        SupervisorPatientDetailData(
            patient = patient,
            latestNotes = notes.take(5).map { it.toNoteItem() },
            relapseCount = notes.count { it.hadRelapse == true },
        )
    }

    suspend fun loadCachedProfileData(): SupervisorDataResult<SupervisorProfileData>? {
        val uid = sessionManager.getUid()?.takeIf(String::isNotBlank) ?: return null
        val profile = profileSnapshotDao?.get(uid) ?: return null
        val summary = dashboardSummaryDao?.get(uid)
        return SupervisorDataResult.Success(
            SupervisorProfileData(
                initials = profile.displayName.initials(),
                fullName = profile.displayName,
                email = sessionManager.getEmail()?.takeIf(String::isNotBlank) ?: "Sin correo registrado",
                phone = "Se actualizará al conectar",
                supervisorType = profile.supervisorType,
                uid = uid,
                supervisorCode = profile.supervisorCode,
                authorized = profile.authorized && profile.verified && profile.status == ACTIVE_SUPERVISOR_STATUS,
                status = profile.status,
                linkedPatientsCount = summary?.patientsCount ?: 0,
                pendingRequestsCount = summary?.pendingRequestsCount ?: 0,
                isFromCache = true,
                isRefreshing = true,
                lastSyncedAt = maxOf(profile.lastSyncedAt, summary?.lastSyncedAt ?: 0L),
            ),
        )
    }

    suspend fun loadProfileData(): SupervisorDataResult<SupervisorProfileData> {
        val ownerUid = sessionManager.getUid()
        val result = safeRequest {
        val profile = readMeProfile()
        val supervisorUid = profile.uid.orSessionUid()
        val pendingRequests = if (profile.isAuthorizedSupervisor()) {
            loadPendingRequestsOrEmpty()
        } else {
            emptyList()
        }
        val linkedPatients = if (profile.isAuthorizedSupervisor()) {
            loadSupervisorPatientSummariesWithoutNotes(supervisorUid, pendingRequests)
        } else {
            emptyList()
        }
        val pendingRequestsCount = if (profile.isAuthorizedSupervisor()) {
            pendingRequests.size
        } else {
            0
        }

        val data = SupervisorProfileData(
            initials = profile.fullName.initials(),
            fullName = profile.fullName.cleanText() ?: "Supervisor RehabiAnex",
            email = profile.email.cleanText() ?: "Sin correo registrado",
            phone = profile.phone.cleanText() ?: "Sin teléfono registrado",
            supervisorType = profile.supervisorTypeLabel(),
            uid = supervisorUid,
            supervisorCode = profile.supervisorCodeOrFallback(),
            authorized = profile.isAuthorizedSupervisor(),
            status = profile.status.cleanText() ?: "sin estado",
            linkedPatientsCount = linkedPatients.size,
            pendingRequestsCount = pendingRequestsCount,
        )
        cacheSupervisorSnapshot(
            profile = profile,
            homeData = SupervisorHomeData(
                fullName = data.fullName,
                supervisorType = data.supervisorType,
                supervisorUid = data.uid,
                supervisorCode = data.supervisorCode,
                authorized = data.authorized,
                patients = emptyList(),
                pendingRequests = emptyList(),
                patientsCount = data.linkedPatientsCount,
                pendingRequestsCount = data.pendingRequestsCount,
            ),
            pendingRequests = emptyList(),
        )
        data
        }
        clearSnapshotOnDenied(result, ownerUid)
        return result
    }

    suspend fun respondRequest(
        requestId: String,
        accepted: Boolean,
    ): SupervisorDataResult<String> = safeRequest(
        defaultUnexpectedMessage = "No se pudo responder la solicitud.",
    ) {
        requireAuthorizedSupervisor()
        val cleanRequestId = requestId.safeIdentifier("Solicitud inválida.")
        val pendingRequest = loadPendingRequestsOrEmpty()
            .firstOrNull { it.id == cleanRequestId }

        val response = authorizedCall {
            apiService.respondSupervisionRequest(
                requestId = cleanRequestId,
                request = RespondSupervisionRequest(
                    status = if (accepted) "accepted" else "rejected",
                ),
            )
        }

        if (response.isSuccessful) {
            refreshSupervisorSupervision()
            if (accepted && pendingRequest?.kind == SupervisorRequestKind.Unlink) {
                "Solicitud de desvinculación aceptada. El paciente salió de tu seguimiento."
            } else if (!accepted && pendingRequest?.kind == SupervisorRequestKind.Unlink) {
                response.body()?.message.cleanText()
                    ?: "Solicitud de desvinculación rechazada. El paciente seguirá en tu lista."
            } else if (accepted && pendingRequest?.kind == SupervisorRequestKind.Link) {
                response.body()?.message.cleanText()
                    ?: "Solicitud aceptada. El paciente aparecerá cuando el permiso de seguimiento esté activo."
            } else {
                response.body()?.message.cleanText()
                    ?: if (accepted) "Solicitud aceptada." else "Solicitud rechazada."
            }
        } else {
            throw EarlyFailureException(
                response.toSupervisorFailure(),
            )
        }
    }

    suspend fun unlinkPatient(
        patientUid: String,
    ): SupervisorDataResult<String> = safeRequest(
        defaultUnexpectedMessage = "No fue posible desvincular al paciente.",
    ) {
        requireAuthorizedSupervisor()
        val cleanPatientUid = patientUid.safeIdentifier("Paciente inválido.")
        val supervisorUid = sessionUid()
        val pendingRequests = loadPendingRequestsOrEmpty()
        val linkedPatient = loadSupervisorPatients(supervisorUid, pendingRequests)
            .firstOrNull { it.uid == cleanPatientUid }
            ?: throw EarlyFailureException(
                SupervisorDataResult.Failure("Este paciente ya no aparece en tu lista de seguimiento."),
            )

        unlinkPatientDirect(
            patientUid = cleanPatientUid,
            supervisorUid = supervisorUid,
            patientName = linkedPatient.name,
        )
    }

    private suspend fun unlinkPatientDirect(
        patientUid: String,
        supervisorUid: String,
        patientName: String,
    ): String {
        val response = authorizedCall {
            apiService.unlinkSupervision(
                supervisorUid = supervisorUid,
                patientUid = patientUid,
            )
        }
        if (!response.isSuccessful) {
            throw EarlyFailureException(
                response.toSupervisorFailure(),
            )
        }

        refreshSupervisorSupervision()
        return response.body()?.message.cleanText()
            ?: "$patientName fue retirado de tu seguimiento."
    }

    suspend fun loadNotificationSettings(): SupervisorDataResult<NotificationSettingsData> = safeRequest {
        val supervisorUid = sessionUid()
        notificationSettingsStore?.load(supervisorUid)
            ?: defaultSupervisorNotificationSettings(supervisorUid)
    }

    suspend fun saveNotificationSettings(
        settings: NotificationSettingsData,
    ): SupervisorDataResult<NotificationSettingsData> = safeRequest(
        defaultUnexpectedMessage = "No fue posible guardar tus notificaciones.",
    ) {
        val supervisorUid = sessionUid()
        notificationSettingsStore?.save(supervisorUid, settings)
            ?: settings.copy(
                id = "$LOCAL_NOTIFICATION_ID_PREFIX$supervisorUid",
                eventRemindersEnabled = false,
            )
    }

    suspend fun loadChatData(): SupervisorDataResult<SupervisorChatData> = safeRequest(
        timeoutMessage = "El Chat IA tardó demasiado en validar tus permisos.",
        connectionMessage = "El Chat IA requiere conexión. Verifica tu red e inténtalo nuevamente.",
    ) {
        val profile = readMeProfile()
        if (!profile.isAuthorizedSupervisor()) {
            val data = SupervisorChatData(
                supervisorUid = profile.uid.orSessionUid(),
                patientsCount = 0,
                authorized = false,
            )
            chatEligibility = data.toEligibility()
            return@safeRequest data
        }

        val supervisorUid = profile.uid.orSessionUid()
        val eligiblePatientsCount = loadAiChatEligiblePatientsCount(supervisorUid)
        val data = SupervisorChatData(
            supervisorUid = supervisorUid,
            patientsCount = eligiblePatientsCount,
            authorized = true,
        )
        chatEligibility = data.toEligibility()
        data
    }

    suspend fun askSupervisorChat(
        questionInput: String,
        mode: String = "ai",
    ): SupervisorDataResult<SupervisorChatMessage> = safeRequest(
        defaultUnexpectedMessage = "No se pudo consultar el chatbot IA.",
        timeoutMessage = "El Chat IA tardó demasiado en responder. Inténtalo nuevamente.",
        connectionMessage = "El Chat IA requiere conexión. Verifica tu red e inténtalo nuevamente.",
    ) {
        val question = questionInput.sanitizeAiChatQuestion()
        if (question.isBlank()) {
            throw EarlyFailureException(
                SupervisorDataResult.Failure("Escribe una pregunta válida.", 422),
            )
        }
        if (question.length > MAX_AI_CHAT_QUESTION_LENGTH) {
            throw EarlyFailureException(
                SupervisorDataResult.Failure(
                    "La pregunta no puede superar $MAX_AI_CHAT_QUESTION_LENGTH caracteres.",
                    422,
                ),
            )
        }
        val requestedMode = mode.cleanText(maxLength = 16)
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it in VALID_AI_CHAT_MODES }
            ?: throw EarlyFailureException(
                SupervisorDataResult.Failure("Selecciona un modo de consulta válido.", 422),
            )

        val supervisorUid = sessionUid()
        val knownEligibility = chatEligibility?.takeIf { it.supervisorUid == supervisorUid }
        if (knownEligibility?.authorized == false) {
            throw EarlyFailureException(
                SupervisorDataResult.Failure(SUPERVISOR_AUTHORIZATION_PENDING_MESSAGE, 403),
            )
        }
        if (knownEligibility?.patientsCount == 0) {
            throw EarlyFailureException(
                SupervisorDataResult.Failure(
                    "Aún no tienes pacientes con permiso para Chat IA. Actualiza la pantalla cuando cambien los consentimientos.",
                    409,
                ),
            )
        }

        val response = authorizedCall {
            apiService.supervisorChat(
                SupervisorChatRequest(
                    supervisorUid = supervisorUid,
                    question = question,
                    mode = requestedMode,
                ),
            )
        }

        if (response.isSuccessful) {
            val answer = response.body()?.answer.cleanText(maxLength = 3000)
                ?: throw EarlyFailureException(
                    SupervisorDataResult.Failure(
                        "El asistente no devolvió una respuesta. Inténtalo nuevamente.",
                    ),
                )
            SupervisorChatMessage(
                text = answer.toReadableAiAnswer(),
                fromUser = false,
                responseMode = response.body()?.mode.cleanText(maxLength = 32)
                    ?.lowercase(Locale.ROOT)
                    ?.takeIf { it in VALID_AI_CHAT_RESPONSE_MODES }
                    ?: requestedMode,
            )
        } else {
            throw EarlyFailureException(
                SupervisorDataResult.Failure(response.chatErrorMessage(), response.code()),
            )
        }
    }

    private suspend fun loadAiChatEligiblePatientsCount(
        supervisorUid: String,
    ): Int {
        val response = authorizedCall {
            apiService.supervisorChatEligibility()
        }

        if (response.isSuccessful) {
            val body = response.body() ?: throw EarlyFailureException(
                SupervisorDataResult.Failure(
                    "El servidor no devolvió la elegibilidad del Chat IA.",
                ),
            )
            return body.eligiblePatientsCount
                ?.coerceAtLeast(0)
                ?: if (body.eligible == true) 1 else 0
        }

        if (response.code() != 404) {
            throw EarlyFailureException(
                SupervisorDataResult.Failure(response.chatErrorMessage(), response.code()),
            )
        }

        val patientsResponse = authorizedCall {
            apiService.supervisedPatients(supervisorUid)
        }
        if (!patientsResponse.isSuccessful) {
            throw EarlyFailureException(
                SupervisorDataResult.Failure(
                    patientsResponse.chatErrorMessage(),
                    patientsResponse.code(),
                ),
            )
        }

        return patientsResponse.body()?.patients.orEmpty()
            .filter { it.permissions?.aiChatSummary == true }
            .mapNotNull { it.uid.cleanText(maxLength = 128) }
            .distinct()
            .size
    }

    private fun SupervisorChatData.toEligibility() = ChatEligibility(
        supervisorUid = supervisorUid,
        authorized = authorized,
        patientsCount = patientsCount,
    )

    private suspend fun loadSupervisorPatients(
        supervisorUid: String,
        pendingRequests: List<SupervisorPendingRequest>,
    ): List<SupervisorPatientSummary> {
        val patientsFromEndpoint = loadPatientSummaries(
            supervisorUid = supervisorUid,
            pendingPatientUids = pendingRequests.patientUidSet(),
        )
        val loadedPatientUids = patientsFromEndpoint.map { it.uid }.toSet()
        val restrictedPatientsFromConsents = loadRestrictedPatientsFromConsents(
            supervisorUid = supervisorUid,
            excludedPatientUids = loadedPatientUids,
        )

        return (patientsFromEndpoint + restrictedPatientsFromConsents)
            .distinctBy { it.uid }
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    private suspend fun loadRestrictedPatientsFromConsents(
        supervisorUid: String,
        excludedPatientUids: Set<String>,
    ): List<SupervisorPatientSummary> {
        val response = authorizedCall {
            apiService.consents(supervisorUid = supervisorUid, limit = 50)
        }
        if (!response.isSuccessful) {
            if (response.code() == 404) return emptyList()
            if (response.code() == 403) {
                throw EarlyFailureException(
                    SupervisorDataResult.Failure(
                        SUPERVISOR_AUTHORIZATION_PENDING_MESSAGE,
                        403,
                    ),
                )
            }
            throw EarlyFailureException(
                response.toSupervisorFailure(),
            )
        }

        return response.body()?.data.orEmpty()
            .filter { consent ->
                consent.supervisorUid.cleanText(maxLength = 128) == supervisorUid
            }
            .groupBy { consent -> consent.patientUid.cleanText(maxLength = 128) }
            .values
            .mapNotNull { consents ->
                consents.maxWithOrNull(
                    compareBy<ConsentDto> { it.updatedAt.cleanText().orEmpty() }
                        .thenBy { it.consentStatePriority() },
                )
            }
            .mapNotNull { consent ->
                val patientUid = consent.patientUid.cleanText(maxLength = 128)
                    ?: return@mapNotNull null
                if (patientUid in excludedPatientUids) return@mapNotNull null

                when (val accessState = consent.restrictedAccessState()) {
                    SupervisorPatientAccessState.Active -> null
                    SupervisorPatientAccessState.Paused ->
                        restrictedPatientSummaryFallback(patientUid, accessState)
                    SupervisorPatientAccessState.NoAccess,
                    SupervisorPatientAccessState.Unlinked ->
                        restrictedPatientSummaryFallback(patientUid, accessState)
                }
            }
    }

    private suspend fun loadPatientSummaries(
        supervisorUid: String,
        pendingPatientUids: Set<String>? = null,
    ): List<SupervisorPatientSummary> {
        val blockedPendingPatientUids = pendingPatientUids
            ?: loadPendingRequestsOrEmpty().patientUidSet()
        val response = authorizedCall {
            apiService.supervisedPatients(supervisorUid)
        }

        if (!response.isSuccessful) {
            throw EarlyFailureException(
                response.toSupervisorFailure(),
            )
        }

        return response.body()?.patients.orEmpty()
            .mapNotNull { patient ->
                val patientUid = patient.uid.cleanText(maxLength = 128) ?: return@mapNotNull null
                when (patient.accessStateForSupervisor(supervisorUid, blockedPendingPatientUids)) {
                    SupervisorAccessState.Excluded -> return@mapNotNull null
                    SupervisorAccessState.Paused -> return@mapNotNull patient.toPatientSummary(
                        notes = emptyList(),
                        accessPaused = true,
                        accessState = SupervisorPatientAccessState.Paused,
                    )
                    SupervisorAccessState.NoAccess -> return@mapNotNull patient.toRestrictedPatientSummary(
                        patientUid = patientUid,
                        accessState = SupervisorPatientAccessState.NoAccess,
                    )
                    SupervisorAccessState.Active -> Unit
                }
                val notesAccess = loadPatientNotesAccess(patientUid)
                if (!notesAccess.authorized) {
                    return@mapNotNull patient.toRestrictedPatientSummary(
                        patientUid = patientUid,
                        accessState = SupervisorPatientAccessState.NoAccess,
                    )
                }
                patient.toPatientSummary(notesAccess.notes)
            }
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    /**
     * Proyección ligera para Home/Perfil. Evita una llamada de notas por paciente;
     * los datos clínicos solo se consultan en flujos que realmente los muestran.
     */
    private suspend fun loadSupervisorPatientSummariesWithoutNotes(
        supervisorUid: String,
        pendingRequests: List<SupervisorPendingRequest>,
    ): List<SupervisorPatientSummary> {
        val response = authorizedCall { apiService.supervisedPatients(supervisorUid) }
        if (!response.isSuccessful) {
            throw EarlyFailureException(response.toSupervisorFailure())
        }
        val pendingPatientUids = pendingRequests.patientUidSet()
        return response.body()?.patients.orEmpty().mapNotNull { patient ->
            val patientUid = patient.uid.cleanText(maxLength = 128) ?: return@mapNotNull null
            when (patient.accessStateForSupervisor(supervisorUid, pendingPatientUids)) {
                SupervisorAccessState.Excluded -> null
                SupervisorAccessState.Paused -> patient.toPatientSummary(
                    notes = emptyList(),
                    accessPaused = true,
                    accessState = SupervisorPatientAccessState.Paused,
                )
                SupervisorAccessState.NoAccess -> patient.toRestrictedPatientSummary(
                    patientUid = patientUid,
                    accessState = SupervisorPatientAccessState.NoAccess,
                )
                SupervisorAccessState.Active -> patient.toPatientSummary(notes = emptyList())
            }
        }.sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    private suspend fun loadPatientNotesAccess(patientUid: String): PatientNotesAccess {
        val response = authorizedCall {
            apiService.patientNotesByPatient(patientUid)
        }

        if (!response.isSuccessful) {
            if (response.code() == 403) {
                return PatientNotesAccess(
                    notes = emptyList(),
                    authorized = false,
                )
            }
            throw EarlyFailureException(
                response.toSupervisorFailure(),
            )
        }

        return PatientNotesAccess(
            notes = response.body()?.resolvedNotes().orEmpty()
                .sortedByDescending { it.createdAt.orEmpty() },
            authorized = true,
        )
    }

    private suspend fun loadPendingRequestsOrEmpty(): List<SupervisorPendingRequest> {
        val supervisorUid = sessionUid()
        val response = authorizedCall {
            apiService.supervisionRequests(status = "pending", limit = 50)
        }

        if (!response.isSuccessful) {
            if (response.code() == 404) return emptyList()
            if (response.code() == 403) {
                throw EarlyFailureException(
                    SupervisorDataResult.Failure(
                        SUPERVISOR_AUTHORIZATION_PENDING_MESSAGE,
                        403,
                    ),
                )
            }
            throw EarlyFailureException(
                response.toSupervisorFailure(),
            )
        }

        return response.body()?.data.orEmpty()
            .filter { request ->
                request.supervisorUid.cleanText(maxLength = 128) == supervisorUid
            }
            .mapNotNull { it.toPendingRequest() }
            .sortedByDescending { it.requestedAt }
    }

    private suspend fun refreshSupervisorSupervision() {
        val profile = readMeProfile()
        if (!profile.isAuthorizedSupervisor()) return

        val supervisorUid = profile.uid.orSessionUid()
        val pendingRequests = loadPendingRequestsOrEmpty()
        loadSupervisorPatients(supervisorUid, pendingRequests)
    }

    private suspend fun cacheSupervisorSnapshot(
        profile: SessionProfileDto,
        homeData: SupervisorHomeData,
        pendingRequests: List<SupervisorPendingRequest>,
    ) {
        val uid = profile.uid.orSessionUid()
        val syncedAt = now()
        profileSnapshotDao?.upsert(
            SupervisorProfileSnapshotEntity(
                ownerUid = uid,
                displayName = profile.fullName.cleanText() ?: "Supervisor RehabiAnex",
                supervisorType = profile.supervisorTypeLabel(),
                supervisorCode = profile.supervisorCodeOrFallback(),
                authorized = profile.authorized == true,
                verified = profile.verified == true,
                status = profile.status.cleanText()?.lowercase(Locale.getDefault()) ?: "sin estado",
                lastSyncedAt = syncedAt,
                isStale = false,
            ),
        )
        dashboardSummaryDao?.upsert(
            SupervisorDashboardSummaryEntity(
                ownerUid = uid,
                patientsCount = homeData.patientsCount,
                pendingRequestsCount = homeData.pendingRequestsCount.takeIf { it > 0 }
                    ?: pendingRequests.size,
                openInterventionsCount = homeData.openInterventionsCount,
                lastSyncedAt = syncedAt,
            ),
        )
    }

    private suspend fun clearSnapshotOnDenied(
        result: SupervisorDataResult<*>,
        ownerUid: String?,
    ) {
        val failure = result as? SupervisorDataResult.Failure ?: return
        if (failure.statusCode !in setOf(401, 403)) return
        val uid = ownerUid?.takeIf(String::isNotBlank) ?: return
        profileSnapshotDao?.clearOwner(uid)
        dashboardSummaryDao?.clearOwner(uid)
    }

    private suspend fun readMeProfile(): SessionProfileDto {
        val response = authorizedCall { apiService.me() }

        if (response.isSuccessful) {
            return response.body()?.profile ?: throw EarlyFailureException(
                SupervisorDataResult.Failure("El servidor no devolvió el perfil de la cuenta."),
            )
        }

        throw EarlyFailureException(
            response.toSupervisorFailure(),
        )
    }

    private suspend fun requireAuthorizedSupervisor(): SessionProfileDto {
        val profile = readMeProfile()
        if (!profile.isAuthorizedSupervisor()) {
            throw EarlyFailureException(
                SupervisorDataResult.Failure(
                    message = SUPERVISOR_AUTHORIZATION_PENDING_MESSAGE,
                    statusCode = 403,
                ),
            )
        }
        return profile
    }

    private fun sessionUid(): String {
        return sessionManager.getUid()
            ?.takeIf { it.isNotBlank() }
            ?: throw MissingSessionException()
    }

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
                    SupervisorDataResult.Failure(
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
                SupervisorDataResult.Failure(
                    message = ErrorMapper.fromHttpCode(401).message,
                    statusCode = 401,
                ),
            )
        }
        return retryResponse
    }

    private fun invalidateSession() {
        sessionManager.clear()
        SessionInvalidationBus.notifySessionExpired()
    }

    private suspend fun <T> safeRequest(
        defaultUnexpectedMessage: String = "Ocurrió un error inesperado al cargar tus datos.",
        timeoutMessage: String = "El servidor tardó demasiado en responder.",
        connectionMessage: String = "No fue posible conectar con el servidor.",
        block: suspend () -> T,
    ): SupervisorDataResult<T> {
        val ownerUid = sessionManager.getUid()
        val result = try {
            SupervisorDataResult.Success(block())
        } catch (error: EarlyFailureException) {
            error.failure
        } catch (error: MissingSessionException) {
            SupervisorDataResult.Failure("No hay una sesión activa. Inicia sesión nuevamente.")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            SupervisorDataResult.Failure(
                ErrorMapper.fromThrowable(
                    error = error,
                    timeoutMessage = timeoutMessage,
                    connectionMessage = connectionMessage,
                    unexpectedMessage = defaultUnexpectedMessage,
                ).message,
            )
        }
        clearSnapshotOnDenied(result, ownerUid)
        return result
    }

    private fun Response<*>.errorMessage(): String {
        val parsedMessage = ApiErrorParser.parse(errorBody(), code()).message
        return ErrorMapper.fromHttpCode(code(), parsedMessage).message
    }

    private fun Response<*>.toSupervisorFailure(): SupervisorDataResult.Failure {
        val parsedError = ApiErrorParser.parse(errorBody(), code())
        return SupervisorDataResult.Failure(
            message = ErrorMapper.fromHttpCode(code(), parsedError.message).message,
            statusCode = code(),
            fieldErrors = parsedError.fieldErrors,
        )
    }

    private fun Response<*>.chatErrorMessage(): String {
        val parsedMessage = ApiErrorParser.parse(errorBody(), code()).message
        return ErrorMapper.safeBackendMessage(code(), parsedMessage) ?: when (code()) {
            401 -> "Tu sesión expiró. Inicia sesión nuevamente."
            403 -> "No tienes permiso para usar el Chat IA o no hay pacientes con consentimiento activo."
            409 -> "No hay pacientes con consentimiento activo para usar Chat IA. Actualiza la pantalla e inténtalo nuevamente."
            422 -> "La pregunta enviada no es válida."
            429 -> "Has alcanzado el límite de consultas. Espera un momento e inténtalo nuevamente."
            in 500..599 -> "El asistente no está disponible temporalmente. Inténtalo más tarde."
            else -> "No fue posible consultar el Chat IA."
        }
    }

    private fun SupervisorPatientDto.toPatientSummary(
        notes: List<PatientNoteDto>,
        accessPaused: Boolean = false,
        accessState: SupervisorPatientAccessState = SupervisorPatientAccessState.Active,
    ): SupervisorPatientSummary {
        val latestNote = notes.firstOrNull()
        val anxiety = latestNote?.anxietyLevel ?: 0
        val craving = latestNote?.cravingLevel ?: 0
        val riskLevel = latestNote?.aiRiskLevel.cleanText() ?: when {
            anxiety >= 7 || craving >= 7 -> "high"
            anxiety >= 4 || craving >= 4 -> "medium"
            else -> "low"
        }

        return SupervisorPatientSummary(
            uid = uid.cleanText(maxLength = 128).orEmpty(),
            name = displayName(),
            soberDays = sobrietyStartDate.soberDaysText(),
            latestStatus = latestNote?.recentState() ?: "Sin registros recientes",
            latestDateText = latestNote?.createdAt.relativeDateText(),
            anxietyText = "${(anxiety / 2.0).roundToInt().coerceIn(0, 5)}/5",
            cravingText = "${(craving / 2.0).roundToInt().coerceIn(0, 5)}/5",
            anxietyLevel = anxiety,
            cravingLevel = craving,
            riskLevel = riskLevel,
            hasRecentActivity = latestNote?.createdAt.isRecentActivity(),
            notesCount = notes.size,
            accessPaused = accessPaused,
            canOpenDetails = !accessPaused,
            accessState = accessState,
        )
    }

    private fun SupervisorPatientDto.accessStateForSupervisor(
        currentSupervisorUid: String,
        pendingPatientUids: Set<String>,
    ): SupervisorAccessState {
        val patientUid = uid.cleanText(maxLength = 128)
            ?: return SupervisorAccessState.Excluded
        val linkedSupervisorUid = supervisorUid.cleanText(maxLength = 128)
        val patientStatus = status.cleanText()?.lowercase(Locale.getDefault())
        val supervision = supervisionStatus.cleanText()?.lowercase(Locale.getDefault())
        val consent = consentStatus.cleanText()?.lowercase(Locale.getDefault())
        val hasAcceptedSupervision = supervision in ACTIVE_SUPERVISION_STATUSES ||
            (supervision == null && linkedSupervisorUid == currentSupervisorUid)

        return when {
            linkedSupervisorUid != null && linkedSupervisorUid != currentSupervisorUid ->
                SupervisorAccessState.Excluded
            patientStatus in INACTIVE_PATIENT_STATUSES ->
                SupervisorAccessState.Excluded
            supervision in EXCLUDED_SUPERVISION_STATUSES ->
                SupervisorAccessState.Excluded
            supervision in NO_ACCESS_SUPERVISION_STATUSES ->
                SupervisorAccessState.NoAccess
            supervision in PAUSED_SUPERVISION_STATUSES ->
                SupervisorAccessState.Paused
            consent in NO_ACCESS_CONSENT_STATUSES ->
                SupervisorAccessState.NoAccess
            consent in PAUSED_CONSENT_STATUSES ->
                SupervisorAccessState.Paused
            consentActive == false || explicitConsent == false ->
                SupervisorAccessState.NoAccess
            patientUid in pendingPatientUids && linkedSupervisorUid != currentSupervisorUid && !hasAcceptedSupervision ->
                SupervisorAccessState.Excluded
            else -> SupervisorAccessState.Active
        }
    }

    private fun List<SupervisorPendingRequest>.patientUidSet(): Set<String> {
        return filter { it.kind == SupervisorRequestKind.Link }
            .mapNotNull { it.patientUid.cleanText(maxLength = 128) }
            .toSet()
    }

    private fun SupervisionRequestDto.toPendingRequest(): SupervisorPendingRequest? {
        val id = documentId.cleanText(maxLength = 128)
            ?: requestId.cleanText(maxLength = 128)
            ?: return null
        val patientUid = patientUid.cleanText(maxLength = 128) ?: return null
        val cleanMessage = message.cleanText(maxLength = 180)
        val kind = if (isUnlinkRequest()) {
            SupervisorRequestKind.Unlink
        } else {
            SupervisorRequestKind.Link
        }

        return SupervisorPendingRequest(
            id = id,
            patientUid = patientUid,
            patientName = patientName.cleanText(maxLength = 80)
                ?: "Paciente pendiente",
            message = if (kind == SupervisorRequestKind.Unlink) {
                "El paciente solicita finalizar el seguimiento."
            } else {
                cleanMessage ?: "Solicitud de supervisión sin mensaje."
            },
            requestedAt = requestedAt.cleanText() ?: "",
            kind = kind,
        )
    }

    private fun SupervisionRequestDto.isUnlinkRequest(): Boolean {
        val officialType = type.cleanText(maxLength = 20)?.lowercase(Locale.ROOT)
            ?: requestType.cleanText(maxLength = 20)?.lowercase(Locale.ROOT)
        if (officialType != null) return officialType == SUPERVISION_REQUEST_TYPE_UNLINK

        // Solo para solicitudes históricas creadas antes de que type fuera obligatorio.
        val legacyMessage = message.cleanText(maxLength = 240)
            ?.lowercase(Locale.getDefault())
            .orEmpty()
        return legacyMessage.contains("solicitud_desvinculacion") ||
            legacyMessage.contains("desvincul") ||
            legacyMessage.contains("cortar seguimiento")
    }

    private fun ConsentDto.restrictedAccessState(): SupervisorPatientAccessState {
        val normalizedStatus = status.cleanText(maxLength = 32)?.lowercase(Locale.ROOT)
        val normalizedReason = revocationReason.cleanText(maxLength = 64)?.lowercase(Locale.ROOT)
        return when {
            normalizedStatus == "active" && explicitConsent == true ->
                if (scope.orEmpty().isNotEmpty()) {
                    SupervisorPatientAccessState.Active
                } else {
                    SupervisorPatientAccessState.NoAccess
                }
            normalizedStatus == CONSENT_STATUS_PAUSED ->
                SupervisorPatientAccessState.Paused
            normalizedStatus == "revoked" && normalizedReason == "supervision_unlinked" ->
                SupervisorPatientAccessState.Unlinked
            normalizedStatus == "revoked" ->
                SupervisorPatientAccessState.NoAccess
            normalizedStatus == null && explicitConsent == false ->
                SupervisorPatientAccessState.NoAccess
            explicitConsent != true ->
                SupervisorPatientAccessState.NoAccess
            else -> SupervisorPatientAccessState.Active
        }
    }

    private fun ConsentDto.consentStatePriority(): Int {
        return when (status.cleanText(maxLength = 32)?.lowercase(Locale.ROOT)) {
            "revoked" -> 4
            CONSENT_STATUS_PAUSED -> 3
            "active" -> 2
            else -> 0
        }
    }

    private fun defaultSupervisorNotificationSettings(
        supervisorUid: String,
    ): NotificationSettingsData {
        return NotificationSettingsData(
            id = "$LOCAL_NOTIFICATION_ID_PREFIX$supervisorUid",
            dailyNoteEnabled = true,
            dailyNoteTime = "08:00",
            soberDayEnabled = true,
            soberDayTime = "09:00",
            achievementEnabled = true,
            achievementTime = "09:30",
            eventRemindersEnabled = false,
            motivationalEnabled = false,
            motivationalTime = "18:00",
        )
    }

    private fun PatientNoteDto.toNoteItem(): SupervisorNoteItem {
        val anxiety = anxietyLevel ?: 0
        val craving = cravingLevel ?: 0
        return SupervisorNoteItem(
            title = "${mood.cleanText()?.capitalizeFirst() ?: "Registro"} · Ansiedad ${anxiety}/10 · Craving ${craving}/10",
            detail = noteText.cleanText(maxLength = 180)
                ?: "Sin nota libre registrada.",
            dateText = createdAt.relativeDateText(),
        )
    }

    private fun PatientNoteDto.recentState(): String {
        val anxiety = anxietyLevel ?: 0
        val craving = cravingLevel ?: 0

        return when {
            hadRelapse == true -> "Recaída registrada"
            anxiety >= 7 -> "Ansiedad alta"
            craving >= 7 -> "Craving alto"
            anxiety >= 4 || craving >= 4 -> "Riesgo moderado"
            else -> mood.cleanText()?.capitalizeFirst() ?: "Estable"
        }
    }

    private fun SupervisorPatientDto.displayName(): String {
        val safeAlias = nickname.cleanText(maxLength = 80)
            ?: displayName.cleanText(maxLength = 80)
            ?: patientRef.cleanText(maxLength = 80)
        if (isAnonymous == true || privacyMode == true) {
            return safeAlias ?: "Paciente protegido"
        }
        return safeAlias
            ?: fullName.cleanText(maxLength = 80)
            ?: "Paciente supervisado"
    }

    private fun SupervisorPatientDto.toRestrictedPatientSummary(
        patientUid: String,
        accessState: SupervisorPatientAccessState,
    ): SupervisorPatientSummary {
        return restrictedPatientSummary(
            patientUid = uid.cleanText(maxLength = 128) ?: patientUid,
            patientName = displayName(),
            accessState = accessState,
        )
    }

    private fun restrictedPatientSummaryFallback(
        patientUid: String,
        accessState: SupervisorPatientAccessState,
    ): SupervisorPatientSummary {
        return restrictedPatientSummary(
            patientUid = patientUid,
            patientName = when (accessState) {
                SupervisorPatientAccessState.Paused -> "Paciente con acceso pausado"
                SupervisorPatientAccessState.NoAccess -> "Paciente sin acceso"
                SupervisorPatientAccessState.Unlinked -> "Paciente desvinculado"
                SupervisorPatientAccessState.Active -> "Paciente"
            },
            accessState = accessState,
        )
    }

    private fun restrictedPatientSummary(
        patientUid: String,
        patientName: String,
        accessState: SupervisorPatientAccessState,
    ): SupervisorPatientSummary {
        return SupervisorPatientSummary(
            uid = patientUid,
            name = patientName,
            soberDays = "—",
            latestStatus = accessState.statusLabel(),
            latestDateText = "Privado",
            anxietyText = "—",
            cravingText = "—",
            anxietyLevel = 0,
            cravingLevel = 0,
            riskLevel = accessState.name.lowercase(Locale.ROOT),
            hasRecentActivity = false,
            notesCount = 0,
            accessPaused = accessState == SupervisorPatientAccessState.Paused,
            canOpenDetails = false,
            accessState = accessState,
        )
    }

    private fun SupervisorPatientAccessState.statusLabel(): String {
        return when (this) {
            SupervisorPatientAccessState.Active -> "Supervisión activa"
            SupervisorPatientAccessState.Paused -> "Acceso pausado"
            SupervisorPatientAccessState.NoAccess -> "Sin acceso"
            SupervisorPatientAccessState.Unlinked -> "Desvinculado"
        }
    }

    private fun SessionProfileDto.supervisorTypeLabel(): String {
        return when (supervisorType.cleanText()?.lowercase(Locale.getDefault())) {
            "clinical_psychologist" -> "Psicólogo clínico"
            "doctor" -> "Doctor"
            "padrino" -> "Padrino"
            "sponsor" -> "Padrino"
            "therapist" -> "Terapeuta"
            else -> "Supervisor"
        }
    }

    private fun SessionProfileDto.supervisorCodeOrFallback(): String {
        return supervisorCode.cleanText(maxLength = 24)
            ?: if (isAuthorizedSupervisor()) {
                "RA-${uid.orSessionUid().sha1Prefix(8)}"
            } else {
                ""
            }
    }

    private fun String?.orSessionUid(): String {
        return cleanText(maxLength = 128) ?: sessionUid()
    }

    private fun String?.soberDaysText(): String {
        val startDate = toZonedDateTimeOrNull() ?: return "0.0"
        val minutes = ChronoUnit.MINUTES
            .between(startDate, ZonedDateTime.now(ZoneId.systemDefault()))
            .coerceAtLeast(0)
        val days = minutes / MINUTES_PER_DAY
        return String.format(Locale.getDefault(), "%.1f", days)
    }

    private fun String?.relativeDateText(): String {
        val date = toZonedDateTimeOrNull()?.toLocalDate() ?: return "Sin registro"
        val days = ChronoUnit.DAYS.between(date, LocalDate.now()).coerceAtLeast(0)
        return when (days) {
            0L -> "hoy"
            1L -> "ayer"
            else -> "hace $days días"
        }
    }

    private fun String?.isRecentActivity(): Boolean {
        val date = toZonedDateTimeOrNull()?.toLocalDate() ?: return false
        return ChronoUnit.DAYS.between(date, LocalDate.now()).coerceAtLeast(0) <= 2
    }

    private fun String?.toZonedDateTimeOrNull(): ZonedDateTime? {
        val value = this ?: return null
        return runCatching {
            OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault())
        }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(value).atZone(ZoneId.systemDefault())
            }.getOrNull()
            ?: runCatching {
                LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault())
            }.getOrNull()
    }

    private fun String?.cleanText(maxLength: Int = MAX_TEXT_LENGTH): String? {
        return this
            ?.filterNot { it.isISOControl() }
            ?.replace("<", "")
            ?.replace(">", "")
            ?.trim()
            ?.take(maxLength)
            ?.takeIf { it.isNotBlank() }
    }

    private fun String?.cleanTimeOrDefault(defaultValue: String): String {
        val cleanValue = cleanText(maxLength = 5) ?: return defaultValue
        return cleanValue.takeIf { Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(it) }
            ?: defaultValue
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

    private fun String.safeIdentifier(message: String): String {
        val cleanValue = cleanText(maxLength = 128)
            ?: throw EarlyFailureException(SupervisorDataResult.Failure(message))
        val hasUnsafeCharacters = cleanValue.any { char ->
            char.isWhitespace() || char == '/' || char == '\\' || char == '?' || char == '#'
        }

        if (hasUnsafeCharacters) {
            throw EarlyFailureException(SupervisorDataResult.Failure(message))
        }
        return cleanValue
    }

    private fun String.sha1Prefix(length: Int): String {
        val bytes = MessageDigest
            .getInstance("SHA-1")
            .digest(toByteArray())

        return bytes.joinToString("") { byte -> "%02x".format(byte) }
            .take(length)
            .uppercase(Locale.getDefault())
    }

    private fun String.toReadableAiAnswer(): String {
        val normalized = replace("\r", "\n")
            .replace("**", "")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\s*-\\s*(?=[A-ZÁÉÍÓÚÑ¿])"), "\n• ")
            .replace(Regex("\\.\\s*(?=(Estado de ánimo|Niveles de ansiedad|Niveles de craving|Calidad del sueño|Detonantes|Relapses|Recaídas|Riesgo calculado|Prioridades de seguimiento|Resumen|Recomendaciones|Nota):?)"), ".\n\n")
            .replace("Estado de ánimo:", "\nEstado de ánimo:\n")
            .replace("Niveles de ansiedad:", "\nAnsiedad:\n")
            .replace("Niveles de craving:", "\nCraving:\n")
            .replace("Calidad del sueño:", "\nSueño:\n")
            .replace("Detonantes identificados:", "\nDetonantes:\n")
            .replace("Relapses:", "\nRecaídas:\n")
            .replace("Riesgo calculado:", "\nRiesgo:\n")
            .replace("Prioridades de seguimiento:", "\nPrioridades:\n")
            .replace("Esto no representa un diagnóstico;", "\n\nEsto no representa un diagnóstico;")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

        val withSafetyNote = if (normalized.contains("diagnóstico clínico", ignoreCase = true)) {
            normalized
        } else {
            "$normalized\n\nNota: esta respuesta no representa diagnóstico clínico ni prescripción."
        }

        return withSafetyNote
    }

    private class MissingSessionException : Exception()

    private class EarlyFailureException(
        val failure: SupervisorDataResult.Failure,
    ) : Exception()

    private enum class SupervisorAccessState {
        Active,
        Paused,
        NoAccess,
        Excluded,
    }

    private companion object {
        const val MAX_TEXT_LENGTH = 160
        const val MINUTES_PER_DAY = 1_440.0
        const val LOCAL_NOTIFICATION_ID_PREFIX = "local_"
        const val SUPERVISION_REQUEST_TYPE_UNLINK = "unlink"
        val VALID_AI_CHAT_MODES = setOf("ai", "local")
        val VALID_AI_CHAT_RESPONSE_MODES = VALID_AI_CHAT_MODES + "fallback_local"
        const val CONSENT_STATUS_PAUSED = "paused"
        const val SUPERVISOR_AUTHORIZATION_PENDING_MESSAGE =
            "Tu cuenta de supervisor está pendiente de autorización. Un administrador revisará tu información antes de habilitar el acceso a pacientes, solicitudes y Chat IA."
        const val PATIENT_ACCESS_RESTRICTED_MESSAGE =
            "El acceso está pausado o revocado. Ya no puedes consultar notas, agenda, contactos ni métricas de este paciente."
        val VALID_ROLES = setOf("patient", "supervisor")
        val ACTIVE_SUPERVISION_STATUSES = setOf("accepted", "active", "authorized")
        val EXCLUDED_SUPERVISION_STATUSES = setOf(
            "pending",
            "requested",
            "not_requested",
            "rejected",
            "denied",
            "disabled",
        )
        val PAUSED_SUPERVISION_STATUSES = setOf(
            "paused",
            "paused_by_patient",
            "private",
        )
        val NO_ACCESS_SUPERVISION_STATUSES = setOf(
            "revoked",
            "inactive",
        )
        val PAUSED_CONSENT_STATUSES = setOf(
            "paused",
            "paused_by_patient",
            "private",
            "pending",
        )
        val NO_ACCESS_CONSENT_STATUSES = setOf(
            "revoked",
            "inactive",
            "false",
            "disabled",
        )
        val INACTIVE_PATIENT_STATUSES = setOf("inactive", "disabled", "deleted")
    }
}
