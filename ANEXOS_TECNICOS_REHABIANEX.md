# Anexos técnicos de código Android de RehabiAnex

Los siguientes anexos fueron seleccionados directamente del proyecto Android revisado. Los fragmentos se presentan de forma abreviada para destacar la lógica esencial y no incluyen credenciales, claves privadas ni configuraciones sensibles. Cuando se omiten líneas intermedias se indica expresamente mediante el comentario `// ... código omitido por brevedad`.

## Anexo 1. Registro emocional del paciente

### Descripción

Este componente recibe los valores capturados en el registro emocional, valida sus rangos y listas permitidas, sanitiza la nota libre y construye la solicitud que se entrega al repositorio. Es relevante porque concentra las reglas de entrada de ánimo, ansiedad, craving, energía, sueño, recaída, detonantes y acciones de afrontamiento antes de persistir la información.

### Archivo o ubicación

`app/src/main/java/com/example/rehabianex/ui/screens/patient/PatientDataViewModels.kt`

### Fragmento de código

```kotlin
class EmotionalCheckinViewModel(
    private val repository: PatientRepository,
) : ViewModel() {
    private val _saveState = MutableStateFlow<DailyNoteSaveUiState>(DailyNoteSaveUiState.Idle)
    val saveState: StateFlow<DailyNoteSaveUiState> = _saveState.asStateFlow()

    fun save(input: DailyNoteFormInput) {
        if (_saveState.value is DailyNoteSaveUiState.Loading) return

        val request = input.toRequestOrError()
        if (request == null) return

        viewModelScope.launch {
            _saveState.value = DailyNoteSaveUiState.Loading
            _saveState.value = when (val result = repository.saveDailyNote(request)) {
                is PatientDataResult.Success -> {
                    invalidatePatientRefreshes()
                    DailyNoteSaveUiState.Success(result.data)
                }
                is PatientDataResult.Failure -> DailyNoteSaveUiState.Error(
                    result.message,
                    result.fieldErrors,
                )
            }
        }
    }

    // ... código omitido por brevedad

    private fun DailyNoteFormInput.toRequestOrError(): CreatePatientNoteRequest? {
        val cleanMood = mood.takeIf { it in ALLOWED_MOODS }
        val anxietyValue = anxiety.toIntOrNull()?.takeIf { it in 0..5 }
        val cravingValue = craving.toIntOrNull()?.takeIf { it in 0..5 }
        val energyValue = energy.toIntOrNull()?.takeIf { it in 0..5 }
        val sleepQualityValue = sleepQuality.toIntOrNull()?.takeIf { it in 0..5 }
        val keptSobriety = when (sober) {
            "Sí" -> true
            "No" -> false
            else -> null
        }
        val safeTriggers = triggers
            .filter { it in ALLOWED_TRIGGERS }
            .take(8)
        val safeCopingActions = copingActions
            .filter { it in ALLOWED_COPING_ACTIONS }
            .take(8)
        val safeNote = note
            .filterNot { it.isISOControl() }
            .replace("<", "")
            .replace(">", "")
            .trim()
            .take(1000)

        val message = when {
            cleanMood == null -> "Selecciona un estado de ánimo válido."
            anxietyValue == null -> "Selecciona un nivel de ansiedad válido."
            cravingValue == null -> "Selecciona un nivel de craving válido."
            energyValue == null -> "Selecciona un nivel de energía válido."
            sleepQualityValue == null -> "Selecciona cómo dormiste."
            keptSobriety == null -> "Indica si mantuviste tu sobriedad."
            else -> null
        }

        if (message != null) {
            _saveState.value = DailyNoteSaveUiState.Error(message)
            return null
        }

        return CreatePatientNoteRequest(
            mood = cleanMood!!.lowercase(),
            moodScore = cleanMood.toMoodScore(),
            anxietyLevel = anxietyValue!! * 2,
            cravingLevel = cravingValue!! * 2,
            energyLevel = energyValue!! * 2,
            sleepQuality = sleepQualityValue!! * 2,
            hadRelapse = !keptSobriety!!,
            triggers = safeTriggers,
            copingActions = safeCopingActions,
            noteText = safeNote,
        )
    }
}
```

### Explicación técnica

- **Recibe:** un `DailyNoteFormInput` procedente de la pantalla Compose.
- **Procesa:** valida escalas de 0 a 5, restringe valores permitidos, limita listas y elimina caracteres de control o etiquetas de la nota.
- **Devuelve o modifica:** actualiza un `StateFlow` con estados de carga, éxito o error y envía un `CreatePatientNoteRequest` al repositorio.
- **Relación con otros módulos:** el repositorio decide si el registro se envía al backend o se almacena temporalmente en Room.
- **Relevancia:** evita que información inválida llegue a las capas de persistencia y sincronización.

## Anexo 2. Entidad y DAO de Room para registros emocionales

### Descripción

