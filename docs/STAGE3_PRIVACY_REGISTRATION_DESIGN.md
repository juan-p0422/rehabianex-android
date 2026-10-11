# Etapa 3: privacidad y registro sin credenciales

Estado verificado el 2026-10-11: diseño acordado entre Android y API; el alta
sin nombre/correo **no está implementada**. Falta el texto o URL oficial y
versionado del Aviso de Privacidad, además de la política para cuentas
históricas con `legal_acceptance=null`. El resumen existente no sustituye el
aviso íntegro y no debe usarse para aparentar aceptación informada.

## Comportamiento existente que se conserva

- El registro convencional envía aceptación explícita y la versión
  `2026-08-01`.
- La API genera la fecha de aceptación y proyecta únicamente su estado,
  versión y fecha.
- La aceptación legal, el consentimiento de supervisión y el permiso Android
  de notificaciones son decisiones independientes.
- `is_anonymous` y `privacy_mode` siguen siendo campos heredados de privacidad
  visual. No describen el tipo de identidad técnica ni garantizan anonimato.
- No se convierte ni migra ninguna cuenta existente.

## Contrato acordado, pendiente de implementación

### Alta técnica

Android iniciará una sesión con Firebase Auth mediante `signInAnonymously()` y
enviará el ID token a:

`POST /api/auth/anonymous/register`

Body exacto:

```json
{
  "id_token": "string, requerido, máximo 16384",
  "privacy_notice_accepted": true,
  "privacy_notice_version": "versión vigente"
}
```

No se enviarán nombre, correo, teléfono, contraseña, rol ni `is_anonymous`.
Laravel comprobará que Firebase identifique un usuario anónimo sin correo ni
proveedores vinculados y solo podrá crear un perfil `patient`.

### Respuesta y errores

- `201`: primera alta.
- `200`: repetición idempotente del mismo UID con perfil anónimo compatible;
  también puede reparar el claim de rol faltante.
- `401`: token inválido/expirado o identidad Firebase no anónima.
- `409`: UID asociado a un perfil, rol o estado incompatible; no se fusionan ni
  sobrescriben datos.
- `422`: payload o versión legal inválidos.
- `502`: fallo temporal de Firebase o persistencia; admite reintento acotado.

La respuesta mantiene `{ok,message,auth,profile}`. `auth` no devuelve tokens y
contiene UID, correo nulo, proveedores vacíos y rol `patient`. `profile` añade:

```json
{
  "account_type": "anonymous",
  "recovery_available": false,
  "full_name": null,
  "email": null,
  "phone": null,
  "privacy_mode": true,
  "is_anonymous": false
}
```

`account_type` representa la identidad técnica; `is_anonymous` conserva su
significado histórico exclusivamente visual. Tras el alta, Android forzará una
sola renovación de ID token para obtener el claim `role=patient`; cualquier
reintento adicional será acotado y nunca un bucle.

### Consistencia y recuperación

La API asignará el claim antes de crear el perfil. Si falla el claim, no habrá
perfil; si falla Firestore, intentará restaurar los claims previos. Un claim
aislado no abre sesión porque la autenticación de la API también exige el
perfil. La repetición idempotente reparará estados parciales compatibles.

Cerrar sesión, borrar datos o reinstalar la aplicación puede hacer
irrecuperable una cuenta anónima mientras no se vinculen credenciales. Android
deberá advertirlo antes del cierre o cambio de usuario y mantener el aislamiento
local por UID. La vinculación con credenciales queda fuera de esta etapa hasta
acordar recuperación y fallos parciales; no se publicará un botón incompleto.

## Condiciones para desbloquear la implementación

1. Aprobar y proporcionar el texto íntegro o URL oficial del Aviso de
   Privacidad, con su versión.
2. Definir si una cuenta histórica con `legal_acceptance=null` debe reaceptar y
   habilitar, si corresponde, un endpoint autenticado de reaceptación.
3. Presentar el aviso antes de cualquier campo personal o creación de identidad
   y conservar acceso desde login y perfiles.
4. Probar con Firebase Auth y Firestore Emulator: primera alta, idempotencia,
   claim renovado, reinicio, advertencia de logout/cambio de usuario, fallos
   parciales y regresión del registro convencional.
