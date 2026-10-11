# Auditoría Android de privacidad y permisos

Fecha: 2026-07-31

## Alcance

Esta revisión alinea la presentación de datos sensibles con los permisos devueltos por backend. No se cambiaron endpoints, DTO del contrato, navegación por roles ni configuración de URL. El entorno de desarrollo continúa usando el backend local configurado para el emulador.

## Refresco al entrar a pantallas

Las rutas sensibles ejecutan una consulta nueva al recibir `Lifecycle.Event.ON_RESUME`. Antes de esperar la respuesta, sus ViewModels sustituyen cualquier contenido anterior por estado `Loading`; si la consulta falla, muestran `Error` sin conservar el contenido previo.

| Entrada | Fuente consultada | Protección frente a datos anteriores |
|---|---|---|
| Inicio y métricas del paciente (notas) | `GET /patients/{patient_uid}/notes` y perfil | El contenido previo se reemplaza por `Loading/Error`. |
| Contactos de apoyo | API de `support-contacts` | La lista anterior se elimina del estado visible antes de consultar. |
| Agenda | API de `agenda-events` | Los eventos anteriores se eliminan del estado visible antes de consultar. |
| Lista de pacientes del supervisor | `GET /supervisors/{uid}/patients` más consentimientos | La lista se reconstruye desde backend en cada reentrada. |
| Detalle del paciente | Proyección del supervisor, consentimiento, perfil, notas, agenda y contactos | Ya refrescaba en `ON_RESUME`; un `403` termina en pantalla de acceso restringido sin contenido clínico. |
| Chat IA | Perfil, pacientes del supervisor y consentimientos | Se borran mensajes y contexto en memoria antes de recalcular autorización. |
| Perfil del paciente | Perfil, solicitudes y consentimiento | Refresca estados de vinculación y consentimiento al reentrar. |

## Permisos y caché

- No existe una base Room ni otra persistencia offline de notas, métricas, agenda, contactos o detalle clínico del supervisor.
- Los mensajes del Chat IA se mantienen únicamente en memoria. Al recargar se vacían antes de validar permisos.
- La identidad local del propio paciente y las preferencias locales de notificación no contienen el detalle clínico consultado por un supervisor.
- Los pacientes con consentimiento `paused`, `revoked` o relación `unlinked` permanecen como referencia de estado cuando backend los devuelve, pero no habilitan detalle ni exponen notas, métricas, agenda o contactos.
- Si backend responde `403` al consultar detalle o alcances, el repositorio devuelve fallo/restricción y la UI no reutiliza la respuesta sensible anterior.
- En modo sin conexión no existe fallback a datos clínicos sensibles del supervisor; se presenta error de conexión.

## Estados visibles

Se presentan mensajes diferenciados para:

- supervisor pendiente de autorización;
- solicitud de vinculación pendiente;
- solicitud de vinculación rechazada;
- supervisión activa;
- acceso pausado;
- consentimiento revocado o sin acceso;
- desvinculación pendiente;
- paciente desvinculado.

El estado `LinkRejected` se deriva exclusivamente de `supervision_status = rejected|denied` recibido del backend. No se crea a partir de una acción local y permite iniciar otra solicitud.

## Desvinculación

- El supervisor ejecuta `POST /supervisors/{supervisor_uid}/patients/{patient_uid}/unlink`.
- El paciente solicita desvinculación con `POST /supervision-requests` y `type = unlink`.
- Aceptar o rechazar solicitudes usa la respuesta oficial del backend.
- Después de una desvinculación o respuesta exitosa, los repositorios vuelven a consultar perfil, solicitudes y/o lista de pacientes. No se elimina ni oculta una relación únicamente en memoria para simular éxito.

## Chat IA

El cierre específico para producción, incluyendo payload, modos, sanitización y errores, está documentado en `docs/AI_CHAT_PRODUCTION_AUDIT.md`.

El Chat IA se habilita solo si:

1. el supervisor está autorizado y activo;
2. existe al menos un paciente accesible en la lista oficial;
3. el consentimiento más reciente está activo e incluye `ai_chat_summary`.

Los pacientes pausados, revocados, sin acceso o desvinculados no forman parte del contexto local ni del conteo que habilita el chat.

## Validación ejecutada

- `./gradlew.bat :app:compileDebugKotlin`: exitosa.
- `./gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain`: exitosa; 81 pruebas, 0 fallos y 0 errores.
- `./gradlew.bat :app:assembleDebug --no-daemon --console=plain`: exitosa; APK debug generado.

## Riesgos restantes

- Una revocación remota que ocurra mientras la pantalla permanece abierta se detecta en el siguiente refresco/reentrada; no existe invalidación en tiempo real por push o socket.
- El mensaje de solicitud rechazada depende de que backend conserve `supervision_status` como `rejected` o `denied`; si devuelve directamente estado vacío, Android solo puede mostrar “Sin supervisor”.
- La prueba integral con cuentas reales requiere un backend local activo y credenciales de paciente/supervisor; las pruebas automatizadas validan repositorios y políticas, no sustituyen esa prueba instrumentada.
