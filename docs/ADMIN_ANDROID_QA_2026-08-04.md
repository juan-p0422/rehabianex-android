# QA Android — acceso y panel administrativo

Fecha: 2026-08-04

## Objetivo

Validar en Android debug que una identidad administrativa creada exclusivamente por backend:

- inicia sesión sin credenciales embebidas en la app;
- conserva el rol `admin` devuelto por backend;
- navega al panel administrativo y no a paciente o supervisor;
- consulta las listas de supervisores;
- cierra sesión desde el menú de opciones;
- no deja una sesión o identidad sensible visible después del logout.

## Política de prueba

- Se utilizó una cuenta sintética y efímera creada con `rehabianex:create-admin` contra Firebase Emulator.
- Correo y contraseña se generaron durante la ejecución y se conservaron únicamente en memoria del proceso de QA.
- No se escribieron credenciales en código, documentación, propiedades, logs ni archivos del repositorio.
- Antes del flujo se limpió únicamente el almacenamiento de la aplicación debug instalada en el emulador.
- No se utilizó Render ni una cuenta administrativa real.

## Cambios Android

### Navegación y experiencia admin

- La pantalla principal de administración ahora presenta una barra inferior con:
  - Pendientes.
  - Activos.
  - Suspendidos.
- Cada opción actualiza el filtro y vuelve a consultar `GET /admin/supervisors?status=...`.
- Se eliminó el botón de logout expuesto al final del contenido.
- `Cerrar sesión` quedó dentro del menú de tres puntos, siguiendo el patrón visual de las demás cuentas.
- Las rutas de paciente y supervisor no fueron modificadas.

### Protección por variante

- `BuildConfig.ADMIN_PANEL_ENABLED=true` en debug.
- `BuildConfig.ADMIN_PANEL_ENABLED=false` en release.
- En release, una sesión con rol admin llega a una pantalla segura que informa que la administración no está disponible y permite cerrar sesión desde el menú de opciones.
- El flag controla publicación/experiencia, pero no sustituye las validaciones de rol y estado del backend.

## Resultado manual

| Caso | Resultado | Evidencia |
|---|---|---|
| Login con cuenta admin creada por backend | Pasa | `POST /auth/login` respondió correctamente con rol `admin`. |
| Navegación por rol | Pasa | Android abrió directamente “Panel de administración”; no mostró Home paciente, Home supervisor ni selector de rol. |
| Carga inicial del panel | Pasa | Se mostró “Cuenta administradora activa” y la sección de pendientes. |
| Consulta de supervisores pendientes | Pasa | La API respondió correctamente; el conjunto aislado no contenía pendientes. |
| Consulta de supervisores activos | Pasa | La barra inferior cambió a Activos y la API respondió correctamente. |
| Consulta de supervisores suspendidos | Pasa | La barra inferior cambió a Suspendidos y la API respondió correctamente. |
| Logout oculto | Pasa | “Cerrar sesión” no era visible en el contenido; apareció solo al abrir Opciones. |
| Logout remoto y navegación | Pasa | Se observó `POST /auth/logout` y Android volvió a Login. |
| Caché sensible después del logout | Pasa | Panel no visible, cero entradas en sesión segura, cero entradas en identidad de paciente y cero trabajos de usuario programados. |

Pantallas verificadas:

1. Splash.
2. Login.
3. Panel admin — Pendientes.
4. Panel admin — Activos.
5. Panel admin — Suspendidos.
6. Menú de tres puntos con logout.
7. Login posterior al logout.

## Errores y limitaciones encontradas

### Entorno local desalineado al iniciar QA

La primera llamada de login devolvió 401 aunque Firebase Auth Emulator aceptaba la identidad. La causa fue un proceso Laravel anterior que no estaba conectado al emulador Auth vigente. Se reinició únicamente el servidor local con `FIREBASE_AUTH_EMULATOR_HOST` y el proyecto `demo-`; después, el mismo flujo pasó.

No fue un error del contrato ni de Android. Para evitar falsos negativos debe iniciarse Auth/Firestore Emulator antes del servidor Laravel y comprobar un login API local antes de probar la UI.

### Dataset administrativo vacío

El entorno aislado contenía solo la cuenta admin, por lo que las tres consultas devolvieron listas vacías válidas. Quedaron comprobados navegación, filtro, autorización Bearer, respuesta y estado vacío; no se repitieron en este ciclo acciones sobre una tarjeta real de supervisor.

## Compilación y pruebas

Comandos ejecutados:

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:compileReleaseKotlin --no-daemon --console=plain
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain
```

Resultados:

- Debug y release Kotlin: `BUILD SUCCESSFUL`.
- Pruebas JVM: 124 ejecutadas, 0 fallos, 0 errores y 0 omitidas.
- Lint debug: 0 errores y 34 advertencias existentes.
- APK debug: generado correctamente.
- BuildConfig verificado: admin habilitado en debug y deshabilitado en release.

## Recomendaciones de seguridad

1. Mantener `ADMIN_PANEL_ENABLED=false` en release hasta una aprobación explícita de producto y seguridad.
2. No distribuir builds debug: el flag es una barrera de publicación, no una autorización de seguridad.
3. Mantener todas las rutas `/admin/*` protegidas por token, claim `role=admin` y perfil admin activo en backend.
4. No habilitar registro público de administradores; continuar usando el comando seguro del backend.
5. Crear un flavor `staging` separado si se necesita probar administración fuera de debug, con URL y firma propias.
6. Evitar datos de pacientes en el panel. La pantalla actual solo muestra metadatos administrativos mínimos de supervisores.
7. Considerar MFA, sesiones administrativas más cortas y reautenticación antes de suspender/reactivar cuentas.
8. Conservar logs sanitizados: nunca registrar tokens, credenciales ni respuestas administrativas completas.

## Archivos modificados

- `app/build.gradle.kts`.
- `app/src/main/java/com/example/rehabianex/navigation/RehabianexNavHost.kt`.
- `app/src/main/java/com/example/rehabianex/ui/screens/admin/AdminHomeScreen.kt`.
- `docs/ADMIN_ANDROID_QA_2026-08-04.md`.
