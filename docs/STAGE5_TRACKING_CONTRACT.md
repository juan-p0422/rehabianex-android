# Contrato de etapa 5: registro y seguimiento

Este documento registra el acuerdo mínimo entre Android y la API. No sustituye el contrato OpenAPI ni cambia hitos históricos.

## Registro emocional

- `POST /api/patients/{uid}/notes` conserva sus campos actuales.
- Las escalas requieren una selección explícita. `0` es una respuesta válida; un campo ausente no equivale a cero.
- `triggers` y `coping_actions` son arreglos de textos no vacíos, distintos y de hasta 120 caracteres por elemento.
- La opción visual “Otro” se envía como el texto escrito dentro del arreglo correspondiente, sin un campo paralelo.
- `client_mutation_id` conserva la idempotencia existente y la cola local se limpia solo tras confirmación remota.

## Historial

- “Últimos 7 días” significa hoy y los seis días calendario anteriores, usando la zona horaria del dispositivo.
- “Mes actual” significa desde el primer día del mes local hasta hoy.
- La UI muestra el rango inclusivo y el número real de registros. Los promedios ignoran valores ausentes, pero conservan el cero como valor contestado.
- Las métricas son resúmenes de autorreportes; no son indicadores de eficacia clínica.

## Fecha de sobriedad

- Se mantiene `PATCH /api/patients/{uid}` con `sobriety_start_date` en formato `AAAA-MM-DD`.
- Una fecha futura responde `422`.
- Android solicita confirmación y explica que el cambio recalcula días y progreso de logros sin borrar registros.

## Agenda y asistencia

- `status` continúa separado con los valores `scheduled`, `completed` y `cancelled`.
- `PATCH /api/agenda-events/{id}` admite `attendance_reported: true | false | null`.
- Solo el paciente propietario puede autorreportar asistencia. La API puede devolver `attendance_reported_at` y `attendance_reported_by` para trazabilidad.
- Una cita completada no implica asistencia; una cita agendada tampoco se presenta como completada.

## Compatibilidad

- Los registros, eventos y cuentas existentes siguen siendo legibles.
- Los nuevos campos de asistencia son nulos para eventos históricos.
- La base local migra de versión 4 a 5 agregando columnas nulas; no elimina datos.
