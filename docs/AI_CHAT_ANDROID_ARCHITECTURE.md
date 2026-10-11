# Arquitectura Android del Chat IA

Última actualización: 2026-08-04.

## Navegación de la pantalla

El Chat conserva un `Scaffold` propio porque su campo de entrada debe permanecer
anclado. Ese `Scaffold` reutiliza la misma barra inferior del supervisor con las
rutas `Inicio`, `Pacientes` y `Chat`.

- La acción `Atrás` y el botón Atrás del sistema regresan a la pantalla previa;
  si no existe una entrada previa, se usa Inicio supervisor como destino seguro.
- El botón `Perfil` abre `SupervisorProfile`, desde donde se mantiene el cierre
  de sesión existente.
- Los elementos de la barra inferior invocan el mismo callback `onNavigate` del
  grafo de supervisor.
- Un supervisor con acceso limitado continúa viendo únicamente Inicio; la
  extracción de la barra compartida no amplía sus permisos.

Este ajuste es exclusivamente de navegación. No cambia el request, el endpoint,
la elegibilidad, el almacenamiento ni el tratamiento de las respuestas del Chat.

## Payload y responsabilidad

Android envía a `POST /ai/supervisor-chat` únicamente:

```json
{
  "supervisor_uid": "uid",
  "question": "pregunta sanitizada",
  "mode": "ai"
}
```

`mode` admite `ai` y `local`. Ambos modos son procesados por backend y requieren
conexión. Android no adjunta UID de pacientes, notas, métricas, contactos,
agenda, nombres ni otro contexto clínico. Backend construye y autoriza el
contexto final.

## Elegibilidad ligera

Al abrir la pantalla se realizan como máximo dos consultas de validación:

1. `GET /auth/me`: confirma `authorized=true`, `verified=true` y `status=active`.
2. `GET /consents?supervisor_uid=...`: verifica al menos un consentimiento
   activo y explícito que contenga `ai_chat_summary`.

No se requiere el scope `patient_notes`. Tampoco se consultan solicitudes
pendientes, la lista detallada de pacientes ni `GET /patients/{uid}/notes`.

La elegibilidad se conserva únicamente en memoria mientras vive el repositorio.
Después de cargar la pantalla, cada pregunta ejecuta directamente
`POST /ai/supervisor-chat`; no repite el preflight. Backend mantiene la decisión
final y puede responder `403` si el supervisor dejó de estar autorizado o `409`
si ya no existe contexto elegible.

## Comparación de llamadas

| Operación | Antes | Ahora |
|---|---:|---:|
| Abrir Chat | `4 + N` llamadas (`N` = pacientes cuyas notas se descargaban) | 2 llamadas |
| Enviar cada pregunta | `5 + N` llamadas incluyendo el POST | 1 llamada |

La mejora elimina el crecimiento lineal por paciente y reduce la latencia previa
a cada pregunta a cero llamadas adicionales. Un `401` aún puede provocar el
refresh y un único reintento, conforme a la política general de sesión.

## Privacidad y almacenamiento

- El Chat es network-only; sin conexión no intenta generar respuestas locales.
- Pregunta y respuesta no se escriben en Room ni en preferencias.
- Los mensajes existen sólo en el `StateFlow` de la pantalla y desaparecen al
  destruir su `ViewModel`.
- El cliente HTTP usa logging `BASIC` en debug y ningún interceptor de logging en
  release; no registra cuerpos, preguntas ni respuestas.
- La UI mantiene visible: “Esta herramienta no sustituye atención médica,
  psicológica ni terapéutica.”

## Riesgos restantes

- La consulta ligera de consentimientos usa el límite contractual de 50. Si un
  supervisor supera ese volumen y el backend pagina sin incluir un consentimiento
  elegible en la primera respuesta, la pantalla podría requerir reintento o una
  consulta de elegibilidad dedicada.
- La elegibilidad en memoria puede quedar obsoleta durante una sesión. Esto no
  expone datos porque backend vuelve a validar cada POST y Android transforma
  `403/409` en estado bloqueado con mensaje amigable.
- El historial visible es efímero. Persistirlo requeriría una política legal,
  cifrado y retención aprobados; actualmente no se implementa.
