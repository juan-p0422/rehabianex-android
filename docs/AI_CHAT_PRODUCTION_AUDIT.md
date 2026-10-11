# Cierre de producción del Chatbot IA en Android

Fecha: 2026-07-31

## Alcance

Este documento registra los controles aplicados al Chatbot IA del supervisor. No se cambiaron la URL local, el endpoint `POST /ai/supervisor-chat`, la navegación por roles ni el almacenamiento de sesión.

## Contrato enviado por Android

Android construye exclusivamente este payload:

```json
{
  "supervisor_uid": "uid de la sesión",
  "question": "pregunta sanitizada",
  "mode": "ai|local"
}
```

No adjunta pacientes, nombres, UID de pacientes, notas, agenda, contactos, métricas, consentimientos ni contexto clínico. El contexto autorizado se construye únicamente en backend.

## Controles antes de enviar

1. Se vuelve a consultar el perfil y se exige supervisor autorizado, verificado y con estado activo.
2. Se consulta consentimiento y se exige al menos un paciente accesible con scope activo `ai_chat_summary`.
3. Se rechazan preguntas vacías, menores de seis caracteres o mayores de 800 caracteres.
4. Se eliminan controles y etiquetas HTML.
5. Se redactan correos, teléfonos, identificadores largos y direcciones detectables como `[dato protegido]`.
6. La UI advierte que no deben escribirse nombres, teléfonos, correos, direcciones ni identificadores de pacientes.
7. Solo se aceptan los modos `ai` y `local`.

## Modos

- `ai`: solicita al backend una respuesta con proveedor IA.
- `local`: solicita al backend su respuesta local segura.
- `fallback_local`: el backend no pudo usar el proveedor y devolvió una respuesta local segura; Android muestra la respuesta y señala su origen.

Los modos `local` y `fallback_local` no ejecutan modelos, reglas clínicas ni generación de respuestas en el dispositivo. Todos requieren conexión y autorización de backend.

## Privacidad local

- Preguntas y respuestas solo existen en el `StateFlow` en memoria de la pantalla.
- Al reentrar al Chat se limpia la conversación antes de revalidar autorización y consentimientos.
- No se escriben mensajes en Room, DataStore, SharedPreferences ni archivos.
- El cliente HTTP usa logging `BASIC` únicamente en debug y ningún `HttpLoggingInterceptor` en release; por ello no imprime bodies con preguntas o respuestas.

## Mensajes y errores

| Caso | Comportamiento |
|---|---|
| `401` | Se intenta refresh; si la sesión sigue inválida, se invalida y se dirige al login mediante el flujo global existente. |
| `403` | Se vacían mensajes y contexto, se bloquea el Chat y se muestra falta de autorización/consentimiento. |
| `429` | Se informa el límite temporal de consultas. |
| `500`/`502` | Se informa indisponibilidad temporal sin detalles técnicos. |
| Timeout | Se informa que el Chat tardó demasiado. |
| Sin conexión | Se informa que el Chat IA requiere conexión; no se genera respuesta local en Android. |
| Respuesta vacía | Se rechaza como respuesta inválida y se permite reintentar. |
| `fallback_local` | Se muestra `answer` con etiqueta de respuesta local segura. |

## Aviso fijo

La pantalla presenta permanentemente:

> Esta herramienta no sustituye atención médica, psicológica ni terapéutica.

## Pruebas automatizadas

La suite cubre:

- supervisor no autorizado;
- filtro por `ai_chat_summary`;
- pregunta vacía y longitud máxima;
- sanitización de HTML y PII detectable;
- payload con únicamente `supervisor_uid`, `question` y `mode`;
- modos `ai` y `local`;
- respuesta `fallback_local`;
- `401`, `403`, `429`, `500`, `502`, timeout y respuesta vacía;
- apertura y envío sin conexión;
- ausencia de bodies en logging HTTP;
- no anexar identificadores locales de pacientes.

Resultado: 86 pruebas del proyecto, 0 fallos y 0 errores.

## Compilación

- `./gradlew.bat :app:compileDebugKotlin`: exitosa.
- `./gradlew.bat :app:assembleDebug --no-daemon --console=plain`: exitosa; APK debug generado.

## Riesgos restantes

- Un nombre libre escrito por el supervisor no puede detectarse con certeza sin comparar contra identidades de pacientes. Android advierte al usuario y backend conserva la sanitización autoritativa de nombres/nicknames conocidos.
- La autorización puede cambiar mientras la pantalla permanece abierta; se revalida al entrar y nuevamente antes de cada pregunta, pero no existe invalidación en tiempo real por socket o push.
- La prueba visual integral con backend real requiere credenciales de supervisor autorizado y al menos un consentimiento `ai_chat_summary` activo.
