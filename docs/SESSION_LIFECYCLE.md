# Ciclo de sesión Android

Última actualización: 2026-08-03.

## Alcance

Este documento registra el cierre remoto/local, la renovación de tokens y la
navegación posterior a autenticación. No modifica las rutas funcionales de
paciente, supervisor o administrador y no cambia la URL de release.

## Logout

Android consume `POST /auth/logout` con el Bearer vigente. El cierre sigue esta
secuencia:

1. Captura el UID y rol antes de eliminar credenciales.
2. Consulta si existen notas emocionales pendientes. Si existen, detiene el
   cierre y pide confirmación antes de perderlas.
3. Tras confirmación, intenta revocar la sesión en backend.
4. Cancela el trabajo único de sincronización de notas del UID y la
   reprogramación de notificaciones.
5. Cancela recordatorios por rol, alarmas de agenda conocidas y notificaciones
   visibles.
6. `OfflineDataCleanupCoordinator` elimina primero Room sincronizado: notas, agenda,
   contactos, configuración de notificaciones, logros del paciente, resumen y
   metadatos de sincronización del UID. También elimina nombre/nickname local y
   preferencias locales de notificación del supervisor.
   Las notas pendientes solo se borran después de la confirmación explícita.
7. Elimina siempre ID token y refresh token mediante `SessionManager.clear()`.

Los catálogos globales de recursos locales y logros no se eliminan porque no
pertenecen a un usuario y no contienen el seguimiento personal. Si el logout
remoto falla, la UI vuelve a login y puede mostrar un aviso controlado; nunca se
conservan tokens por ese fallo.

## Refresh

| Resultado de `POST /auth/refresh` | Sesión local | Comportamiento |
|---|---|---|
| 200 con tokens válidos | Se conserva y actualiza | Reintenta una vez la solicitud original |
| 401 | Se elimina | Notifica expiración y vuelve a login |
| Refresh token local ausente | Se elimina | La sesión local es definitivamente inválida |
| 422 | Se conserva | Muestra validación, sin maquillar éxito |
| 429 | Se conserva | Muestra límite temporal |
| 500 o 502 | Se conserva | Muestra indisponibilidad temporal |
| Timeout o sin conexión | Se conserva | Muestra error de red; permite reintentar |
| Respuesta 200 incompleta | Se conserva | Muestra respuesta incompleta; no asume revocación |
| Segundo 401 tras refresh válido | Se elimina | El token renovado no fue aceptado |

El contrato backend vigente normaliza un refresh token inválido, expirado o
revocado como `401`; por ello ese código es la señal definitiva de invalidación
remota.

## Navegación

- Login usa exclusivamente el rol confirmado en la respuesta del backend.
- Registro autenticado de supervisor/admin entra a su home correspondiente.
- Registro autenticado de paciente conserva la pantalla de consentimiento y,
  al continuar, abre Patient Home según el rol confirmado.
- Si ya hay una sesión activa, `RoleSelection` redirige al home del rol y no
  presenta el selector manual.
- Si un login devuelve un UID diferente al de la sesión previa, Android cancela
  el WorkManager anterior, limpia memoria/caché y datos identificables del UID
  previo antes de persistir la sesión nueva.

## Pruebas automatizadas

- Logout remoto correcto limpia datos y sesión.
- Fallo de red en logout limpia datos y sesión y devuelve aviso controlado.
- Fallo de limpieza local no impide borrar tokens.
- Logout con notas pendientes no llama backend ni borra sesión hasta confirmar.
- Login con UID distinto limpia al propietario anterior antes de guardar al nuevo.
- Refresh 422, 429, 500 y 502 conserva sesión.
- Timeout de refresh conserva sesión.
- Refresh 401 elimina sesión.
- Refresh correcto actualiza tokens y reintenta la operación.
- Navegación de paciente, supervisor y admin usa el rol confirmado.

## Riesgos y decisiones pendientes

- Room está cifrado con SQLCipher y clave envuelta por Android Keystore. Aun así,
  logout pide confirmación antes de eliminar notas pendientes y el cambio de
  usuario aplica borrado de privacidad del UID anterior; el cifrado no reemplaza
  la separación lógica ni la política de purga.
- El borrado Room es tolerante a fallos por recurso para garantizar que el token
  se elimine. Una futura capa de telemetría no sensible podría reportar fallos de
  limpieza sin incluir UID ni contenido clínico.
- El endpoint puede no confirmar revocación si el dispositivo pierde conexión;
  Android no puede reintentar después del cierre porque elimina de inmediato las
  credenciales, como exige la política local.
