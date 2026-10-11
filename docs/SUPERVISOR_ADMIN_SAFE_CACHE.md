# Caché segura de supervisor y administrador

Fecha: 2026-08-05

## Alcance

Se agregó una capa cache-first limitada a información propia de la cuenta y conteos agregados. No se modificó el contrato HTTP ni se creó persistencia local para información clínica o de terceros.

## Datos persistidos

Room v4 incorpora:

- `supervisor_profile_snapshot`: UID propio, nombre visible, tipo/código de supervisor, autorización, verificación, estado y fecha de sincronización.
- `supervisor_dashboard_summary`: cantidades de pacientes vinculados, solicitudes pendientes e intervenciones abiertas. No contiene identificadores de pacientes.
- `admin_profile_snapshot`: UID propio, nombre visible, correo propio si ya forma parte de la sesión, estado y fecha de sincronización.
- `admin_dashboard_summary`: cantidades de supervisores pendientes, activos y suspendidos.

Los cuatro recursos se identifican por `ownerUid`, permanecen dentro de la base SQLCipher existente y se purgan en logout o cambio de usuario mediante `OfflineDataCleanupCoordinator`.

## Datos excluidos deliberadamente

No se agregaron entidades ni DAOs para:

- pacientes supervisados;
- notas, agenda o contactos de terceros;
- intervenciones clínicas;
- preguntas, respuestas o historial de Chat IA;
- lista completa de supervisores administrables.

Pacientes y detalle de paciente continúan network-first/network-only. Chat IA continúa network-only. La lista admin se mantiene solo en memoria y su contenido cacheado nunca se considera autoridad.

## Estrategia de lectura

### Supervisor

Home y Perfil consultan primero el snapshot seguro. Si existe, se muestra inmediatamente con `Actualizando...` y se refresca desde API sin reemplazar la pantalla por un loader. Ante error de red se conserva el snapshot y se muestra la hora de la última actualización. El resumen cacheado no muestra nombres, alertas ni solicitudes de pacientes.

Una respuesta 401 o 403 en cualquier operación del repositorio supervisor elimina centralmente el snapshot propio y sus contadores.

### Administrador

El panel muestra inmediatamente el resumen agregado disponible y refresca la pestaña seleccionada desde API. Mientras la lista no sea confirmada por red, se informa que el resumen no habilita acciones. Autorizar, rechazar, suspender y reactivar requieren conectividad validada antes de invocar Retrofit.

Una respuesta 401 o 403 elimina los snapshots administrativos. Las listas completas nunca se escriben en Room.

## Migración

La base pasa de versión 3 a 4 mediante `MIGRATION_3_4`. La migración solo crea las cuatro tablas seguras; no transforma ni copia datos clínicos existentes.

## Pruebas automatizadas

`SafeRoleCacheTest` cubre:

- lectura cacheada del Home supervisor sin pacientes ni solicitudes;
- purga de snapshot supervisor ante 403;
- lectura cacheada del resumen admin;
- bloqueo de acciones admin sin conexión y sin llamadas HTTP;
- ausencia de DAOs Room para pacientes supervisados y Chat IA.

## Riesgos y límites

- Los conteos pueden quedar desactualizados sin conexión y se presentan únicamente como última referencia.
- `openInterventionsCount` queda en cero hasta que backend exponga un resumen agregado confiable; no se descargan intervenciones para calcularlo.
- La autorización cacheada permite renderizar la estructura del Home, pero no concede acceso a datos ni operaciones sensibles; esas rutas siguen validadas por backend.
- La prueba de migración cifrada en dispositivo sigue siendo recomendable además de las pruebas JVM.
