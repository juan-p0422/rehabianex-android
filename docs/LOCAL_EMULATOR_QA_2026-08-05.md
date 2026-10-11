# QA Android local en emulador — 2026-08-05

## Alcance y entorno

- Emulador: `emulator-5554`, Android 13.
- Backend usado exclusivamente: `http://10.0.2.2:8000/api/` desde Android y `http://127.0.0.1:8000/api/` desde el host.
- Render no fue consultado.
- No se hizo push.
- El workspace no expone un repositorio Git utilizable.
- Se crearon dos cuentas efímeras en el backend local/Firebase de desarrollo: un paciente y un supervisor pendiente. Las credenciales no se documentan.

## Validación Gradle

Comandos ejecutados:

```powershell
./gradlew.bat :app:clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain
./gradlew.bat :app:connectedDebugAndroidTest --no-daemon --console=plain
```

Resultados:

- Build limpio: correcto.
- Pruebas unitarias: 147 ejecutadas, 147 aprobadas, 0 fallos.
- Pruebas instrumentadas: 7 ejecutadas, 7 aprobadas, 0 fallos.
- Lint: 0 errores y 32 advertencias.
- APK debug generado correctamente.
- No hubo `FATAL EXCEPTION` ni ANR de RehabiAnex durante el recorrido.

Las pruebas instrumentadas cubren apertura/cifrado de Room, reemplazo de base sin cifrar, purga por logout/cambio de usuario, aislamiento por `ownerUid`, tombstones y disponibilidad offline de notas/agenda.

## Matriz manual

| Área | Caso | Estado | Evidencia/observación |
|---|---|---:|---|
| Auth | Login inválido | Aprobado | `POST /auth/login` devolvió 401 y la UI mostró un mensaje amigable. |
| Auth | Registro paciente | Parcial | El contrato real creó la cuenta con 201; el envío completo desde Compose no se certificó por inestabilidad de automatización ADB del teclado. |
| Auth | Login paciente | Aprobado | 200, navegación a Home paciente. |
| Auth | Logout real | Aprobado | `POST /auth/logout` devolvió 200 y la UI regresó a Login. |
| Auth | Login supervisor | Aprobado parcialmente | Supervisor nuevo entra como pendiente y ve acceso limitado. |
| Auth | Login admin | Bloqueado | No se proporcionó una cuenta admin de QA. |
| Auth | Fallo temporal de refresh | Aprobado | Sin red, el supervisor conservó sesión y snapshot; pruebas unitarias cubren timeout, 429, 500/502 y 401 definitivo. |
| Paciente | Home | Aprobado | Carga identidad segura, métricas y accesos rápidos. |
| Paciente | Perfil cache-first | Parcial | Perfil online correcto; el comportamiento cache-first está cubierto por mapper/Room, pero no se repitió el perfil paciente completo offline. |
| Paciente | Modo anónimo | Aprobado | Tras activar, logout y relogin, Home mostró `Hola, Paciente anónimo`; el nombre real no apareció. |
| Paciente | Nota online | Aprobado | `POST /patients/{uid}/notes` devolvió 201. |
| Paciente | Nota offline | Aprobado | La UI informó que quedó guardada y pendiente de sincronización. |
| Paciente | Recuperar red/sincronizar | Aprobado | WorkManager terminó con `SUCCESS`; backend confirmó dos notas y `client_mutation_id` en la nota offline. |
| Paciente | Reintento de nota | Aprobado parcialmente | Se comprobó pending offline y sincronización al recuperar red; no se inyectó un 500/502 ambiguo real. La política está cubierta por pruebas unitarias. |
| Paciente | Contactos offline | Aprobado parcialmente | Pantalla estable y mensaje sin conexión; la cuenta no tenía contactos para validar contenido cacheado. |
| Paciente | Agenda offline | Aprobado | Calendario cacheado/estado offline visible, sin crash. |
| Notificaciones | Permiso denegado | Aprobado | Banner persistente y botón `Abrir ajustes`. |
| Notificaciones | Abrir Ajustes | Aprobado | Abrió `InstalledAppDetails` de RehabiAnex. |
| Notificaciones | Permiso concedido al volver | Aprobado | `ON_RESUME` ocultó el banner; permiso quedó concedido. |
| Supervisor | Snapshot seguro | Aprobado | Perfil propio disponible offline y sesión conservada. |
| Supervisor | Autorización pendiente | Aprobado | Bloquea pacientes, solicitudes y Chat IA con mensaje claro. |
| Supervisor | Home autorizado | Bloqueado | No hubo supervisor autorizado de QA. |
| Supervisor | Chat: entrar/salir/navegación | Bloqueado manual / aprobado unitario | Supervisor pendiente no puede acceder; pruebas unitarias validan rutas, payload y ausencia de precarga de notas. |
| Supervisor | Lista de pacientes no cacheada | Aprobado estructural | `SafeRoleCacheTest` confirma que Room no expone DAO de pacientes supervisados ni Chat IA. |
| Supervisor | Detalle borrado ante 403 | Bloqueado manual | Requiere supervisor autorizado, paciente vinculado y consentimiento revocable. |
| Admin | Login/Home/acciones offline/logout | Bloqueado manual / aprobado unitario | Sin cuenta admin de QA. Unit tests cubren ruta admin, repositorio, snapshots y rechazo de acciones offline. |
| Rendimiento | Duplicados en Home paciente | Aprobado | En arranque se observó una llamada por `auth/me`, notas oficiales, agenda y settings. |
| Rendimiento | Volver a Home | Aprobado | Mostró caché con indicador discreto y una sola actualización por endpoint. |
| Rendimiento | `/patient-notes` adicional | Esperado | Es reconciliación de outbox; métricas usan `/patients/{uid}/notes`. No es una segunda consulta idéntica. |
| Rendimiento | Consentimientos/solicitudes | Bloqueado | La cuenta no tenía vínculo de supervisión. |
| Seguridad | Logout limpia caché visible | Aprobado | Después de logout solo se mostró Login. |
| Seguridad | Cambio de usuario | Aprobado | Supervisor nuevo no vio notas, métricas ni identidad del paciente anterior. |
| Seguridad | Anonimato purga display real | Aprobado | Verificado después de logout/login. |
| Seguridad | Chat no se guarda en Room | Aprobado estructural | No existe superficie DAO/Entity de Chat IA; prueba unitaria explícita aprobada. |