La entidad `PatientNoteEntity` representa el registro emocional dentro de la base local y conserva tanto los datos funcionales como el estado de sincronización. El DAO consulta los registros activos, identifica operaciones pendientes y permite recuperar sincronizaciones interrumpidas.

### Archivo o ubicación

- `app/src/main/java/com/example/rehabianex/data/local/db/OfflineEntities.kt`
- `app/src/main/java/com/example/rehabianex/data/local/db/OfflineDaos.kt`

### Fragmento de código

```kotlin
@Entity(
    tableName = "patient_notes",
    indices = [
        Index("ownerUid"),
        Index("serverId"),
        Index("pendingSync"),
        Index("clientMutationId"),
        Index("syncState"),
    ],
)
data class PatientNoteEntity(
    @PrimaryKey val cacheKey: String,
    val serverId: String?,
    val ownerUid: String,
    val mood: String?,
    val moodScore: Int?,
    val anxietyLevel: Int?,
    val cravingLevel: Int?,
    val energyLevel: Int?,
    val sleepQuality: Int?,
    val hadRelapse: Boolean?,
    val triggers: List<String>,
    val copingActions: List<String>,
    val noteText: String?,
    val aiRiskLevel: String?,
    val aiRiskScore: Double?,
    val createdAt: String?,
    val updatedAt: String?,
    val deletedAt: String?,
    val clientMutationId: String?,
    @ColumnInfo(defaultValue = "'synced'")
    val syncState: PatientNoteSyncState,
    val pendingSync: Boolean,
    val syncAttempts: Int,
    val lastSyncError: String?,
    val lastSyncedAt: Long?,
)

enum class PatientNoteSyncState(val persistedValue: String) {
    Pending("pending"),
    Syncing("syncing"),
    RetryableError("retryable_error"),
    Ambiguous("ambiguous"),
    PermanentError("permanent_error"),
    Synced("synced"),
}
```

```kotlin
@Dao
interface PatientNoteDao {
    @Query("SELECT * FROM patient_notes WHERE ownerUid = :ownerUid AND deletedAt IS NULL ORDER BY createdAt DESC")
    suspend fun activeForOwner(ownerUid: String): List<PatientNoteEntity>

    @Query("""
        SELECT * FROM patient_notes
        WHERE ownerUid = :ownerUid
          AND (
            syncState IN ('pending', 'retryable_error')
            OR (syncState = 'ambiguous' AND clientMutationId IS NOT NULL)
          )
        ORDER BY createdAt
    """)
    suspend fun readyForSync(ownerUid: String): List<PatientNoteEntity>

    @Query("SELECT COUNT(*) FROM patient_notes WHERE ownerUid = :ownerUid AND syncState != 'synced'")
    suspend fun pendingCount(ownerUid: String): Int

    @Query("""
        UPDATE patient_notes
        SET syncState = 'retryable_error', pendingSync = 1,
            lastSyncError = :message
        WHERE ownerUid = :ownerUid AND syncState = 'syncing'
    """)
    suspend fun recoverInterruptedSync(ownerUid: String, message: String)

    @Upsert
    suspend fun upsert(item: PatientNoteEntity)

    @Query("DELETE FROM patient_notes WHERE cacheKey = :cacheKey")
    suspend fun deleteByCacheKey(cacheKey: String)
}
```

### Explicación técnica

- **Recibe:** registros asociados al UID del paciente y estados de sincronización.
- **Procesa:** organiza consultas por propietario, distingue operaciones sincronizadas, reintentables, ambiguas o permanentes y mantiene una clave de mutación del cliente.
- **Devuelve o modifica:** devuelve listas y conteos; también inserta, actualiza o elimina registros locales.
- **Relación con otros módulos:** es utilizado por `PatientRepository` y `PatientSyncWorker`.
- **Relevancia:** proporciona persistencia parcial sin conexión para el registro emocional y evita mezclar datos entre usuarios.

## Anexo 3. Protección de la base Room con SQLCipher y Android Keystore

### Descripción

La base Room se abre mediante SQLCipher y una passphrase aleatoria protegida con una clave AES no exportable del Android Keystore. La configuración también detecta bases heredadas sin cifrar y rechaza su uso en compilaciones de tipo release.

### Archivo o ubicación

- `app/src/main/java/com/example/rehabianex/data/local/db/RehabiAnexDatabase.kt`
- `app/src/main/java/com/example/rehabianex/data/local/security/DatabasePassphraseProvider.kt`

### Fragmento de código

