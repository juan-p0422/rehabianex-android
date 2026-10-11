# QA Android local — 2026-08-04

## Alcance y entorno

- Aplicación: variante `debug` instalada en un emulador Android API 33.
- API usada por Android: `http://10.0.2.2:8000/api/`.
- Backend: Laravel local escuchando en `0.0.0.0:8000`.
- Servicios de prueba: Firebase Auth y Firestore Emulator en un proyecto aislado con prefijo `demo-`.
- Datos: cuentas y contenido sintéticos; no se usaron cuentas ni datos reales.
- Render y release quedaron fuera del alcance. No se hizo push ni se modificó código de producción.

## Resultado automatizado

Comando ejecutado:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain
```

Resultado:

- Build: `BUILD SUCCESSFUL` en 11 s.
- Pruebas JVM: 124 ejecutadas, 0 fallos, 0 errores, 0 omitidas, distribuidas en 24 suites.
- Lint: 0 errores y 34 advertencias ya reportadas.
- APK debug: generado correctamente en `app/build/outputs/apk/debug/app-debug.apk`.

## Matriz del flujo manual

| # | Caso | Resultado | Evidencia observada |
|---:|---|---|---|
| 1 | Registro paciente con aviso legal | Pasa | La UI exigió el aviso versión `2026-08-01`, separó la aceptación legal del consentimiento de supervisión y el backend aceptó `POST /auth/register`. |
| 2 | Login paciente | Pasa | Sesión iniciada y rol paciente confirmado mediante `auth/me`. |
| 3 | Home | Pasa | Cargó progreso, estado reciente y accesos rápidos desde API/Room. |
| 4 | Crear nota online | Pasa | Mostró “Registro guardado” y el backend confirmó la creación. |
| 5 | Activar modo offline | Pasa | Android quedó sin red activa y la app mostró “Modo sin conexión”. |
| 6 | Crear nota offline | Pasa | Guardó la nota local y mostró un registro pendiente de sincronización. |
| 7 | Reactivar red | Pasa | Android recuperó una red activa y WorkManager volvió a ser elegible. |
| 8 | Ver sincronización | Pasa | El indicador pendiente desapareció y el backend recibió la sincronización con WorkManager. |
| 9 | Perfil cache-first | Pasa | Sin red, el perfil apareció inmediatamente con “Mostrando datos guardados”; no hubo pantalla de carga completa. |
| 10 | Modo anónimo e identidad local | Parcial | `PatientIdentityStore` dejó de contener el nombre real y Perfil mostró “Paciente anónimo”; sin embargo, Home volvió a mostrar el nombre registrado tras iniciar sesión otra vez. Ver QA-02. |
| 11 | Contactos offline | Pasa | Un contacto creado online siguió disponible sin conexión, incluida la acción de llamada rápida. |
| 12 | Agenda offline | Pasa | Un evento creado online siguió visible sin conexión. |
| 13 | Permiso de notificaciones | Pasa con observación | Denegar no produjo crash; conceder permitió publicar una notificación neutra: “Tienes novedades en tu agenda. Abre la app para consultarlas.” Ver QA-03. |
| 14 | Registro/login supervisor | Pasa | El alta quedó en `pending_review`; la app mostró acceso limitado. Tras autorización administrativa local e inicio de sesión nuevo, mostró acceso autorizado y código corto. |
| 15 | Vinculación `link` | Pasa | `supervisors/resolve` mostró nombre, tipo y disponibilidad; después de confirmar se creó la solicitud y el supervisor pudo aceptarla. |
| 16 | Consentimiento `active/paused/revoked` | Pasa | Se activaron notas, agenda, contactos y `ai_chat_summary`; la pausa conservó la vinculación sin acceso y la revocación retiró los datos compartidos. |
| 17 | Detalle supervisor tras revocación | Pasa | La lista cambió a “Paciente sin acceso”; no mostró notas ni métricas y la UI impidió abrir el detalle sensible. |
| 18 | Chat IA, modo local | Pasa | “Local seguro” llamó `POST /ai/supervisor-chat` y recibió respuesta procesada en servidor. Sin red no generó contenido local y mostró que el Chat IA requiere conexión. |
| 19 | Logout real | Pasa | En varios cambios de cuenta se observó `POST /auth/logout`; la sesión local se eliminó y se volvió a Login. |
| 20 | Cambio de usuario y aislamiento | Parcial | Los cambios paciente ↔ supervisor no mostraron el Home, contactos o agenda propios del UID anterior. La validación final posterior al Chat quedó bloqueada por QA-01; las pruebas JVM de seguridad/outbox pasaron, pero falta repetir el último tramo con una segunda cuenta paciente en dispositivo. |

## Bugs y observaciones

### QA-01 — Bloqueante: Chat IA no ofrece salida ni acceso a logout

- Severidad: alta.
- Reproducción: entrar como supervisor autorizado, abrir Chat y pulsar Atrás.
- Resultado: `AiChatScreen` no muestra navegación inferior ni botón de regreso. El botón Atrás del sistema cierra la actividad; al abrir nuevamente, la sesión regresa al Chat.
- Impacto: desde esa pantalla no se puede llegar a Perfil/Cerrar sesión. Bloqueó el cierre exacto de los pasos 19–20 después del Chat.
- Ubicación probable: `AiChatScreen.kt` recibe `onNavigate`, pero no lo usa; `ChatHeader` solo presenta el título.
- Recomendación: agregar atrás o conservar la navegación del supervisor, sin alterar el contrato de IA.

### QA-02 — Privacidad: Home ignora el modo anónimo

- Severidad: alta.
- Resultado: Perfil mostró “Paciente anónimo” y la preferencia de identidad ya no contenía el nombre real, pero Home volvió a saludar con el nombre registrado tras un login nuevo.
- Impacto: exposición visual del nombre real pese a estar activo el modo anónimo.
- Recomendación: resolver el nombre de Home desde el snapshot de privacidad y no desde `auth.display_name` cuando `privacyMode/isAnonymous` estén activos; limpiar también el estado Compose al cambiar el modo.

### QA-03 — Permiso de notificaciones denegado sin confirmación visible

- Severidad: media-baja.
- Resultado: denegar `POST_NOTIFICATIONS` es seguro y no genera crash, pero la pantalla vuelve a mostrar “Activar recordatorios” sin explicar que el permiso fue denegado.
- Recomendación: mostrar un mensaje breve y, si Android ya no permite volver a solicitarlo, ofrecer abrir Ajustes.

### QA-04 — Refrescos duplicados observables

- Severidad: baja, rendimiento.
- Resultado: el log local mostró pares de consultas consecutivas a `auth/me`, notas, consentimientos, solicitudes y listas de pacientes al entrar o volver a algunas pantallas.
- Riesgo: más latencia y carga local; no se observó duplicación funcional gracias a la idempotencia de notas.
- Recomendación: revisar la combinación de carga inicial y `ON_RESUME` antes de pruebas de rendimiento, sin refactor urgente.

### QA-05 — Evento nuevo puede quedar inmediatamente “Completado”

- Severidad: baja, UX.
- Resultado: el formulario tomó la hora actual; al terminar de capturar y guardar, esa hora ya había pasado y el evento apareció como completado.
- Recomendación: iniciar eventos nuevos en el siguiente intervalo futuro, por ejemplo 15 minutos.

## Capturas sugeridas para evidencia

1. Registro con checkbox legal, versión y texto que separa el consentimiento.
2. Home online y Home offline con la tarjeta de un cambio pendiente.
3. Mensaje de nota offline y la misma pantalla después de sincronizar.
4. Perfil offline con “Mostrando datos guardados”.
5. Perfil en modo anónimo y, para documentar QA-02, Home mostrando aún el nombre real.
6. Contacto y evento visibles sin conexión.
7. Diálogo del permiso de notificaciones denegado/concedido y notificación neutra.
8. Supervisor pendiente y supervisor autorizado con código corto.
9. Vista previa de `supervisors/resolve` y solicitud pendiente.
10. Consentimiento activo, pausado y revocado.
11. Lista del supervisor con “Paciente sin acceso” después de revocar.
12. Chat en modo Local seguro, aviso terapéutico y error sin conexión.
13. Chat sin control de regreso/navegación para evidenciar QA-01.

## Bloqueos y preparación para dispositivo físico

- Sustituir la URL del emulador por `http://IP_LOCAL_PC:8000/api/` únicamente en debug, por ejemplo:

```powershell
.\gradlew.bat :app:assembleDebug -PDEBUG_API_BASE_URL=http://IP_LOCAL_PC:8000/api/ --no-daemon --console=plain
```

- El teléfono y la PC deben estar en la misma red y sin aislamiento de clientes.
- Laravel debe escuchar en `0.0.0.0:8000` y el firewall debe permitir el puerto 8000 solo en la red privada de prueba.
- Verificar desde el navegador del teléfono una ruta de salud local antes de abrir Android.
- HTTP local está permitido solo por la configuración debug. No generar release con una URL local o con un override de pruebas.
- Firebase Emulator permanece detrás del backend; el teléfono no debe conectarse directamente a sus puertos.
- QA-01 debe corregirse antes de repetir el flujo continuo completo en un dispositivo, porque impide cerrar sesión desde Chat.

## Cambios de esta ejecución

- Código Android modificado: ninguno.
- Configuración de release: sin cambios.
- Documento agregado: `docs/LOCAL_ANDROID_QA_2026-08-04.md`.
- Estado local de QA: se crearon datos sintéticos únicamente en los emuladores de Firebase y se autorizó un supervisor sintético mediante el comando administrativo local del backend.