## Hallazgos y severidad

### Bloqueos de QA — severidad alta para despliegue

1. No se pudo validar manualmente el rol admin por falta de cuenta de QA.
2. No se pudo validar supervisor autorizado, Chat IA real, lista/detalle de pacientes, 403 posterior a revocación ni consentimiento active/paused/revoked.
3. No se puede afirmar cobertura exhaustiva de `consents` y `supervision-requests` sin un paciente y supervisor enlazados.

Estos puntos son bloqueos de cobertura, no fallos funcionales confirmados.

### Deuda no bloqueante

- Lint conserva 32 advertencias. La advertencia `InsecureBaseConfiguration` corresponde al XML exclusivo de debug que permite HTTP local; debe mantenerse fuera de release.
- El registro paciente fue validado contra el mismo backend y contrato, pero falta repetir el envío completo desde la UI con interacción humana o una prueba Compose estable.
- Las cuentas efímeras creadas deben eliminarse del backend de desarrollo cuando termine la campaña QA si no existe una política automática de limpieza.

## Pasos de reproducción relevantes

### Nota offline y sincronización

1. Login paciente.
2. Abrir Diario y guardar una nota online.
3. Desactivar Wi-Fi/datos del emulador.
4. Guardar otra nota.
5. Confirmar mensaje de pendiente de sincronización.
6. Reactivar Wi-Fi.
7. Volver a Home y esperar WorkManager.
8. Confirmar en backend ambas notas y `client_mutation_id` para la mutación offline.

### Anonimato persistente

1. Perfil > Configurar privacidad > Anónimo.
2. Guardar y confirmar `Paciente anónimo`.
3. Cerrar sesión.
4. Iniciar sesión nuevamente.
5. Confirmar que Home no contiene el nombre registrado.

### Banner de notificaciones

1. Revocar `POST_NOTIFICATIONS`.
2. Abrir Notificaciones.
3. Confirmar banner y pulsar `Abrir ajustes`.
4. Conceder permiso en Ajustes.
5. Volver y confirmar que el banner desaparece.

## Capturas recomendadas para evidencia formal

1. Login inválido con mensaje amigable.
2. Home paciente online.
3. Nota offline pendiente.
4. Home/Métricas con indicador offline.
5. Home después del relogin mostrando `Paciente anónimo`.
6. Banner de permiso denegado y pantalla de Ajustes.
7. Supervisor pendiente con acceso limitado.
8. Admin Home, supervisor autorizado y 403 tras revocación cuando existan cuentas de QA.

Los dumps UI de la ejecución están en `app/build/reports/rehabianex-ui-*.xml`; son artefactos generados y no deben versionarse.

## Decisión

- **Build candidato para continuar QA local:** sí.
- **Listo para commit técnico de checkpoint:** sí, condicionado a revisar los cambios fuente acumulados fuera de esta ejecución.
- **Listo para despliegue/release:** no. Faltan los flujos manuales admin, supervisor autorizado, vinculación/consentimiento/403 y Chat IA real.