```kotlin
private fun buildEncryptedDatabase(
    context: Context,
    databaseName: String,
    isDebug: Boolean,
): RehabiAnexDatabase {
    Logger.setTarget(NoopTarget())
    System.loadLibrary(SQLCIPHER_LIBRARY)
    val databaseFile = context.getDatabasePath(databaseName)
    when (
        legacyDatabaseAction(
            databaseExists = databaseFile.exists(),
            hasPlaintextHeader = databaseFile.hasPlaintextSqliteHeader(),
            isDebug = isDebug,
        )
    ) {
        LegacyDatabaseAction.Continue -> Unit
        LegacyDatabaseAction.DeletePlaintextDebugDatabase -> {
            deleteDebugPlaintextDatabase(context, databaseName)
        }
        LegacyDatabaseAction.RejectPlaintextReleaseDatabase -> {
            throw IllegalStateException(
                "La base local heredada no está cifrada; release requiere una migración explícita.",
            )
        }
    }

    val databaseExistsAfterPolicy = context.getDatabasePath(databaseName).exists()
    val passphrase = DatabasePassphraseProvider(context).getOrCreate(
        databaseAlreadyExists = databaseExistsAfterPolicy,
    )
    val factory = SupportOpenHelperFactory(passphrase.copyOf())
    passphrase.fill(0)
    return Room.databaseBuilder(
        context,
        RehabiAnexDatabase::class.java,
        databaseName,
    )
        .openHelperFactory(factory)
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        .build()
        .also { encryptedDatabase ->
            encryptedDatabase.openHelper.writableDatabase
        }
}
```

```kotlin
@Synchronized
fun getOrCreate(databaseAlreadyExists: Boolean): ByteArray {
    if (keyEnvelopeFile.exists()) {
        return decryptEnvelope(keyEnvelopeFile.readBytes())
    }
    if (databaseAlreadyExists) {
        throw DatabaseKeyUnavailableException(
            "La clave protegida de la base local no está disponible.",
        )
    }

    val passphrase = ByteArray(PASSPHRASE_SIZE_BYTES).also(SecureRandom()::nextBytes)
    try {
        persistEnvelope(encryptPassphrase(passphrase))
    } catch (error: Exception) {
        passphrase.fill(0)
        throw DatabaseKeyUnavailableException(
            "No fue posible proteger la clave de la base local.",
            error,
        )
    }
    return passphrase
}

private fun encryptPassphrase(passphrase: ByteArray): DatabaseKeyEnvelope {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey())
    cipher.updateAAD(ENVELOPE_AAD)
    return DatabaseKeyEnvelope(
        initializationVector = cipher.iv,
        cipherText = cipher.doFinal(passphrase),
    )
}
```

### Explicación técnica

- **Recibe:** el contexto de Android, el nombre de la base y el estado de la base existente.
- **Procesa:** obtiene o genera una passphrase de 256 bits, la protege con AES-GCM y configura el `SupportOpenHelperFactory` de SQLCipher.
- **Devuelve o modifica:** devuelve una instancia de `RehabiAnexDatabase` cifrada y guarda únicamente el sobre criptográfico en `noBackupFilesDir`.
- **Relación con otros módulos:** protege las entidades Room usadas por registros emocionales, agenda, contactos, configuraciones y cachés.
- **Relevancia:** reduce la exposición de información sensible almacenada localmente. No equivale a cifrado de extremo a extremo.

## Anexo 4. Sincronización diferida de registros con WorkManager

### Descripción

El `PatientSyncWorker` programa la sincronización de registros emocionales pendientes cuando existe conectividad. Utiliza trabajo único por paciente y retroceso exponencial, lo cual evita duplicar tareas y permite reintentar fallos temporales.

### Archivo o ubicación

`app/src/main/java/com/example/rehabianex/sync/PatientSyncWorker.kt`

### Fragmento de código

```kotlin
class PatientSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val ownerUid = inputData.getString(INPUT_OWNER_UID)
            ?.takeIf { it.isNotBlank() }
            ?: return Result.failure()
        val repository = AppContainer(applicationContext).patientRepository
        val result = repository.syncPendingPatientNotes(ownerUid)
        return when {
            result.ownerMismatch -> Result.success()
            result.shouldRetry -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        fun enqueue(context: Context, ownerUid: String) {
            val request = OneTimeWorkRequestBuilder<PatientSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setInputData(workDataOf(INPUT_OWNER_UID to ownerUid))
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    MIN_BACKOFF_SECONDS,
                    TimeUnit.SECONDS,
                )
                .addTag(WORK_TAG)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "$UNIQUE_PREFIX-$ownerUid",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context, ownerUid: String) {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork("$UNIQUE_PREFIX-$ownerUid")
        }

        private const val UNIQUE_PREFIX = "rehabianex-patient-note-sync"
        private const val WORK_TAG = "rehabianex-offline-sync"
        const val INPUT_OWNER_UID = "ownerUid"
        private const val MIN_BACKOFF_SECONDS = 30L
    }
}
```

### Explicación técnica

