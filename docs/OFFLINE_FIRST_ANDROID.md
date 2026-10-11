# Estrategia Room/offline-first de Android

Fecha: 1 de agosto de 2026  
Estado: implementación incremental para datos propios del paciente.

## Contratos revisados

- `ANDROID_API_CONTRACT_V1.md`
- `OFFLINE_FIRST_BACKEND.md`
- `THUNDER_CLIENT_SECURITY_FLOW.md`

La implementación respeta el refresh acotado del backend. No envía
`updated_since`, porque la sincronización incremental y los cursores todavía no
forman parte del contrato congelado.

## Arquitectura

```text
Compose UI
   ↑ estado + indicador offline/pending
ViewModel
   1. lee Room y publica el contenido disponible
   2. solicita refresh al repositorio
PatientRepository
   ↔ Room (solo datos del paciente propietario)
   ↔ Retrofit/API (fuente autoritativa)
   → WorkManager (outbox de notas con red disponible)
```

`SupervisorRepository` no recibe la base Room. Por diseño, Android no persiste
detalle, notas, agenda, contactos, intervenciones ni logros de pacientes
supervisados. Una respuesta 401 o 403 nunca usa fallback de caché.

## Entidades Room

| Tabla / entidad | Propietario | `pendingSync` | `lastSyncedAt` | Uso |
|---|---|---:|---:|---|
| `patient_notes` / `PatientNoteEntity` | `ownerUid` | Sí | Sí | Notas propias, métricas, Home y outbox. |
| `agenda_events` / `AgendaEventEntity` | `ownerUid` | Preparado; escritura offline deshabilitada | Sí | Agenda propia de lectura offline. |
| `support_contacts` / `SupportContactEntity` | `ownerUid` | Preparado; escritura offline deshabilitada | Sí | Contactos propios y llamada rápida offline. |
| `notification_settings` / `NotificationSettingsEntity` | `ownerUid` | Preparado; escritura offline deshabilitada | Sí | Preferencias remotas del paciente. |
| `local_resources` / `LocalResourceEntity` | Catálogo | No aplica | Sí | Directorio no clínico offline. |
| `achievements` / `AchievementEntity` | Catálogo | No aplica | Sí | Catálogo de logros. |
| `patient_achievements` / `PatientAchievementEntity` | `ownerUid` | No aplica | Sí | Logros otorgados al paciente propietario. |
| `patient_summary` / `PatientSummaryEntity` | `ownerUid` | No aplica | Sí | Nombre y fecha de sobriedad mínimos para Home/métricas offline. |
| `patient_profile` / `PatientProfileEntity` | `ownerUid` | No aplica | Sí | Snapshot seguro del perfil propio para carga inmediata. |
| `cache_sync_state` / `CacheSyncStateEntity` | `ownerUid` + recurso | No aplica | Sí | Distingue una caché vacía válida de una nunca sincronizada. |

Las claves de recursos personales incluyen el UID propietario para impedir
colisiones o lecturas entre cuentas. Las consultas activas excluyen
`deletedAt != null`.

## DAOs

- `PatientNoteDao`: lectura propia, pendientes, conteo, upsert y purga por propietario.
- `AgendaEventDao`: agenda activa propia y upsert de refresh/tombstones.
- `SupportContactDao`: contactos activos propios y upsert.
- `NotificationSettingsDao`: configuración única por propietario.
- `LocalResourceDao`: catálogo local activo.
- `AchievementDao`: catálogo de logros activo.
- `PatientAchievementDao`: logros del propietario.
- `PatientSummaryDao`: resumen mínimo del paciente.
- `PatientProfileDao`: snapshot propio, marcado `isStale` y purga por UID.
- `CacheSyncStateDao`: fecha del último refresh exitoso por recurso.

Room valida las consultas durante compilación mediante KSP y se exporta el
schema v3 en `app/schemas`.

## Mappers

`OfflineMappers.kt` contiene conversiones DTO ↔ Entity para los siete recursos,
con conversión Entity → UI para configuración y Entity → DTO → UI para los
modelos que ya tenían mapper visual en `PatientRepository`. También normaliza:

