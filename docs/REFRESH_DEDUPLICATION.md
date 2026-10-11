# Política de refresh y deduplicación

Fecha: 2026-08-05

## Problema corregido

Varias pantallas ejecutaban `load()` durante la creación del ViewModel y nuevamente en `ON_RESUME`. Esto permitía dos cadenas Retrofit simultáneas para `auth/me`, notas, consentimientos y listas.

## Patrón implementado

`StaleRefreshController` aplica a cada pantalla:

- TTL de 20 segundos para resultados exitosos.
- Un solo `Job` activo por ViewModel.
- Una solicitud `refreshIfStale()` se descarta si existe otra activa o el resultado sigue fresco.
- `force=true` cancela el trabajo previo y ejecuta una nueva carga.
- El TTL solo se actualiza después de un resultado exitoso.
- `load()` queda como alias forzado para reintentos explícitos; `ON_RESUME` usa la variante no forzada.
- Las mutaciones invalidan una versión compartida por rol (`patient`, `supervisor` o `admin`). Esto permite que una modificación hecha en otra pantalla venza el TTL sin provocar múltiples llamadas.

No se agregaron logs con payloads, UID, notas, preguntas o respuestas.

## Estados de UI

- Sin contenido: estado `Loading` y skeleton existente.
- Con contenido: se conserva el contenido y se marca `isRefreshing`.
- Error temporal con contenido: se conserva el contenido y se expone `warning/errorMessage`.
- `401/403` de supervisor: se elimina el contenido previo para no mantener información después de perder permisos.
- `lastSyncedAt` se conserva en `OfflineStatus`, modelos cache-first o estados supervisor/admin.

## Pantallas incluidas

Paciente:

- Home, métricas, logros, contactos, agenda, directorio local, perfil y notificaciones.

Supervisor:

- Home, pacientes, detalle de paciente, perfil, notificaciones y Chat IA.

Administrador:

- panel y filtros de supervisores.

## Mutaciones forzadas/invalidadas

- Nota emocional nueva.
- Contacto creado o editado.
- Evento creado, completado o cancelado.
- Vinculación, consentimiento, revocación y desvinculación.
- Privacidad/perfil.
- Preferencias de notificaciones.
- Aceptar/rechazar solicitudes y desvincular pacientes como supervisor.
- Autorizar, rechazar, suspender o reactivar como administrador.

## Reducción de llamadas

| Flujo | Antes | Después dentro del TTL |
|---|---:|---:|
| `init` + primer `ON_RESUME` | 2 cadenas concurrentes | 1 cadena |
| Regreso inmediato a Home/Perfil | 1 cadena adicional | 0 llamadas |
| Regreso después de 20 s | 1 cadena | 1 cadena silenciosa |
| Regreso tras mutación | comportamiento no coordinado | 1 refresh forzado/invalidation-aware |
| Home supervisor con N pacientes | `auth/me` + solicitudes + lista + N consultas de notas | `auth/me` + solicitudes + lista; 0 consultas de notas |
| Chat IA | perfil + consentimientos | perfil + consentimientos; 0 consultas de notas |

La lista y detalle de pacientes continúan network-first/network-only por privacidad. El detalle descarga notas porque las muestra; Home y Perfil supervisor ya no lo hacen.

## Pruebas

- `StaleRefreshControllerTest`: deduplicación in-flight, TTL, expiración, cancelación forzada e invalidación entre pantallas.
- `SupervisorAccessFlowTest`: Home supervisor usa una sola lista y no llama `patientNotesByPatient`.
- La prueba de revocación existente confirma que un `403` reemplaza el detalle previo y no conserva datos sensibles.

Validación final:

- `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain`: `BUILD SUCCESSFUL`.
- 147 pruebas en 29 suites, 0 fallos y 0 omitidas.
- Auditoría estática de navegación: 0 llamadas directas a `viewModel.load()` desde NavGraphs y 15 llamadas TTL-aware a `viewModel.refreshIfStale()`.

## Riesgos restantes

- El TTL es local al proceso; tras recreación completa se vuelve a consultar, comportamiento intencional.
- Las pantallas de lista/detalle supervisor aún pueden consultar notas cuando realmente las muestran. Una optimización adicional requeriría un endpoint backend agregado de métricas.
- La medición de llamadas con Network Inspector en un dispositivo autenticado sigue recomendada para validar latencia real y comportamiento del fabricante.
