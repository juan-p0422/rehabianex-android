# Módulo de notificaciones Android — RehabiAnex

Actualizado: 2026-09-27.

Estado: **consolidación conjunta local aprobada para hardening pre-commit;
validación remota Render queda pendiente para fase posterior**.

FCM es la fuente principal cuando existe conectividad. Android conserva
únicamente fallback local básico y no sensible. El contrato detallado está en
`docs/ANDROID_FCM_CONTRACT.md`.

## FCM implementado

- `FirebaseMessagingService` procesa exclusivamente `remoteMessage.data`.
- El canal visible es `rehabianex_reminders`.
- La allowlist Android contiene los 18 tipos aprobados, incluidos
  `consent_suspended` y `supervisor_validation_approved`.
- Tipo, ruta, rol y coherencia con `entity_id` se validan antes de navegar.
- Una ruta inválida o un rol incompatible abre el inicio seguro del rol activo.
- Android ignora campos ajenos al contrato y aplica textos visibles canónicos.
- El token se registra después del login o cambio de cuenta y se revoca antes
  de limpiar la sesión durante logout.
- `onNewToken` no puede reactivar un token mientras el ciclo está suspendido por
  logout.

`system_notice` existe solo para compatibilidad y transporte genérico. No debe
ser el emisor principal de nuevos flujos cuando exista un tipo específico.
El recordatorio de nota/check-in reutiliza `progress_checkin`; no se admite un
tipo separado `note_reminder`.

## Tipos pendientes de dominio

Para los siguientes tipos el contrato y el transporte están preparados, pero
no deben habilitarse funcionalmente hasta que exista un evento de dominio real:

- `progress_checkin`
- `sober_day_update`
- `achievement_unlocked`
- `vulnerable_user_priority`
- `user_validation_required`
- `supervision_request_conflict`

`intervention_update` se dirige actualmente al paciente propietario. Una
notificación al supervisor queda fuera del alcance salvo una regla futura.

## Fallback local permitido

| Recordatorio | Motivo | Contenido visible |
|---|---|---|
| Registro/check-in básico | Continuidad offline | Genérico |
| Agenda cacheada | Evento previamente sincronizado | Genérico, sin título ni detalle del evento |
| Día sobrio, logro o motivacional básico | Recordatorio simple no sensible | Genérico |

Las alarmas locales equivalentes de agenda se deduplican contra FCM por evento
y `notification_id`. Recibir FCM cancela únicamente la alarma equivalente; no
elimina otras alarmas.

No existe fallback local para recaída, riesgo alto, solicitudes o
desvinculación, consentimiento suspendido, validaciones administrativas,
intervenciones clínicas ni prioridad de usuarios vulnerables.

## Resumen supervisor local

El recordatorio `REMINDER_SUPERVISOR_SUMMARY` está deshabilitado. La política
local cancela también los recordatorios de recaída, solicitudes y seguimiento
específico del supervisor. Ningún recordatorio local sustituye alertas FCM
clínicas o de supervisión ni puede mostrar nombre, alias o datos clínicos.

## Privacidad y transporte

- Todos los mensajes remotos son `data-only`; no se admite bloque
  `notification`.
- No se usan topics ni multicast.
- No se muestra nombre, alias, correo, teléfono, UID ni datos clínicos.
- El detalle solo aparece dentro de una pantalla autenticada después de validar
  sesión, rol y permisos.
- Los tokens completos y payloads sensibles no se escriben en Logcat.
- La ausencia de `POST_NOTIFICATIONS` no provoca crash ni cambia el acceso a la
  aplicación.

## Programación local

- `AlarmManager` programa recordatorios básicos; no se solicita
  `SCHEDULE_EXACT_ALARM`.
- `WorkManager` recupera preferencias y reprograma alarmas, pero no genera
  alertas clínicas.
- Room mantiene agenda y preferencias necesarias para el funcionamiento
  offline.
- El logout cancela recordatorios del rol y limpia notificaciones visibles de
  la sesión anterior.

## Render

Render queda pospuesto para una fase posterior. En este cierre no se prueba
Render, no se cambia la `baseUrl`, no se hace push, no se despliega y no se
habilita producción.
