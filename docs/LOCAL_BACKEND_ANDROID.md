# Acople de Android con backend local (configuración desactivada)

> Estado desde el 6 de agosto de 2026: esta guía se conserva como referencia histórica. El build actual ignora `DEBUG_API_BASE_URL` y todas las variantes consumen exclusivamente `https://rehabianex-api.onrender.com/api/`. Para volver a localhost se deberá restaurar explícitamente la separación de debug y su configuración de seguridad de red.

Fecha de actualización: 1 de agosto de 2026  
Alcance: variante `debug`; no autoriza staging, Render ni producción.

## Configuración vigente

- El emulador Android usa por defecto `http://10.0.2.2:8000/api/`.
- Un dispositivo físico debe compilarse con
  `-PDEBUG_API_BASE_URL=http://IP_LOCAL_PC:8000/api/`.
- La variante `debug` permite HTTP para desarrollo local mediante un recurso
  `network_security_config.xml` exclusivo de `src/debug`.
- La configuración de `src/main` bloquea HTTP. Por tanto, `release` no hereda
  la excepción local de debug.
- Este cambio no valida ni autoriza la URL release existente. No se debe
  compilar ni distribuir release hasta revisar su endpoint por separado.

## Requisitos del backend

Para el emulador, el backend debe estar disponible en el puerto `8000` del PC.
Para un dispositivo físico, además debe:

1. Escuchar en `0.0.0.0:8000`.
2. Permitir el puerto `8000` en el firewall de la red privada.
3. Estar en la misma red LAN que el dispositivo.
4. Mantener el prefijo `/api/` en la URL configurada.

CORS no interviene en Retrofit/OkHttp porque Android nativo no ejecuta estas
peticiones dentro de un navegador.

## Comandos

### Emulador Android Studio

El valor predeterminado de debug es suficiente:

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Para declararlo de forma explícita:

```powershell
.\gradlew.bat :app:assembleDebug "-PDEBUG_API_BASE_URL=http://10.0.2.2:8000/api/" --no-daemon --console=plain
```

### Dispositivo físico

```powershell
.\gradlew.bat :app:assembleDebug "-PDEBUG_API_BASE_URL=http://IP_LOCAL_PC:8000/api/" --no-daemon --console=plain
```

No se debe guardar la IP local en el repositorio. Debe sustituirse
`IP_LOCAL_PC` solo en el comando local de compilación.

## Verificación

1. Confirmar que el backend responde desde el PC.
2. Compilar `debug` y revisar el `BuildConfig` generado.
3. Instalar el APK debug en emulador o dispositivo.
4. Probar registro, login, `auth/me`, registro emocional y configuración de
   notificaciones con cuentas desechables.
5. Confirmar en Logcat que no se imprimen tokens ni cuerpos sensibles.
6. Si falla la conexión, clasificarla como URL, firewall, cleartext, backend
   caído o incompatibilidad de contrato. CORS no es una causa aplicable.

## Historial del cambio

- Se retiró de `src/main` la excepción HTTP para `10.0.2.2`.
- Se agregó en `src/debug` una política que permite HTTP local para emulador
  e IP LAN.
- No se modificó la configuración de URL de release.

## Evidencia de verificación

Verificación realizada el 1 de agosto de 2026 con backend local escuchando en
`0.0.0.0:8000` y un emulador Android activo:

- `:app:assembleDebug`: `BUILD SUCCESSFUL`.
- `BuildConfig.API_BASE_URL` debug: `http://10.0.2.2:8000/api/`.
- Registro desde la app: HTTP 201.
- Login desde la app: HTTP 200.
- `GET /auth/me` desde la app: HTTP 200.
- Registro emocional desde la app: HTTP 201.
- Lectura de `notification-settings`: HTTP 200.
- Escritura de `notification-settings`: HTTP 201.
- Acceso al servidor por la IP LAN del PC confirmado; `/api/` respondió 404
  porque no existe una ruta raíz, no por firewall ni cleartext.

Se utilizaron cuentas paciente desechables exclusivamente en el backend local.
No se documentaron correos, contraseñas, tokens ni UID. No se ejecutó ninguna
tarea release y no se realizó push.

## Auditoría de compatibilidad posterior a seguridad backend

Verificación realizada el 3 de agosto de 2026 contra el backend local:

- Login válido desde Android: HTTP 200.
- Login con credenciales inválidas: HTTP 401 y mensaje amigable en la UI.
- `GET /auth/me` desde Android: HTTP 200.
- Endpoint inexistente: HTTP 404 con el sobre JSON
  `{ "ok": false, "message": "...", "errors": {} }`.
- El login inválido también usa el mismo sobre JSON.
- `ApiErrorParser` conserva mensajes seguros y solo convierte `errors` en
  errores por campo para respuestas 422.
- `ErrorMapper` conserva los fallbacks amigables para 400, 401, 403, 404, 409,
  422, 429, 500 y 502, además de timeout y falta de conexión.
- No existe consumo ni dependencia Android de `/api/ping`.

No fue necesario modificar código Android para esta compatibilidad. La única
modificación de esta auditoría fue agregar esta evidencia documental.