- **Recibe:** el UID propietario mediante `inputData`.
- **Procesa:** exige conexión, verifica el resultado del lote y aplica reintento exponencial cuando corresponde.
- **Devuelve o modifica:** devuelve `success`, `retry` o `failure` a WorkManager; el repositorio modifica los estados de las notas en Room.
- **Relación con otros módulos:** se agenda desde `PatientRepository` cuando una nota se guarda sin conexión.
- **Relevancia:** implementa sincronización diferida parcial. No proporciona funcionamiento offline total para agenda, contactos, consentimiento o Chatbot IA.

## Anexo 5. Autenticación, registro y aceptación obligatoria del aviso de privacidad

### Descripción

El repositorio envía las solicitudes de inicio de sesión y registro al backend, valida la sesión devuelta y conserva el ID token utilizado para autenticar peticiones posteriores. En el registro, el validador impide crear la solicitud si no se acepta expresamente la versión vigente del aviso de privacidad.

### Archivo o ubicación

- `app/src/main/java/com/example/rehabianex/data/repository/AuthRepository.kt`
- `app/src/main/java/com/example/rehabianex/validation/AuthValidator.kt`

### Fragmento de código

```kotlin
suspend fun login(request: LoginRequest): AuthResult = executeRequest {
    apiService.login(request)
}

suspend fun register(request: RegisterRequest): AuthResult = executeRequest(
    allowTokenlessAccount = true,
) {
    apiService.register(request)
}

// ... código omitido por brevedad

private suspend fun readSuccessfulResponse(
    response: Response<AuthResponse>,
    allowTokenlessAccount: Boolean,
): AuthResult {
    val body = response.body()
    val tokens = body?.resolvedTokens()
    val auth = body?.resolvedAuth()
    val profile = body?.resolvedProfile()
    val idToken = tokens?.idToken?.takeIf {
        it.isSafeCredential(MAX_TOKEN_LENGTH)
    }
    val refreshToken = tokens?.refreshToken?.takeIf { it.isNotBlank() }
    val uid = auth?.uid?.takeIf {
        it.isSafeCredential(MAX_UID_LENGTH)
    }
    val role = (auth?.role ?: profile?.role)?.takeIf { it in VALID_ROLES }

    // ... código omitido por brevedad

    if (
        idToken == null ||
        uid == null ||
        role == null ||
        (refreshToken != null && !refreshToken.isSafeCredential(MAX_TOKEN_LENGTH))
    ) {
        return AuthResult.Failure(
            "El servidor devolvió una sesión incompleta. Contacta a soporte.",
        )
    }

    val session = AuthSession(
        idToken = idToken,
        refreshToken = refreshToken,
        uid = uid,
        role = role,
        email = email,
        displayName = displayName,
    )

    // ... código omitido por brevedad
}
```

```kotlin
if (!input.acceptedPrivacy) {
    errors["privacy"] = "Debes aceptar el aviso de privacidad."
}

return if (errors.isEmpty()) {
    ValidationResult(
        value = RegisterRequest(
            email = email,
            password = input.password,
            fullName = fullName,
            role = input.role,
            phone = phone,
            gender = gender,
            age = age,
            supervisorType = supervisorType,
            privacyNoticeAccepted = true,
            privacyNoticeVersion = PRIVACY_NOTICE_VERSION,
        ),
    )
} else {
    ValidationResult(fieldErrors = errors)
}
```

### Explicación técnica

- **Recibe:** credenciales o datos de registro y la confirmación del aviso de privacidad.
- **Procesa:** delega la autenticación al backend, valida token, UID y rol, y rechaza registros sin aceptación explícita.
- **Devuelve o modifica:** crea un `AuthSession` o devuelve errores de validación.
- **Relación con otros módulos:** la sesión alimenta el interceptor Bearer y la navegación por roles.
- **Relevancia:** el cliente Android no usa directamente el SDK de Firebase Authentication; consume la API Laravel, que devuelve los tokens de Firebase empleados por la aplicación.

## Anexo 6. Configuración de privacidad visual del paciente

### Descripción

El perfil permite elegir entre nombre visible, nickname o presentación anónima. El ViewModel valida el nombre mostrado y envía al repositorio únicamente el estado necesario para actualizar la privacidad visual.

### Archivo o ubicación

`app/src/main/java/com/example/rehabianex/ui/screens/patient/PatientDataViewModels.kt`

### Fragmento de código

