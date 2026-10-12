# Etapa 4: matriz de supervisión y autorización

Este documento describe el contrato coordinado entre Android y Laravel. Los tres
ejes siguientes son independientes: habilitar administrativamente una cuenta de
supervisor no crea un vínculo; aceptar un vínculo no concede acceso; y el acceso
solo existe mientras haya consentimiento activo con el alcance requerido.

## Matriz de estados y transiciones

| Eje | Estado | Acceso a datos | Transición permitida | Actor |
|---|---|---:|---|---|
| Supervisor | `pending_review` | No | `active` mediante autorización | Admin |
| Supervisor | `active` | Solo si además hay vínculo y consentimiento | `suspended` | Admin |
| Supervisor | `suspended` | No | `active` mediante reactivación | Admin |
| Solicitud | `pending` | No | `accepted`, `rejected` | Supervisor asignado |
| Solicitud | `pending` | No | `cancelled` | Paciente propietario |
| Solicitud | `accepted` | No por sí sola | Sin transición adicional | — |
| Solicitud | `rejected` | No | Terminal | — |
| Solicitud | `cancelled` | No | Terminal | — |
| Consentimiento | `inactive` | No | `active`, `revoked` | Paciente propietario |
| Consentimiento | `active` | Solo alcances seleccionados | `paused`, `revoked` o edición de `scope` | Paciente propietario |
| Consentimiento | `paused` | No | `active`, `revoked` o edición de `scope` | Paciente propietario |
| Consentimiento | `revoked` | No | Terminal; un consentimiento posterior debe ser nuevo | Paciente propietario |
| Vínculo | aceptado | Depende del consentimiento | solicitud `unlink` | Paciente |
| Vínculo | finalizado | No | Terminal para ese vínculo | Aceptación de `unlink` o desvinculación autorizada |

Aceptar una solicitud `link` crea el vínculo, pero nunca crea consentimiento.
Aceptar una solicitud `unlink` finaliza el vínculo y revoca sus consentimientos.
Rechazar `unlink` conserva vínculo y consentimiento sin cambios.

## Alcances canónicos

- `patient_notes`: notas de seguimiento.
- `agenda_events`: agenda.
- `support_contacts`: contactos de apoyo.
- `ai_chat_summary`: contexto para consultas nuevas del asistente IA.
- `patient_achievements`: logros.
- `patient_phone`: teléfono.

Android no preselecciona alcances. Activar o reactivar exige al menos uno y el
servidor vuelve a validar vínculo, propietario, estado y alcance. Un alcance no
reconocido no se envía.

## Contratos consumidos por Android

### Consentimiento

- `POST /api/consents`: `supervisor_uid`, `type`, `explicit_consent`,
  `consent_text`, `scope` no vacío. `explicit_consent=false` crea `inactive`;
  `true` crea `active`.
- `PATCH /api/consents/{id}`: uno o más de `explicit_consent`, `status`, `scope`.
- Respuestas relevantes: `200/201`, `401`, `403`, `409`, `422`.

### Solicitud pendiente

- `PATCH /api/supervision-requests/{id}` con `{"status":"cancelled"}`.
- Solo el paciente propietario y solo mientras siga `pending`.
- `DELETE` permanece como compatibilidad heredada, pero Android no lo usa.

### Supervisor vinculado

- `GET /api/auth/me` incluye `profile.supervisor` con
  `{uid, full_name, supervisor_type}` o `null`.
- No se reutilizan rutas administrativas ni se expone información adicional.

### Listas administrativas

- `GET /api/admin/supervisors?q=&status=&authorized=&verified=&limit=&page=`.
- Conserva `count` y `data`; agrega `page`, `limit`, `total` y `has_more`.
- La confirmación de UI no sustituye el control de rol del backend.

## Privacidad, caché y concurrencia

La proyección de pacientes respeta `privacy_mode` e `is_anonymous`: un alcance de
registros no autoriza revelar identidad. El cliente invalida vistas después de
pausa, revocación, desvinculación y cambio de usuario. El backend revalida antes
de entregar recursos sensibles. Aun así, una revocación no borra información ya
consultada y no puede retirar una respuesta que hubiera terminado de cruzar el
límite de autorización antes del cambio.

Las acciones administrativas registran actor y fecha; rechazo y suspensión
incluyen motivo. No se registran tokens, contenido clínico ni credenciales en la
traza administrativa.
