# Auditoría de manejo de errores Android

Fecha: 31 de julio de 2026  
Alcance: contrato final de errores del backend, propagación a repositorios/ViewModels y presentación segura en UI.

## Contrato soportado

Android interpreta el envelope final:

```json
{
  "ok": false,
  "message": "Mensaje apto para la persona usuaria",
  "errors": {
    "field_name": ["Detalle de validación"]
  }
}
```

- `message` solo se acepta para códigos donde el backend puede aportar una explicación funcional: 400, 403, 404, 409, 422 y 429.
- Para 401, 500 y 502 siempre se usa texto controlado por Android; no se muestra el detalle interno del servidor, Firebase o proveedores.
- `errors` se procesa para 422 y acepta como valor un arreglo, una cadena o un objeto con `message`.
- Las claves `snake_case` se convierten a `camelCase` para enlazarlas con los campos de los formularios Compose.
- HTML, trazas, nombres de excepciones, SQL, rutas internas y referencias a infraestructura se descartan de la UI.

## Matriz de mensajes

| Condición | Mensaje/control aplicado |
|---|---|
| 400 | Solicitud con datos inválidos; puede usar mensaje funcional seguro del backend. |
| 401 | Sesión expirada; se intenta refresh en operaciones autenticadas. |
| 403 | Acción no permitida; puede conservar explicación funcional segura. |
| 404 | Recurso no encontrado; puede conservar explicación funcional segura. |
| 409 | Conflicto con el estado actual; nunca se interpreta como éxito. |
| 422 | Validación real; se muestran `message` y errores por campo. Nunca se interpreta como “sin cambios” exitoso. |
| 429 | Límite temporal de solicitudes; se solicita esperar. |
| 500 | Problema temporal del servicio; se oculta el detalle técnico. |
| 502 | Proveedor externo temporalmente no disponible; se oculta el detalle técnico. |
| Timeout | El servidor tardó demasiado; Chat IA conserva su texto contextual. |
| Sin conexión | Mensaje de red controlado; Chat IA indica que requiere conexión. |

## Flujo 401 y sesión

Los repositorios autenticados `PatientRepository`, `SupervisorRepository` y `AdminRepository` realizan el mismo flujo:

1. Ejecutan la solicitud con el Bearer token actual.
2. Ante 401, intentan una sola renovación con `refresh_token`.
3. Persisten el nuevo `id_token` y el `refresh_token` resuelto sin cambiar el rol ni el UID de la sesión.
4. Reintentan una vez la operación original.
5. Si faltan datos de sesión, falla el refresh o el reintento vuelve a responder 401, limpian la sesión y notifican `SessionInvalidationBus`.
6. La navegación global consume esa notificación y vuelve a login.

`auth/login` y `auth/register` no intentan refresh porque son operaciones públicas. Sus 401/422 se presentan como autenticación o validación fallida, respectivamente.

## Verificación por flujo

| Flujo | Resultado |
|---|---|
| Login | Mensaje central seguro; errores `email` y `password` junto al campo. |
| Registro | Errores de identidad, contraseña, rol, privacidad y perfil mapeados a los controles. |
| Notas | Códigos HTTP y red centralizados; 422 conserva errores por campo en el estado de guardado. |
| Agenda | Carga y guardado reciben mensajes amigables; validaciones llegan a los campos del evento. |
| Contactos | Carga y guardado reciben mensajes amigables; nombre, relación, teléfono y prioridad soportan errores por campo. |
| Consentimiento | 403/409/422 ya no se ocultan; el estado de acción muestra mensaje general y detalles de validación. |
| Vinculación | Código y supervisor se enlazan con `code`/`supervisorUid`; 404/409/422 se muestran sin texto técnico. |
| Desvinculación | 403/404/409/422/500 son errores reales y no producen un estado visual de éxito local. |
| Chat IA | Mantiene mensajes específicos para 401, 403, 429, 500, 502, timeout, falta de red y respuesta vacía, sin exponer contenido técnico. |

## Implementación

- `ApiErrorParser` recibe el código HTTP real y devuelve mensaje seguro más errores por campo.
- `ErrorMapper` es la fuente común para HTTP, timeout, falta de conexión y errores inesperados.
- Los resultados de paciente y supervisor transportan `fieldErrors` hasta los ViewModels.
- Los formularios existentes consumen esos errores sin rediseñar pantallas; las acciones sin campo editable muestran una lista corta debajo del mensaje principal.
- Se conservaron mensajes contextuales solo cuando aportan información funcional (por ejemplo, Chat IA), delegando la clasificación de la causa a `ErrorMapper`.

## Pruebas

- `ErrorHandlingContractTest`: matriz HTTP completa, timeout, falta de red, filtrado técnico, envelope 422, alias y formatos alternativos de validación.
- `SessionRefreshErrorTest`: refresh por 401, error 422/429/502 durante refresh, limpieza de sesión y mensaje final.
- `AuthRepositoryContractTest`: login/registro y errores de contrato.
- `PatientUpdateContractTest`: 422 real con errores por campo y operaciones de consentimiento/desvinculación.
- `PatientNotesRepositoryTest`, `NotificationSettingsContractTest`, `SupervisionContractV1Test`, `SupervisorConsentAccessTest` y `SupervisorAiChatContractTest`: cobertura de los flujos funcionales relacionados.

## Resultado de verificación

Comando ejecutado:

```text
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

- Resultado Gradle: `BUILD SUCCESSFUL` en 20 s.
- Pruebas: 89 ejecutadas, 0 fallos, 0 errores y 0 omitidas (19 suites).
- Ensamble: `app-debug.apk` generado correctamente.
- APK verificado en `app/build/outputs/apk/debug/app-debug.apk` (18,002,239 bytes).

## Riesgos restantes

- Las pruebas unitarias validan contrato y estados, pero no sustituyen una corrida instrumental con el backend local devolviendo cada variante de error.
- Los mensajes funcionales seguros de 400/403/404/409/422/429 dependen de que backend mantenga su contrato y no coloque información sensible fuera de los patrones técnicos conocidos.
- La navegación a login tras invalidación depende de que el colector global de `SessionInvalidationBus` permanezca activo durante la sesión de la app.