```kotlin
fun savePrivacy(input: PrivacyFormInput) {
    if (_privacyActionState.value is FormSaveUiState.Loading) return

    val anonymous = input.mode == PRIVACY_ANONYMOUS
    val currentProfileName = (_uiState.value as? PatientProfileUiState.Content)
        ?.profile
        ?.registeredName
        .orEmpty()
    val displayNameSource = when (input.mode) {
        PRIVACY_VISIBLE -> currentProfileName
        PRIVACY_NICKNAME -> input.displayName
        else -> "Paciente anónimo"
    }
    val cleanDisplayName = displayNameSource.cleanFormText(maxLength = 80)
    if (input.mode == PRIVACY_NICKNAME &&
        (cleanDisplayName == null || !cleanDisplayName.isSafeDisplayName())
    ) {
        _privacyActionState.value = FormSaveUiState.Error(
            message = "Revisa tu modo de privacidad.",
            fieldErrors = mapOf(
                "displayName" to "Escribe un nombre o nickname válido.",
            ),
        )
        return
    }

    viewModelScope.launch {
        _privacyActionState.value = FormSaveUiState.Loading
        _privacyActionState.value = when (
            val result = repository.updatePrivacyMode(
                displayName = cleanDisplayName ?: "Paciente anónimo",
                anonymous = anonymous,
                useNickname = input.mode == PRIVACY_NICKNAME,
            )
        ) {
            is PatientDataResult.Success -> {
                invalidatePatientRefreshes()
                repository.loadCachedProfileData()?.let { cached ->
                    _uiState.value = PatientProfileUiState.Content(
                        profile = cached.data,
                        isRefreshing = true,
                    )
                }
                refreshIfStale(force = true)
                FormSaveUiState.Success(result.data)
            }
            is PatientDataResult.Failure -> profileActionFailure(result)
        }
    }
}
```

### Explicación técnica

- **Recibe:** el modo de privacidad y, cuando corresponde, un nickname.
- **Procesa:** selecciona el nombre seguro, limita su longitud y valida caracteres antes de actualizarlo.
- **Devuelve o modifica:** actualiza el estado de la interfaz y solicita al repositorio modificar el perfil remoto y la caché local.
- **Relación con otros módulos:** afecta la identidad presentada en perfil y supervisión autorizada.
- **Relevancia:** reduce la exposición visual del nombre; no constituye autenticación anónima ni elimina la cuenta registrada.

## Anexo 7. Vinculación del paciente mediante código de supervisor

### Descripción

El ViewModel de vinculación normaliza el código ingresado, consulta al backend para presentar una vista previa del supervisor y, tras una segunda confirmación, crea la solicitud de vinculación. La relación no se activa unilateralmente, pues requiere respuesta del supervisor.

### Archivo o ubicación

`app/src/main/java/com/example/rehabianex/ui/screens/patient/PatientDataViewModels.kt`

### Fragmento de código

```kotlin
class SupervisorLinkViewModel(
    private val repository: PatientRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<SupervisorLinkUiState>(SupervisorLinkUiState.Idle)
    val uiState: StateFlow<SupervisorLinkUiState> = _uiState.asStateFlow()
    private var lastKnownStatus = PatientSupervisionState.NoSupervisor

    init {
        refreshStatus()
    }

    fun requestLink(supervisorCode: String) {
        if (_uiState.value is SupervisorLinkUiState.Loading) return

        viewModelScope.launch {
            val normalizedCode = supervisorCode.trim().uppercase()
            val currentPreview = (_uiState.value as? SupervisorLinkUiState.Preview)
                ?.supervisor
            _uiState.value = SupervisorLinkUiState.Loading
            if (currentPreview?.code == normalizedCode) {
                submitLink(currentPreview.supervisorUid)
                return@launch
            }

            when (val result = repository.resolveSupervisorCode(normalizedCode)) {
                is PatientDataResult.Success -> {
                    _uiState.value = SupervisorLinkUiState.Preview(result.data)
                }
                is PatientDataResult.Failure -> {
                    _uiState.value = SupervisorLinkUiState.Error(
                        result.message,
                        result.fieldErrors,
                    )
                }
            }
        }
    }

    private suspend fun submitLink(supervisorReference: String) {
        _uiState.value = when (
            val result = repository.requestSupervisorLink(supervisorReference)
        ) {
            is PatientDataResult.Success -> {
                invalidatePatientRefreshes()
                SupervisorLinkUiState.Success(result.data)
            }
            is PatientDataResult.Failure -> SupervisorLinkUiState.Error(
                result.message,
                result.fieldErrors,
            )
        }
    }
}
```

### Explicación técnica

- **Recibe:** un código de supervisor introducido por el paciente.
- **Procesa:** normaliza el código, resuelve la cuenta, muestra una vista previa y solicita confirmación.
- **Devuelve o modifica:** actualiza `SupervisorLinkUiState` y crea una solicitud pendiente en el backend.
- **Relación con otros módulos:** conecta el perfil del paciente con las pantallas de solicitudes del supervisor y precede al consentimiento.
- **Relevancia:** separa la solicitud de vínculo de la autorización efectiva para consultar datos.

## Anexo 8. Activación, pausa y revocación del consentimiento de supervisión

### Descripción

El repositorio administra el consentimiento granular después de confirmar que existe un supervisor vinculado. Puede crear un consentimiento nuevo, activar o pausar uno existente y revocar expresamente el acceso compartido.

### Archivo o ubicación

`app/src/main/java/com/example/rehabianex/data/repository/PatientRepository.kt`

