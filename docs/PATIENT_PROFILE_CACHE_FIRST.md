# Perfil de paciente cache-first

Fecha: 2026-08-04  
Estado: implementado para el perfil propio del paciente.

## Alcance

La pantalla de perfil conserva la API como fuente autoritativa, pero usa un
snapshot Room cifrado para evitar una pantalla de carga completa cuando ya
existe información segura del mismo `ownerUid`. La edición continúa siendo
exclusivamente en línea.

## Persistencia

La tabla `patient_profile` usa `ownerUid` como clave primaria y almacena:

- `displayName`;
- `nickname`;
- `fullName`, únicamente si `privacyMode=false` e `isAnonymous=false`;
- `privacyMode` e `isAnonymous`;
- `sobrietyStartDate` y `photoUrl`;
- `lastSyncedAt` e `isStale`.

`PatientProfileDao` ofrece lectura, `upsert`, marcado obsoleto y purga por
propietario. La tabla vive en la base Room protegida con SQLCipher y nunca se
consulta sin filtrar por el UID de la sesión.

## Lectura y UX

1. El ViewModel consulta Room.
2. Si existe caché, la publica inmediatamente con el indicador discreto
   `Actualizando...`.
3. El repositorio consulta `auth/me`, solicitudes y consentimiento.
4. Si el refresh termina bien, reemplaza el snapshot y habilita las acciones
   basadas en estado remoto.
5. Si falla temporalmente, conserva el perfil mínimo, lo marca obsoleto y no
   habilita acciones de vinculación, consentimiento o desvinculación.
6. Solo se muestra carga completa en la primera entrada sin caché.

El snapshot no inventa un estado de supervisión. Mientras está obsoleto, la UI
muestra que ese estado está pendiente de validación.

## Escritura

La edición de privacidad es `online-only`. Si el dispositivo no tiene conexión
validada, no se modifica Room y se muestra un mensaje controlado. Después de una
respuesta exitosa de `PATCH /patients/{patient_uid}`, el repositorio actualiza
Room antes de refrescar nuevamente desde API.

## Minimización e invalidación

- En modo anónimo se elimina el nombre real de `patient_profile`,
  `patient_summary` y `PatientIdentityStore`; el estado UI se sustituye de
  inmediato por el snapshot saneado.
- En modo nickname privado se conserva el nickname, pero se elimina el nombre
  real.
- El nombre completo solo puede volver a persistirse cuando el backend confirma
  un perfil no anónimo y sin modo privado.
- Un 401 definitivo limpia sesión e invalida ambos snapshots del perfil.
- Un 403 al cargar o editar el perfil elimina el snapshot Room y la identidad
  local; la UI cambia a error y no conserva datos visibles.
- Logout y cambio de usuario purgan `patient_profile` por `ownerUid` mediante
  `OfflineDataCleanupCoordinator`.

## Migración

Room cambia de esquema v2 a v3. La migración crea `patient_profile` y elimina
las filas antiguas de `patient_summary`, porque el esquema v2 no registraba el
modo de privacidad y no era posible demostrar que un nombre histórico fuera
seguro para conservarse.

## Pruebas

- `OfflineMappersTest`: perfiles visible, privado y anónimo.
- `PatientUpdateContractTest`: edición solo en línea y payload privado sin
  nombre real.
- `OfflineDaosInstrumentedTest`: aislamiento por UID, `isStale` y purga.
- `EncryptedRoomDatabaseInstrumentedTest`: purga del perfil en logout y cambio
  de usuario sobre Room cifrado.

Validación ejecutada el 2026-08-04:

- `:app:testDebugUnitTest :app:assembleDebug`: `BUILD SUCCESSFUL`; 121 pruebas
  JVM, sin fallos, errores ni omitidas.
- `:app:connectedDebugAndroidTest`: `BUILD SUCCESSFUL`; 7 pruebas en emulador
  API 33, sin fallos.
- APK debug generado en `app/build/outputs/apk/debug/app-debug.apk`.

## Riesgos restantes

- Sin conexión no se puede comprobar un cambio de privacidad realizado desde
  otro dispositivo. Se conserva únicamente el snapshot mínimo del propietario
  y se bloquean acciones dependientes del estado remoto.
- `photoUrl` puede apuntar a un recurso que requiera red o que expire; no se
  almacena la imagen binaria en Room.
- La validación visual completa requiere prueba en emulador o dispositivo con
  una cuenta real y alternancia de red.
