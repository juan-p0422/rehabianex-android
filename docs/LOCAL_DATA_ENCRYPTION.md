# Cifrado local de Android

Última actualización: 2026-08-03.

## Decisión

Room se cifra en reposo con `net.zetetic:sqlcipher-android:4.16.0` y
`SupportOpenHelperFactory`. La versión seleccionada está publicada en Maven
Central, soporta la integración `androidx.sqlite` usada por Room 2.8.4 y el
proyecto cumple su mínimo de Android API 23 porque RehabiAnex usa API 27.

Referencias técnicas:

- https://github.com/sqlcipher/sqlcipher-android
- https://central.sonatype.com/artifact/net.zetetic/sqlcipher-android
- https://developer.android.com/identity/data/autobackup

## Protección de clave

1. `DatabasePassphraseProvider` genera 32 bytes aleatorios con `SecureRandom`.
2. Genera o recupera una clave AES-256 no exportable bajo el alias
   `rehabianex_room_wrap_key_v1` en `AndroidKeyStore`.
3. La passphrase SQLCipher se cifra con `AES/GCM/NoPadding`, IV aleatorio y AAD
   versionado.
4. Solo se persiste el sobre autenticado en
   `noBackupFilesDir/rehabianex_room_key_v1.enc`, ubicación excluida por Android
   de backups y transferencias.
5. No existe clave hardcodeada, no se convierte a `String` y no se registra en
   Logcat.
6. Si el sobre falta o no autentica cuando ya existe una base cifrada, la app no
   genera una clave distinta: falla de forma cerrada para evitar pérdida opaca.

La passphrase descifrada existe temporalmente en memoria porque SQLCipher la
necesita para abrir la conexión. No se almacena en preferencias ni archivos en
claro.

## Base previa sin cifrar

La base offline todavía corresponde a una fase local/debug, no a una publicación
productiva validada. La política es explícita:

- Debug: si la cabecera es `SQLite format 3`, se eliminan la base y sus archivos
  `-wal`, `-shm` y `-journal`; Room crea una base SQLCipher nueva. Se pierden los
  datos offline de pruebas, incluidos pendientes.
- Release: una base con cabecera SQLite plana se rechaza. No se abre y no se
  borra silenciosamente.
- Una futura actualización productiva que deba conservar datos deberá convertir
  la base fuera de Room mediante `sqlcipher_export`, verificar integridad y
  reemplazar archivos de manera atómica antes de activar el factory cifrado.
- Las migraciones de esquema Room 1→2 y 2→3 siguen disponibles para bases
  que ya estén cifradas y tengan una clave válida. La segunda agrega el snapshot
  seguro de perfil y descarta resúmenes v2 cuyo permiso de privacidad no puede
  demostrarse.

## Backup y preferencias

`backup_rules.xml` y `data_extraction_rules.xml` excluyen de nube y transferencia:

- `rehabianex-offline.db`, `-wal`, `-shm` y `-journal`;
- `secure_session.xml`;
- `patient_identity.xml`;
- `supervisor_notification_settings.xml`;
- `rehabianex_notifications.xml`.

La clave envuelta está en `noBackupFilesDir`, que Android excluye siempre. Las
preferencias `patient_identity` y de notificaciones aún no tienen cifrado por
campo; su mitigación actual es almacenamiento privado, exclusión de backup y
purga por UID en logout/cambio de cuenta.

## Pruebas

- `DatabaseSecurityPolicyTest`: formato de sobre, rechazo de datos dañados y
  política debug/release para SQLite plano.
- `EncryptedRoomDatabaseInstrumentedTest`: abre y reabre Room cifrado, confirma
  que el archivo no expone la cabecera SQLite, reemplaza una base plana de debug
  y verifica purga en logout/cambio de usuario.
- Suite JVM: 121 pruebas, 0 fallos.
- Suite instrumentada en emulador API 33: 7 pruebas, 0 fallos.
- `testDebugUnitTest + assembleDebug`: `BUILD SUCCESSFUL` en 40 s.
- `connectedDebugAndroidTest`: `BUILD SUCCESSFUL` en 29 s.

## Riesgos pendientes

- No existe conversión productiva con conservación de datos desde SQLite plano.
- La clave Keystore no requiere autenticación biométrica o bloqueo de pantalla;
  protege contra extracción de archivos, no contra un proceso comprometido que
  ejecute dentro del contexto desbloqueado de la app.
- Las preferencias identificables mencionadas siguen sin cifrado individual.
- Debe repetirse la prueba en dispositivo físico y en las ABI soportadas antes de
  un release.