### Fragmento de código

```kotlin
suspend fun setSupervisorConsent(
    enabled: Boolean,
): PatientDataResult<String> = safeRequest(
    defaultUnexpectedMessage = "Ocurrió un error inesperado al actualizar el consentimiento.",
) {
    val profile = readMeProfile()
    val supervisorUid = profile.supervisorUid.safeIdentifierOrNull()
        ?: throw EarlyFailureException(
            PatientDataResult.Failure(
                "Primero vincula un supervisor para administrar consentimiento.",
            ),
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
        throw EarlyFailureException(response.toPatientFailure())
    }
}

suspend fun revokeSupervisorConsent(): PatientDataResult<String> = safeRequest(
    defaultUnexpectedMessage = "Ocurrió un error inesperado al revocar el consentimiento.",
) {
    val profile = readMeProfile()
    val supervisorUid = profile.supervisorUid.safeIdentifierOrNull()
        ?: throw EarlyFailureException(
            PatientDataResult.Failure(
                "No tienes un supervisor vinculado con consentimiento para revocar.",
            ),
        )
    val currentConsentId = loadCurrentConsentOrNull(supervisorUid)?.resourceId()
        ?: throw EarlyFailureException(
            PatientDataResult.Failure(
                "No existe un consentimiento vigente para revocar.",
            ),
        )
    val response = authorizedCall {
        apiService.updateConsent(
            consentId = currentConsentId,
            request = UpdateConsentRequest(explicitConsent = false),
        )
    }

    // ... validación de la respuesta omitida por brevedad

    refreshPatientSupervision()
    "Consentimiento revocado. Tu supervisor ya no puede consultar los datos compartidos."
}

val DEFAULT_CONSENT_SCOPE = listOf(
    "patient_notes",
    "agenda_events",
    "support_contacts",
    "ai_chat_summary",
)
```

### Explicación técnica

- **Recibe:** el estado deseado del consentimiento y el supervisor asociado al perfil.
- **Procesa:** localiza el consentimiento vigente, crea o actualiza el recurso remoto y aplica los alcances autorizados.
- **Devuelve o modifica:** actualiza el estado de supervisión y devuelve un mensaje de confirmación.
- **Relación con otros módulos:** controla el acceso a notas, agenda, contactos de apoyo y resumen para Chatbot IA.
- **Relevancia:** materializa la capacidad del paciente de activar, pausar o revocar permisos sin eliminar necesariamente la vinculación.

## Anexo 9. Chatbot IA con validación de elegibilidad del supervisor

### Descripción

El repositorio comprueba que la cuenta supervisora esté autorizada y que exista al menos un paciente elegible antes de habilitar el Chatbot IA. La pregunta se sanitiza, se limita a modos permitidos y se envía al endpoint protegido del backend.

### Archivo o ubicación

`app/src/main/java/com/example/rehabianex/data/repository/SupervisorRepository.kt`

### Fragmento de código

```kotlin
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

    // ... validación de longitud y modo omitida por brevedad

    val supervisorUid = sessionUid()
    val knownEligibility = chatEligibility?.takeIf {
        it.supervisorUid == supervisorUid
    }
    if (knownEligibility?.authorized == false) {
        throw EarlyFailureException(
            SupervisorDataResult.Failure(
                SUPERVISOR_AUTHORIZATION_PENDING_MESSAGE,
                403,
            ),
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
```

### Explicación técnica

- **Recibe:** una pregunta del supervisor y el modo `ai` o `local`.
- **Procesa:** sanitiza el texto, valida autorización y elegibilidad, y limita la respuesta recibida.
- **Devuelve o modifica:** devuelve un `SupervisorChatMessage` o un error controlado 403/409/422.
- **Relación con otros módulos:** depende de la sesión supervisora, el consentimiento `ai_chat_summary` y el endpoint Laravel que integra el proveedor de IA.
- **Relevancia:** impide usar el Chatbot sin cuenta autorizada ni pacientes con permiso activo. El historial persistente permanece deshabilitado por privacidad.

## Anexo 10. Programación de recordatorios locales

### Descripción

`NotificationScheduler` centraliza los recordatorios locales del paciente y los eventos de agenda. Los avisos utilizan textos discretos y abren rutas internas específicas sin revelar información sensible en la notificación.

### Archivo o ubicación

`app/src/main/java/com/example/rehabianex/notifications/NotificationScheduler.kt`

### Fragmento de código

