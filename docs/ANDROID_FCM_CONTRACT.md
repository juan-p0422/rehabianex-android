# Contrato Android FCM Total Privada

Actualizado: 2026-09-29.

Estado: **cliente Android preparado para QA online-first contra Render; smoke
FCM autenticado y aprobación de producción continúan pendientes**.

## Fuente y transporte

FCM es la fuente principal con conectividad. Todos los mensajes son
`data-only`. Android no depende de un bloque `notification`, topics ni
multicast. El canal visible es `rehabianex_reminders`.

El fallback local queda limitado a agenda cacheada, check-in y recordatorios
simples no sensibles previamente programados.

## Privacidad visible

Android nunca muestra nombre, alias, correo, teléfono, UID, ubicación, texto
libre ni datos clínicos recibidos en el payload. Incluso si esos campos llegan
por error, se ignoran. El título visible es `Rehabianex` y el cuerpo se elige de
la lista canónica local.

El `entity_id` es opaco. El detalle se consulta exclusivamente dentro del panel
autenticado después de validar sesión, rol y permisos.

## Tipos y rutas

| Tipo | Ruta oficial | Destino/fallback |
|---|---|---|
| `appointment_reminder` | `/agenda-events/{id}` | Agenda / inicio paciente |
| `progress_checkin` | `/checkins/new` | Check-in / inicio paciente |
| `sober_day_update` | `/home` | Inicio paciente |
| `achievement_unlocked` | `/achievements/{id}` | Logros / inicio paciente |
| `supervision_request` | `/supervision-requests/{id}` | Vinculación o pacientes / inicio seguro |
| `supervision_response` | `/supervision-requests/{id}` | Vinculación o pacientes / inicio seguro |
| `unlink_request` | `/unlink-requests/{id}` | Solicitudes/pacientes supervisor |
| `consent_suspended` | `/consents/{id}` | Solicitudes/pacientes supervisor |
| `intervention_update` | `/interventions/{id}` | Intervenciones paciente / inicio paciente |
| `relapse_alert` | `/patients/priority` | Lista prioritaria / inicio supervisor |
| `risk_alert` | `/patients/priority` | Lista prioritaria / inicio supervisor |
| `vulnerable_user_priority` | `/patients/priority` | Lista prioritaria / inicio supervisor |
| `user_validation_required` | `/admin/users/pending/{id}` | Panel admin |
| `supervisor_validation_required` | `/admin/supervisors/pending/{id}` | Panel admin |
| `supervisor_validation_approved` | `/home` | Inicio supervisor |
| `supervision_request_conflict` | `/admin/supervision-conflicts/{id}` | Panel admin |
| `system_notice` | Ruta segura de supervisión/intervención | Inicio seguro según rol |
| `test_notification` | `/home` o vacía | Inicio seguro según rol |

`system_notice` es compatibilidad y transporte genérico; no debe ser el emisor
principal cuando exista un tipo específico.

La allowlist compartida contiene 18 tipos. `progress_checkin` cubre también el
recordatorio configurable de nota/check-in; no existe `note_reminder`.

## Textos canónicos

El título es siempre `Rehabianex`. Android ignora incondicionalmente `title` y
`body` remotos y selecciona el cuerpo por el tipo validado:

| Tipo | Cuerpo visible |
|---|---|
| `appointment_reminder` | `Tienes un evento de agenda próximo.` |
| `progress_checkin` | `Es momento de registrar tu seguimiento.` |
| `achievement_unlocked` | `Has alcanzado un nuevo logro.` |
| `sober_day_update` | `Has alcanzado un nuevo avance en tu proceso.` |
| `intervention_update` | `Tu plan de apoyo tiene una actualización.` |
| `supervision_response` | `Tu solicitud de acompañamiento fue actualizada.` |
| `supervision_request` | `Tienes una nueva solicitud de acompañamiento.` |
| `unlink_request` | `Tienes una solicitud de cambio de consentimiento.` |
| `consent_suspended` | `Se actualizó el estado de un consentimiento.` |
| `risk_alert` | `Hay una actualización importante de seguimiento.` |
| `relapse_alert` | `Hay una actualización prioritaria de seguimiento.` |
| `vulnerable_user_priority` | `Hay una actualización importante de seguimiento.` |
| `supervisor_validation_approved` | `Tu cuenta de supervisor fue aprobada.` |
| `supervisor_validation_required` | `Hay una cuenta de supervisor pendiente de validación.` |
| `user_validation_required` | `Hay una cuenta pendiente de validación.` |
| `supervision_request_conflict` | `Hay una solicitud de supervisión que requiere revisión.` |
| `system_notice` | `Tienes una actualización en Rehabianex.` |
| `test_notification` | `Tienes una actualización en Rehabianex.` |

`intervention_update` tiene como destinatario actual al paciente propietario.
Avisar al supervisor queda fuera del alcance salvo una regla futura.

## Contrato preparado, no funcional

Los siguientes tipos tienen contrato y transporte preparados, pero no deben
habilitarse funcionalmente hasta que exista un evento de dominio real:

- `progress_checkin`
- `sober_day_update`
- `achievement_unlocked`
- `vulnerable_user_priority`
- `user_validation_required`
- `supervision_request_conflict`

## Ciclo del token

- Login o cambio de cuenta registra el token para el propietario activo sin
  reiniciar la aplicación.
- `onNewToken` solo registra con una sesión activa y ciclo habilitado.
- Logout suspende el registro, cancela reintentos, revoca en backend antes de
  limpiar la sesión y evita tokens zombie.
- Los reintentos usan backoff y no escriben el token completo en logs.

## Resumen supervisor local

El resumen supervisor genérico fue deshabilitado como notificación local durante
el hardening. No puede mostrar identidad ni información clínica y no sustituye
alertas FCM. Los recordatorios locales de recaída, riesgo, solicitudes,
desvinculación, intervención, seguimiento supervisor y prioridad vulnerable no
están permitidos.

## Render

Todas las variantes Android usan la URL oficial de Render mediante
`BuildConfig.API_BASE_URL`. `app/build.gradle.kts` mantiene el valor canónico y
permite `-PREHABIANEX_API_BASE_URL` solo cuando coincide exactamente con esa
misma URL. Loopback, localhost, IP LAN, otros hosts y HTTP claro se rechazan;
por tanto, un build debug sin argumentos también apunta a Render.

La variante `staging` no se usa todavía para FCM porque el archivo Firebase
local no contiene un cliente para el sufijo `.staging`. El backend está Live
con dry-run activo, pero `/api/notifications/test` no se ejecuta hasta la
aprobación coordinada del smoke autenticado. Producción no se considera
finalizada.