- aliases `settings_id` / `notification_setting_id`;
- aliases `daily_check_in_*` / `daily_note_*`;
- listas de detonantes, acciones, horarios y etiquetas;
- IDs específicos de cada colección;
- timestamps y tombstones.

## Estrategia de lectura

1. El ViewModel consulta Room y publica inmediatamente el dato cacheado.
2. La UI muestra `Mostrando datos guardados` mientras se refresca.
3. El repositorio consulta la API con el token actual.
4. La respuesta se aplica mediante upsert; no se eliminan filas solo porque no
   aparezcan en una página, conforme a `cache_action=upsert`.
5. Los tombstones actualizan la fila y dejan de aparecer en consultas activas.
6. Si la red/servidor falla, se conserva el dato local y se marca `Modo sin conexión`.
7. Un 401, 403, 404, 409 o 422 no se disfraza como modo offline.

El perfil propio sigue la misma lectura cache-first. Su caché se marca obsoleta
antes del refresh, muestra `Actualizando...` y no expone acciones de supervisión
hasta validar el estado remoto. Un 401/403 elimina el snapshot en vez de usarlo
como fallback.

Para Home y métricas, el listado funcional continúa usando
`GET /patients/{patient_uid}/notes`. El refresh adicional de
`GET /patient-notes?include_deleted=true` se usa exclusivamente para mantener
tombstones; no sustituye la fuente oficial de UI.

## Estrategia de escritura y sincronización

### Registro emocional

- Con red validada: POST actual, confirmación del backend y upsert Room.
- Sin red validada: se crea una fila local `pendingSync=true`, `serverId=null`,
  un `clientMutationId` UUID estable y un ID `local-{clientMutationId}`.
- El POST envía ese valor como `client_mutation_id`; la misma nota conserva la
  clave durante todos sus intentos para que el backend pueda responder de forma
  idempotente.
- Se programa un `PatientSyncWorker` único con restricción
  `NetworkType.CONNECTED`, `ownerUid` en `inputData` y backoff exponencial.
- El worker valida que `ownerUid` coincida con la sesión paciente actual. Un
  trabajo de una sesión anterior termina sin tocar datos.
- Home y métricas también intentan vaciar pendientes al abrirse.
- Al confirmar el POST, la fila local se reemplaza por el ID entregado por el
  servidor y se actualiza el perfil de sobriedad cuando aplica.
- La outbox distingue `pending`, `syncing`, `retryable_error`, `ambiguous`,
  `permanent_error` y `synced`. Timeout, falta de red, 429, 500 y 502 son
  reintentables; 422 es permanente y queda visible para corrección.
- Un resultado ambiguo solo puede volver a enviarse automáticamente si conserva
  `clientMutationId`; sin clave de idempotencia queda detenido para revisión.

### Agenda, contactos y notificaciones

La lectura offline está habilitada. Las escrituras continúan siendo remotas y
autoritativas; después de una respuesta correcta se actualiza Room o se ejecuta
el refresh existente. No se habilitó una outbox para estos POST/PATCH porque el
contrato todavía no ofrece idempotency keys ni control de versión.

### Catálogos y logros

Son solo lectura. Se prefiere servidor y se usa Room cuando no hay conexión o
el servicio presenta un fallo temporal.

## Privacidad y seguridad

- La base solo se conecta al repositorio del paciente propietario.
- Toda consulta personal se filtra por `ownerUid` de la sesión.
- No se cachean respuestas, preguntas, sesiones ni contexto del Chat IA.
- Chat IA conserva su bloqueo offline y requiere autorización/consentimiento.
- No se cachean datos de pacientes supervisados; por ello un 403 no deja una
  copia Room que pueda mostrarse.
- 401 y 403 bloquean el fallback local y mantienen el flujo global de sesión.
- `patient_profile.fullName` solo se persiste si el perfil no es privado ni
  anónimo. En caso contrario se conserva un nombre visible seguro y se elimina
  el nombre real de `PatientIdentityStore`.