```kotlin
object NotificationScheduler {
    fun schedulePatient(
        context: Context,
        settings: NotificationSettingsData,
        events: List<AgendaEvent>,
    ): Int {
        var scheduled = 0

        scheduled += scheduleOrCancel(
            context = context,
            enabled = settings.dailyNoteEnabled,
            actionKey = AgendaNotificationHelper.REMINDER_DAILY_NOTE,
            time = settings.dailyNoteTime,
            route = Routes.EmotionalCheckin,
        )
        scheduled += scheduleOrCancel(
            context = context,
            enabled = settings.soberDayEnabled,
            actionKey = AgendaNotificationHelper.REMINDER_SOBER_DAY,
            time = settings.soberDayTime,
            route = Routes.PatientHome,
        )
        scheduled += scheduleOrCancel(
            context = context,
            enabled = settings.achievementEnabled,
            actionKey = AgendaNotificationHelper.REMINDER_ACHIEVEMENT,
            time = settings.achievementTime,
            route = Routes.Achievements,
        )
        scheduled += scheduleOrCancel(
            context = context,
            enabled = settings.motivationalEnabled,
            actionKey = AgendaNotificationHelper.REMINDER_MOTIVATIONAL,
            time = settings.motivationalTime,
            route = Routes.PatientHome,
        )

        events.forEach { event ->
            if (settings.eventRemindersEnabled) {
                val startsAtMillis = event.startsAtMillis() ?: return@forEach
                if (AgendaNotificationHelper.scheduleEventReminder(
                        context = context,
                        eventId = event.id,
                        startsAtMillis = startsAtMillis,
                        durationMinutes = event.durationMinutes,
                    )
                ) {
                    scheduled++
                }
            } else {
                AgendaNotificationHelper.cancelEventReminder(context, event.id)
            }
        }

        return scheduled
    }

    private fun scheduleOrCancel(
        context: Context,
        enabled: Boolean,
        actionKey: String,
        time: String,
        route: String,
    ): Int {
        if (!enabled) {
            AgendaNotificationHelper.cancelDailyReminder(context, actionKey)
            return 0
        }

        return if (AgendaNotificationHelper.scheduleDailyReminder(
                context = context,
                actionKey = actionKey,
                time = time,
                openRoute = route,
                expectedRole = expectedRole(actionKey),
            )
        ) {
            1
        } else {
            0
        }
    }
}
```

### Explicación técnica

- **Recibe:** configuraciones de notificación y eventos de agenda.
- **Procesa:** programa o cancela alarmas según cada opción y calcula el momento de los eventos.
- **Devuelve o modifica:** devuelve la cantidad de recordatorios programados y registra alarmas locales mediante `AgendaNotificationHelper`.
- **Relación con otros módulos:** abre registro emocional, inicio, logros o agenda mediante las rutas de navegación.
- **Relevancia:** aporta el fallback local configurable y no sensible. La auditoría original que no encontró FCM quedó superada: Android integra FCM `data-only` como fuente principal con conectividad, mientras estas alarmas permanecen únicamente para continuidad offline básica. No existen SMS discretos.

## Anexo 11. Cálculo de métricas, sobriedad y logros

### Descripción

El repositorio transforma los registros recientes en métricas resumidas y calcula el tiempo de sobriedad desde la fecha configurada. Asimismo, compara el avance con una lista de hitos para marcar logros desbloqueados y mostrar el siguiente objetivo.

### Archivo o ubicación

`app/src/main/java/com/example/rehabianex/data/repository/PatientRepository.kt`

### Fragmento de código

```kotlin
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

private fun SessionProfileDto?.soberDaysValue(): Double {
    val startDateTime = this?.sobrietyStartDate?.toZonedDateTimeOrNull()
        ?: return 0.0
    val minutes = ChronoUnit.MINUTES
        .between(startDateTime, ZonedDateTime.now(ZoneId.systemDefault()))
        .coerceAtLeast(0)

    return minutes / MINUTES_PER_DAY
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
```

### Explicación técnica

- **Recibe:** el perfil del paciente y hasta siete registros emocionales recientes.
- **Procesa:** calcula promedios, ánimo predominante, recaídas, tendencia de ansiedad, días transcurridos y cumplimiento de hitos.
- **Devuelve o modifica:** construye `MetricsData` y listas de `AchievementItem` para la interfaz.
- **Relación con otros módulos:** alimenta las pantallas de métricas, inicio, contador de sobriedad y logros.
- **Relevancia:** convierte datos registrados en retroalimentación de seguimiento. El nivel de riesgo es orientativo y no constituye diagnóstico ni validación clínica.

## Anexo 12. Red de apoyo y directorio de recursos

### Descripción

La red de apoyo valida y guarda contactos de confianza, mientras que el directorio filtra recursos por texto y categoría. Ambas funciones ofrecen acceso organizado a apoyos, pero no ejecutan SMS ni alertas remotas automáticas.

### Archivo o ubicación

- `app/src/main/java/com/example/rehabianex/ui/screens/patient/PatientDataViewModels.kt`
- `app/src/main/java/com/example/rehabianex/ui/screens/patient/LocalDirectoryScreen.kt`

### Fragmento de código

