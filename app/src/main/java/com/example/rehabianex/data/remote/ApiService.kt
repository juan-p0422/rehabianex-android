package com.example.rehabianex.data.remote

import com.example.rehabianex.data.model.AuthResponse
import com.example.rehabianex.data.model.AccountDeletionRequest
import com.example.rehabianex.data.model.AccountDeletionResponse
import com.example.rehabianex.data.model.AdminAuthorizeSupervisorRequest
import com.example.rehabianex.data.model.AdminSupervisorReasonRequest
import com.example.rehabianex.data.model.AdminSupervisorResponse
import com.example.rehabianex.data.model.AdminSupervisorsResponse
import com.example.rehabianex.data.model.AgendaEventDto
import com.example.rehabianex.data.model.AchievementDto
import com.example.rehabianex.data.model.ConsentDto
import com.example.rehabianex.data.model.CreatePatientNoteRequest
import com.example.rehabianex.data.model.CreateConsentRequest
import com.example.rehabianex.data.model.CreateAgendaEventRequest
import com.example.rehabianex.data.model.CreateSupportContactRequest
import com.example.rehabianex.data.model.DataControlSummaryResponse
import com.example.rehabianex.data.model.CreateSupervisionRequest
import com.example.rehabianex.data.model.FcmTokenRegistrationRequest
import com.example.rehabianex.data.model.FcmTokenRevocationRequest
import com.example.rehabianex.data.model.FcmDemoRequest
import com.example.rehabianex.data.model.FcmDemoResponse
import com.example.rehabianex.data.model.LoginRequest
import com.example.rehabianex.data.model.LocalResourceDto
import com.example.rehabianex.data.model.MeResponse
import com.example.rehabianex.data.model.MutationResponse
import com.example.rehabianex.data.model.NotificationSettingsDto
import com.example.rehabianex.data.model.PatientAchievementDto
import com.example.rehabianex.data.model.PatientNotesResponse
import com.example.rehabianex.data.model.PrivacyNoticeReadRequest
import com.example.rehabianex.data.model.PrivacyNoticeReadResponse
import com.example.rehabianex.data.model.PrivacyNoticeResponse
import com.example.rehabianex.data.model.RefreshRequest
import com.example.rehabianex.data.model.RegisterRequest
import com.example.rehabianex.data.model.RespondSupervisionRequest
import com.example.rehabianex.data.model.ResourceListResponse
import com.example.rehabianex.data.model.SaveNotificationSettingsRequest
import com.example.rehabianex.data.model.SessionProfileDto
import com.example.rehabianex.data.model.StorePatientNoteResponse
import com.example.rehabianex.data.model.SupervisedPatientsResponse
import com.example.rehabianex.data.model.SupervisionRequestDto
import com.example.rehabianex.data.model.SupervisorChatEligibilityResponse
import com.example.rehabianex.data.model.SupervisorChatRequest
import com.example.rehabianex.data.model.SupervisorChatResponse
import com.example.rehabianex.data.model.SupervisorResolveResponse
import com.example.rehabianex.data.model.SupportContactDto
import com.example.rehabianex.data.model.UpdateAgendaEventRequest
import com.example.rehabianex.data.model.UpdateConsentRequest
import com.example.rehabianex.data.model.UpdatePatientRequest
import com.example.rehabianex.data.model.UpdateSupervisionRequest
import com.example.rehabianex.data.model.UpdateSupervisorRequest
import com.example.rehabianex.data.model.UpdateSupportContactRequest
import com.example.rehabianex.data.model.UnlinkSupervisionResponse
import retrofit2.Response
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {
    @GET("legal/privacy-notice")
    suspend fun privacyNotice(): Response<PrivacyNoticeResponse>

    @POST("legal/privacy-notice/read")
    suspend fun confirmPrivacyNoticeRead(
        @Body request: PrivacyNoticeReadRequest,
    ): Response<PrivacyNoticeReadResponse>

    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequest,
    ): Response<AuthResponse>

    @POST("auth/register")
    suspend fun register(
        @Body request: RegisterRequest,
    ): Response<AuthResponse>

    @POST("auth/refresh")
    suspend fun refresh(
        @Body request: RefreshRequest,
    ): Response<AuthResponse>

    @POST("auth/logout")
    suspend fun logout(): Response<MutationResponse<Unit>>

    @GET("data-control/summary")
    suspend fun dataControlSummary(): Response<DataControlSummaryResponse>

    @GET("data-control/export")
    suspend fun exportOwnData(): Response<ResponseBody>

    @POST("data-control/account-deletion-requests")
    suspend fun requestAccountDeletion(
        @Body request: AccountDeletionRequest,
    ): Response<AccountDeletionResponse>

    @POST("notifications/fcm-token")
    suspend fun registerFcmToken(
        @Body request: FcmTokenRegistrationRequest,
    ): Response<Unit>

    @HTTP(method = "DELETE", path = "notifications/fcm-token", hasBody = true)
    suspend fun revokeFcmToken(
        @Body request: FcmTokenRevocationRequest,
    ): Response<Unit>

    @POST("notifications/demo")
    suspend fun sendFcmDemo(
        @Body request: FcmDemoRequest,
    ): Response<FcmDemoResponse>

    @GET("auth/me")
    suspend fun me(): Response<MeResponse>

    @GET("admin/supervisors")
    suspend fun adminSupervisors(
        @Query("q") query: String? = null,
        @Query("status") status: String? = null,
        @Query("authorized") authorized: Boolean? = null,
        @Query("verified") verified: Boolean? = null,
        @Query("limit") limit: Int = 50,
        @Query("page") page: Int = 1,
    ): Response<AdminSupervisorsResponse>

    @GET("admin/supervisors/{uid}")
    suspend fun adminSupervisorByUid(
        @Path("uid") supervisorUid: String,
    ): Response<AdminSupervisorResponse>

    @PATCH("admin/supervisors/{uid}/authorize")
    suspend fun authorizeAdminSupervisor(
        @Path("uid") supervisorUid: String,
        @Body request: AdminAuthorizeSupervisorRequest,
    ): Response<AdminSupervisorResponse>

    @PATCH("admin/supervisors/{uid}/reject")
    suspend fun rejectAdminSupervisor(
        @Path("uid") supervisorUid: String,
        @Body request: AdminSupervisorReasonRequest,
    ): Response<AdminSupervisorResponse>

    @PATCH("admin/supervisors/{uid}/suspend")
    suspend fun suspendAdminSupervisor(
        @Path("uid") supervisorUid: String,
        @Body request: AdminSupervisorReasonRequest,
    ): Response<AdminSupervisorResponse>

    @PATCH("admin/supervisors/{uid}/reactivate")
    suspend fun reactivateAdminSupervisor(
        @Path("uid") supervisorUid: String,
    ): Response<AdminSupervisorResponse>

    @POST("patients/{patient_uid}/notes")
    suspend fun createPatientNote(
        @Path("patient_uid") patientUid: String,
        @Body request: CreatePatientNoteRequest,
    ): Response<StorePatientNoteResponse>

    @PATCH("patients/{patient_uid}")
    suspend fun updatePatient(
        @Path("patient_uid") patientUid: String,
        @Body request: UpdatePatientRequest,
    ): Response<MutationResponse<SessionProfileDto>>

    @PATCH("supervisors/{supervisor_uid}")
    suspend fun updateSupervisor(
        @Path("supervisor_uid") supervisorUid: String,
        @Body request: UpdateSupervisorRequest,
    ): Response<MutationResponse<SessionProfileDto>>

    @GET("patients/{patient_uid}")
    suspend fun patientById(
        @Path("patient_uid") patientUid: String,
    ): Response<MutationResponse<SessionProfileDto>>

    @GET("support-contacts")
    suspend fun supportContacts(
        @Query("patient_uid") patientUid: String,
        @Query("limit") limit: Int = 50,
        @Query("include_deleted") includeDeleted: Boolean = false,
    ): Response<ResourceListResponse<SupportContactDto>>

    @POST("support-contacts")
    suspend fun createSupportContact(
        @Body request: CreateSupportContactRequest,
    ): Response<MutationResponse<SupportContactDto>>

    @PATCH("support-contacts/{id}")
    suspend fun updateSupportContact(
        @Path("id") contactId: String,
        @Body request: UpdateSupportContactRequest,
    ): Response<MutationResponse<SupportContactDto>>

    @GET("local-resources")
    suspend fun localResources(
        @Query("city") city: String? = null,
        @Query("type") type: String? = null,
        @Query("limit") limit: Int = 100,
        @Query("include_deleted") includeDeleted: Boolean = false,
    ): Response<ResourceListResponse<LocalResourceDto>>

    @GET("agenda-events")
    suspend fun agendaEvents(
        @Query("patient_uid") patientUid: String,
        @Query("status") status: String = "scheduled",
        @Query("limit") limit: Int = 50,
        @Query("include_deleted") includeDeleted: Boolean = false,
    ): Response<ResourceListResponse<AgendaEventDto>>

    @POST("agenda-events")
    suspend fun createAgendaEvent(
        @Body request: CreateAgendaEventRequest,
    ): Response<MutationResponse<AgendaEventDto>>

    @PATCH("agenda-events/{id}")
    suspend fun updateAgendaEvent(
        @Path("id") eventId: String,
        @Body request: UpdateAgendaEventRequest,
    ): Response<MutationResponse<AgendaEventDto>>

    @DELETE("agenda-events/{id}")
    suspend fun deleteAgendaEvent(
        @Path("id") eventId: String,
    ): Response<MutationResponse<Unit>>

    @GET("achievements")
    suspend fun achievements(
        @Query("limit") limit: Int = 100,
        @Query("include_deleted") includeDeleted: Boolean = false,
    ): Response<ResourceListResponse<AchievementDto>>

    @GET("patient-achievements")
    suspend fun patientAchievements(
        @Query("patient_uid") patientUid: String,
        @Query("limit") limit: Int = 100,
        @Query("include_deleted") includeDeleted: Boolean = false,
    ): Response<ResourceListResponse<PatientAchievementDto>>

    @POST("supervision-requests")
    suspend fun createSupervisionRequest(
        @Body request: CreateSupervisionRequest,
    ): Response<MutationResponse<Unit>>

    @POST("consents")
    suspend fun createConsent(
        @Body request: CreateConsentRequest,
    ): Response<MutationResponse<Unit>>

    @GET("consents")
    suspend fun consents(
        @Query("patient_uid") patientUid: String? = null,
        @Query("supervisor_uid") supervisorUid: String? = null,
        @Query("limit") limit: Int = 50,
    ): Response<ResourceListResponse<ConsentDto>>

    @PATCH("consents/{id}")
    suspend fun updateConsent(
        @Path("id") consentId: String,
        @Body request: UpdateConsentRequest,
    ): Response<MutationResponse<ConsentDto>>

    @GET("notification-settings")
    suspend fun notificationSettings(): Response<ResourceListResponse<NotificationSettingsDto>>

    @POST("notification-settings")
    suspend fun createNotificationSettings(
        @Body request: SaveNotificationSettingsRequest,
    ): Response<MutationResponse<NotificationSettingsDto>>

    @PATCH("notification-settings/{id}")
    suspend fun updateNotificationSettings(
        @Path("id") settingsId: String,
        @Body request: SaveNotificationSettingsRequest,
    ): Response<MutationResponse<NotificationSettingsDto>>

    @GET("supervisors/resolve")
    suspend fun resolveSupervisorCode(
        @Query("code") code: String,
    ): Response<SupervisorResolveResponse>

    @GET("supervisors/{supervisor_uid}/patients")
    suspend fun supervisedPatients(
        @Path("supervisor_uid") supervisorUid: String,
    ): Response<SupervisedPatientsResponse>

    @POST("supervisors/{supervisor_uid}/patients/{patient_uid}/unlink")
    suspend fun unlinkSupervision(
        @Path("supervisor_uid") supervisorUid: String,
        @Path("patient_uid") patientUid: String,
    ): Response<UnlinkSupervisionResponse>

    @GET("patients/{patient_uid}/notes")
    suspend fun patientNotesByPatient(
        @Path("patient_uid") patientUid: String,
    ): Response<PatientNotesResponse>

    @GET("patient-notes")
    suspend fun patientNotesForSync(
        @Query("patient_uid") patientUid: String,
        @Query("limit") limit: Int = 100,
        @Query("include_deleted") includeDeleted: Boolean = true,
    ): Response<ResourceListResponse<com.example.rehabianex.data.model.PatientNoteDto>>

    @GET("supervision-requests")
    suspend fun supervisionRequests(
        @Query("status") status: String = "pending",
        @Query("limit") limit: Int = 50,
    ): Response<ResourceListResponse<SupervisionRequestDto>>

    @PATCH("supervision-requests/{request_id}/respond")
    suspend fun respondSupervisionRequest(
        @Path("request_id") requestId: String,
        @Body request: RespondSupervisionRequest,
    ): Response<MutationResponse<SupervisionRequestDto>>

    @PATCH("supervision-requests/{request_id}")
    suspend fun updateSupervisionRequest(
        @Path("request_id") requestId: String,
        @Body request: UpdateSupervisionRequest,
    ): Response<MutationResponse<SupervisionRequestDto>>

    @POST("ai/supervisor-chat")
    suspend fun supervisorChat(
        @Body request: SupervisorChatRequest,
    ): Response<SupervisorChatResponse>

    @GET("ai/supervisor-chat/eligibility")
    suspend fun supervisorChatEligibility(): Response<SupervisorChatEligibilityResponse>
}