- Room usa SQLCipher 4.16.0. Su passphrase aleatoria de 256 bits se envuelve con
  AES-GCM mediante una clave no exportable de Android Keystore; solo el sobre
  cifrado permanece en `noBackupFilesDir`.
- La base, WAL, SHM, journal, identidad local y preferencias identificables se
  excluyeron de backup y transferencia entre dispositivos.
- `OfflineDataCleanupCoordinator` cancela trabajos/recordatorios y limpia por
  `ownerUid` Room sincronizado, identidad local y preferencias identificables.
- Logout con notas no sincronizadas requiere confirmación explícita antes de
  eliminarlas. El cifrado en reposo reduce exposición física, pero no sustituye
  la purga por UID ni el aislamiento entre sesiones.

## Estado visual

Se agregó una tarjeta reutilizable sin rediseñar pantallas:

- `Mostrando datos guardados`: Room ya respondió y API está refrescando.
- `Modo sin conexión`: el refresh falló y se conserva la última copia local.
- `Cambios pendientes`: existe al menos una nota con `pendingSync=true`.
- `Revisión necesaria`: una nota quedó `permanent_error` o `ambiguous` y muestra
  un mensaje no técnico.

Se utiliza en Home, métricas, agenda, contactos, recursos locales, logros y
notificaciones. El perfil usa un indicador más discreto (`Actualizando...`) y
solo presenta carga completa cuando no existe caché previa.

## Pruebas

- `OfflineMappersTest` valida round-trip de notas, aislamiento de claves por
  UID, UUID estable de mutación, creación pendiente, aliases de notificaciones
  y tombstones.
- `PatientNoteOutboxPolicyTest` valida serialización de `client_mutation_id`,
  clasificación 422/429/500/502/timeout y bloqueo de reintento ambiguo sin clave.
- `OfflineDaosInstrumentedTest` valida sobre el emulador aislamiento por UID,
  filtrado de tombstones, conteo de pendientes, agenda programada offline y el
  snapshot de perfil marcado como obsoleto.
- Room/KSP valida en compilación todas las tablas, índices y consultas DAO.
- La suite contractual existente sigue validando API, permisos, refresh,
  notas, notificaciones, consentimiento, desvinculación y Chat IA.

## Resultados de build

- `./gradlew.bat :app:compileDebugKotlin --no-daemon --console=plain`:
  `BUILD SUCCESSFUL` en 56 s.
- `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain`:
  `BUILD SUCCESSFUL` en 40 s.
- Pruebas JVM: 121, sin fallos, errores ni omitidas (23 suites).
- `:app:connectedDebugAndroidTest`: `BUILD SUCCESSFUL`; 7 pruebas instrumentales, sin fallos, en
  emulador API 33. Incluye apertura/reapertura SQLCipher, cabecera cifrada,
  reemplazo controlado de SQLite plano en debug, perfil obsoleto y purga por UID.
- APK debug generado en `app/build/outputs/apk/debug/app-debug.apk`.

## Limitaciones restantes

- La conversión preservando datos desde SQLite plano mediante
  `sqlcipher_export` no se habilitó para release. Debug elimina la base local de
  pruebas en claro; release rechaza una base plana y falla de forma cerrada.
- `patient_identity` y preferencias locales identificables siguen siendo XML
  privado sin cifrado de campo; están excluidas de backup y se purgan por UID.
- Backend no soporta cursores, `updated_since` ni ETag/versiones. Para notas sí
  garantiza idempotencia mediante `client_mutation_id`.
- El límite de 100 elementos impide afirmar que un refresh sea un snapshot
  completo para historiales grandes.
- Agenda, contactos y notificaciones no admiten escritura offline en esta fase.
- Una fila histórica o dañada que quede ambigua sin `clientMutationId` requiere
  revisión manual y nunca se reintenta automáticamente.
- Un dispositivo totalmente offline no puede descubrir una revocación ocurrida
  en otro dispositivo; Android mitiga el riesgo no almacenando datos
  supervisados y bloqueando caché ante 401/403 cuando vuelve a validar.
- Falta una prueba end-to-end con credenciales de paciente y alternancia real
  de red contra Firebase; los DAOs sí se probaron con Room in-memory en emulador.
