# Política Android de nombre seguro y modo anónimo

Última actualización: 2026-08-04.

## Regla obligatoria

Android considera restringida la identidad de un paciente cuando
`is_anonymous == true` o `privacy_mode == true`.

| Estado | Prioridad para UI y sesión |
|---|---|
| Normal | `safe_display_name`, `display_name`, `nickname`, `full_name`, fallback de sesión. |
| Restringido | `safe_display_name`, `display_name`, `nickname`, `Paciente anónimo`. |

En estado restringido, `full_name` y el nombre previo de sesión no participan en
la resolución. La regla vive en `PatientDisplayNamePolicy.kt` y la consumen
autenticación, Perfil, Home y mappers Room.

## Contrato mapeado

`AuthData` y `SessionProfileDto` soportan:

- `display_name`;
- `safe_display_name`;
- `full_name`;
- `is_anonymous`;
- `privacy_mode`.

Los campos extra continúan siendo ignorables por Gson. `full_name` se conserva
en el DTO para edición autorizada, pero no se usa como nombre visible mientras
alguno de los flags privados esté activo.

## Persistencia y purga

Al activar anonimato desde Perfil:

1. Se limpia por UID `PatientIdentityStore.originalName` y el nickname local.
2. El snapshot `PatientProfileEntity` se reemplaza con `fullName = null` y nombre
   seguro.
3. `PatientSummaryEntity.fullName` se conserva como columna legada, pero se
   reemplaza exclusivamente con el nombre seguro; nunca almacena el nombre real
   privado.
4. La sesión se vuelve a guardar con el nombre seguro, manteniendo `id_token`,
   `refresh_token`, UID, correo y rol sin cambios.
5. El ViewModel ya existente toma el snapshot reemplazado y después refresca
   desde API, por lo que el nombre real deja de permanecer en el estado visible.

Al iniciar sesión con una proyección privada, `AuthRepository` ejecuta la purga
del mismo UID antes de guardar la sesión nueva. Esto impide que Home muestre por
un instante un snapshot identificable de una sesión anterior.

Para `privacy_mode=true` con nickname permitido se elimina `originalName`, pero
se conserva el nickname seguro. Para `is_anonymous=true` se limpia toda la
identidad local del paciente.

## Home y Perfil

- Home usa `resolvedPatientDisplayName`; si el perfil está restringido no divide
  ni sustituye el resultado con `full_name`. El saludo anónimo es
  `Hola, Paciente anónimo`.
- Perfil usa la misma resolución para encabezado, iniciales y snapshot.
- Si backend antiguo omite los alias seguros pero informa alguno de los flags,
  el fallback es `Paciente anónimo`, nunca `full_name`.

## Pruebas unitarias

Se añadieron o actualizaron casos para:

- login normal prioriza y guarda `safe_display_name`;
- login anónimo ignora `full_name` real y solicita purga local;
- logout/login simulado conserva la proyección anónima;
- `privacy_mode` usa nickname permitido;
- ausencia de alias seguros usa `Paciente anónimo`;
- activar anonimato limpia `PatientIdentityStore` y reemplaza el nombre de
  sesión sin cambiar tokens;
- mappers Room eliminan `fullName` en snapshots privados.

Comando ejecutado:

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

Resultado: `BUILD SUCCESSFUL` en 25 s; 132 pruebas, 0 fallos, 0 errores y 0
omitidas. El APK debug se generó correctamente.

## Riesgos restantes

- `PatientSummaryEntity.fullName` mantiene el nombre histórico de la columna por
  compatibilidad con la base instalada. Su semántica actual es “nombre seguro”.
  Renombrarla exige una migración Room y no es necesario para cerrar la fuga.
- Conviene repetir en dispositivo el recorrido activar anonimato, logout, login
  y Home contra el backend corregido antes de release.