```kotlin
fun saveContact(input: SupportContactFormInput) {
    if (_saveState.value is FormSaveUiState.Loading) return

    val request = input.toRequestOrError()
    if (request == null) return

    viewModelScope.launch {
        _saveState.value = FormSaveUiState.Loading
        _saveState.value = when (
            val result = repository.saveSupportContact(request, input.contactId)
        ) {
            is PatientDataResult.Success -> {
                invalidatePatientRefreshes()
                refreshIfStale(force = true)
                FormSaveUiState.Success(result.data)
            }
            is PatientDataResult.Failure -> FormSaveUiState.Error(
                result.message,
                result.fieldErrors,
            )
        }
    }
}

private fun SupportContactFormInput.toRequestOrError(): CreateSupportContactRequest? {
    val cleanName = name.cleanFormText(maxLength = 80)
    val cleanRelationship = relationship.cleanFormText(maxLength = 60)
    val normalizedPhone = phone.normalizedPhone()
    val priorityValue = priority.toIntOrNull()?.takeIf { it in 1..5 }
    val cleanNotes = notes.cleanFormText(maxLength = 180)
    val errors = buildMap {
        if (cleanName == null ||
            !cleanName.isHumanLabel(minLength = 2, maxLength = 80)
        ) {
            put("name", "Escribe un nombre válido, solo letras y espacios.")
        }
        if (cleanRelationship == null ||
            !cleanRelationship.isHumanLabel(minLength = 2, maxLength = 60)
        ) {
            put("relationship", "Indica una relación válida.")
        }
        if (normalizedPhone == null) {
            put("phone", "Usa un teléfono válido de 10 a 15 dígitos.")
        }
        if (priorityValue == null) {
            put("priority", "Selecciona una prioridad válida.")
        }
    }

    if (errors.isNotEmpty()) {
        _saveState.value = FormSaveUiState.Error(
            message = "Revisa los datos del contacto.",
            fieldErrors = errors,
        )
        return null
    }

    return CreateSupportContactRequest(
        name = cleanName!!,
        relationship = cleanRelationship!!,
        phone = normalizedPhone!!,
        priority = priorityValue!!,
        canReceiveAlerts = canReceiveAlerts,
        notes = cleanNotes,
    )
}
```

```kotlin
private fun List<LocalResource>.filteredBy(
    query: String,
    filter: String,
): List<LocalResource> {
    val cleanQuery = query.trim().lowercase()

    return filter { resource ->
        val matchesFilter = filter == "Todos" || resource.typeLabel == filter
        val matchesQuery = cleanQuery.isBlank() ||
            resource.name.lowercase().contains(cleanQuery) ||
            resource.city.lowercase().contains(cleanQuery) ||
            resource.detail.lowercase().contains(cleanQuery)

        matchesFilter && matchesQuery
    }
}
```

### Explicación técnica

- **Recibe:** datos de un contacto, una consulta de búsqueda y una categoría de recurso.
- **Procesa:** normaliza teléfono y textos, valida prioridad y filtra recursos por nombre, ciudad o detalle.
- **Devuelve o modifica:** guarda o actualiza el contacto mediante el repositorio y devuelve la lista filtrada del directorio.
- **Relación con otros módulos:** se integra con la pantalla de red de apoyo, el almacenamiento remoto y la acción local de marcado telefónico.
- **Relevancia:** facilita el acceso voluntario a contactos y recursos. El directorio mostrado por el prototipo todavía requiere verificación institucional periódica; no deben asumirse como validados permanentemente sus datos.

## Observaciones y limitaciones identificadas

- La aplicación Android emplea arquitectura basada en ViewModel, repositorios, StateFlow, Retrofit, Room y WorkManager; no se encontró una capa de casos de uso independiente en los módulos revisados.
- La autenticación del cliente se realiza contra Laravel. El backend entrega los tokens de Firebase; no se encontró integración directa del SDK `firebase-auth` dentro del módulo Android.
- La cola offline con reintento automático está implementada específicamente para registros emocionales. Agenda, contactos, directorio, perfiles y resúmenes tienen persistencia o caché parcial, pero no todas sus mutaciones funcionan sin conexión.
- Se confirmó SQLCipher para Room y protección de la passphrase mediante Android Keystore. Esta medida protege el almacenamiento local, pero no representa cifrado de extremo a extremo.
- La observación histórica que no encontró Firebase Cloud Messaging quedó superada. Android integra FCM `data-only`, valida una allowlist cerrada de tipos/rutas y usa textos genéricos; los recordatorios locales se limitan a fallback offline básico no sensible. El estado vigente se documenta en `docs/ANDROID_FCM_CONTRACT.md`.
- El Chatbot IA depende de autorización del supervisor, existencia de pacientes elegibles y consentimiento con alcance `ai_chat_summary`. La respuesta es de acompañamiento informativo y no sustituye evaluación profesional.
- El directorio posee búsqueda y filtrado funcionales, pero su contenido no cuenta todavía con un proceso institucional completo de verificación continua.
