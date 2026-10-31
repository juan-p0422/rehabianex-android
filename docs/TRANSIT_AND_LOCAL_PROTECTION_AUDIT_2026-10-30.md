# RehabiAnex — protección en tránsito y almacenamiento local

Fecha de verificación: 2026-10-30 (America/Mexico_City)

Android: rama `codex/transit-local-protection`, base `03ff60a`, versión declarada `1.0` (`versionCode 1`)

Backend: rama `codex/closed-beta-backend-prep`, cambio de esta tarea `e092a98` (base previa `0f6b612`)

Entorno remoto comprobado: `https://rehabianex-api.onrender.com/api/`

## Alcance y clasificación

Esta revisión verificó primero la implementación existente. No se desplegó, no se usaron datos reales y no se modificaron esquemas ni contratos de API. Un UID se trata como identificador seudónimo, no como cifrado. HTTPS/TLS protege la conexión, pero el servidor, Firebase y el proveedor de IA autorizado pueden acceder al contenido que necesitan procesar; por tanto, RehabiAnex no debe describirse como cifrado de extremo a extremo.

| Dato o flujo | Protección existente comprobada | Brecha o límite | Cambio mínimo | Evidencia |
|---|---|---|---|---|
| Inicio de sesión, token Firebase y llamadas Android → Laravel | Retrofit/OkHttp con validación normal de certificado y hostname; API beta HTTPS; token en cabecera, no en URL | La configuración no impedía por sí misma una URL externa HTTP futura | `ApiClient` ahora exige HTTPS en release y solo permite HTTP de loopback explícito en debug; rechaza credenciales, query y fragmento en la URL base | `ApiTransportPolicyTest`: 2/2; `curl` HTTPS: 200 y certificado verificado; HTTP remoto redirige, pero Android además bloquea cleartext |
| Perfil, diario, agenda, contactos, consentimiento y chatbot Android → Laravel | Cuerpos HTTPS/TLS; sin `TrustManager`/`HostnameVerifier` permisivo; release sin logger HTTP | Laravel puede leer el contenido requerido; no es E2EE | Se conserva TLS, sin cifrado redundante del payload | Inspección de `ApiClient`, manifiesto y configuración de red |
| Identificadores y búsquedas en rutas/query | TLS cubre la URL completa; logger BASIC debug redacta valores de query e IDs dinámicos | El servidor ve UID/código/búsqueda; UID seudónimo no es cifrado | Sin cambio contractual; conservar minimización y autorización | `SanitizedHttpLogger` y pruebas existentes de logging seguro |
| Laravel → Firebase Auth/Firestore/FCM | SDK oficiales/Google y endpoints HTTPS; emulador HTTP limitado a loopback y proyecto demo | Firebase procesa los datos autorizados | Sin cambio | Auditoría backend y contratos FCM |
| Laravel → proveedor IA | URL configurada HTTPS, autorización en cabecera, contexto minimizado y seudonimizado | El proveedor procesa pregunta/contexto; retención/ZDR no verificados; no es E2EE | Backend rechaza HTTP externo y conserva HTTP solo para loopback en `local/testing` | `SupervisorAISecurityTest`; 15 casos de IA dentro de la corrida backend |
| Notificaciones FCM | SDK Firebase; payload data-only, opaco y sin campos sensibles permitidos | Entrega y metadatos dependen de Firebase/dispositivo | Sin cambio | `FcmPrivacyContractTest`: 19 casos dentro de la corrida backend |
| Room: perfil en caché, diario, agenda, contactos, consentimientos y colas offline | SQLCipher realmente conectado mediante `SupportOpenHelperFactory`; passphrase aleatoria | Una instalación histórica con DB realmente plaintext no tiene todavía migración conservadora en release | Sin borrar datos: release conserva el archivo y falla explícitamente; diseñar migración separada antes de actualizar ese caso | `EncryptedRoomDatabaseInstrumentedTest`: 3/3; cabecera no SQLite plaintext y persistencia comprobadas |
| Passphrase de SQLCipher | Sobre AES-GCM en `noBackupFilesDir`; clave AES no exportable en Android Keystore | Si se pierde/invalida la clave no se puede recuperar el contenido anterior | Se conserva fallo explícito; no se regenera silenciosamente sobre una DB existente | `DatabasePassphraseProvider` y pruebas de política existentes |
| Token, refresh token, UID, correo, rol, nombre y token FCM de sesión | AES-GCM con Android Keystore en `SecureSessionManager` | La protección depende del Keystore y seguridad del dispositivo | Sin cambio | Inspección de implementación y exclusión de backup |
| Nombre original y apodo locales | Antes se duplicaban en `SharedPreferences` en claro, aunque fuera de backup | **Brecha confirmada**: lectura local posible en dispositivo comprometido/depuración | Migración al leer hacia AES-256-GCM + Keystore, AAD por clave y prefijo UID SHA-256; elimina entrada legacy solo tras escritura confirmada | `PatientIdentityStoreInstrumentedTest`: 2/2; migración sin pérdida y corrupción/clave ausente no se reemplazan silenciosamente |
| Evidencia local de lectura del aviso | UID hasheado + versión; sin contenido del aviso | Faltaba exclusión explícita de backup/transferencia | Se añadió exclusión a reglas pre-31 y API 31+ | Revisión de `backup_rules.xml` y `data_extraction_rules.xml` |
| Preferencias de notificación/adaptación/deduplicación | Archivos privados y excluidos de backup; sin notas clínicas | Quedan UID/marcadores de baja sensibilidad en algunas preferencias sin cifrado de campo | No ampliar sin necesidad; evaluar si el modelo de amenaza exige cifrarlos | Inventario de `SharedPreferences` y reglas de backup |
| Exportaciones/temporales Android | La exportación de autoservicio ya no está activa en la UI/código actual | Archivos creados por versiones anteriores no se eliminan automáticamente | Sin borrado automático, por requisito de preservación | Inspección de manifiesto, rutas y módulo de control de datos |
| Logs Android y Laravel | Android release sin logger de red; debug solo BASIC sanitizado; backend registra clase/estado/operación, no cuerpos/tokens | El canal local backend activo no rota automáticamente aunque existe configuración diaria de 14 días | Pendiente operativo: configurar rotación/retención del entorno sin registrar datos sensibles | Inspección y búsqueda local sin mostrar secretos |
| Backups y materiales de prueba | DB, sesión y preferencias sensibles excluidas; capturas no forman parte del APK | En el árbol local backend existe un directorio temporal no versionado con archivos que aparentan contener material personal/de proyecto | No abrir, publicar ni incluir; limpieza segura requiere autorización del propietario | Inventario por nombres, sin leer ni eliminar contenido |
| Cifrado en reposo de servicios externos | Firebase documenta cifrado en tránsito y reposo | No se verificó garantía específica de almacenamiento en reposo de Render ni política de retención/ZDR del proveedor IA | No afirmar lo no comprobado; solicitar evidencia contractual del proveedor si es requisito de beta | Revisión coordinada backend |

## Cambios realizados

- `ApiClient.kt`: política de transporte HTTPS para builds distribuibles y excepción de loopback solo en debug.
- `ApiTransportPolicyTest.kt`: cobertura de HTTPS, credenciales/query y loopback.
- `PatientIdentityStore.kt`: cifrado y migración conservadora de nombre/apodo con AES-GCM y Android Keystore.
- `PatientIdentityStoreInstrumentedTest.kt`: migración y fallo seguro ante ciphertext inválido.
- `backup_rules.xml` y `data_extraction_rules.xml`: exclusión de evidencia local de lectura del aviso.
- Backend coordinado: `AIService.php` y `SupervisorAISecurityTest.php` rechazan transporte HTTP externo del proveedor IA.

No cambió ningún endpoint, DTO, esquema de base ni contrato de respuestas.

## Pruebas ejecutadas

- Emulador `Copy_of_Pixel_4`, Android 13 / API 33:
  - `EncryptedRoomDatabaseInstrumentedTest`: 3/3 aprobadas, 0 fallos.
  - `PatientIdentityStoreInstrumentedTest`: 2/2 aprobadas, 0 fallos.
- JVM desde Android Studio:
  - `ApiTransportPolicyTest`: 2/2 aprobadas, 0 fallos.
- Backend PHP:
  - `SupervisorAISecurityTest`, `FcmPrivacyContractTest`, `HealthEndpointSecurityTest`: 36 pruebas, 161 aserciones, 0 fallos.
- `git diff --check`: sin errores en Android ni backend.
- La ejecución Gradle directa desde PowerShell no pudo arrancar por `Unable to establish loopback connection`; no se contabiliza como fallo funcional de la app, pero queda como limitación del runner local.

No se ejecutó en esta revisión una actualización física desde una instalación antigua realmente plaintext ni se generó/distribuyó un APK. La comprobación cubrió la configuración fuente de release y pruebas debug enfocadas; antes de beta debe probarse el artefacto release firmado exacto.

## Pendientes y riesgos

1. Diseñar y probar, con una copia sintética, una migración plaintext → SQLCipher antes de actualizar cualquier instalación histórica que realmente use Room sin cifrar. No borrar ni recrear esa DB en release.
2. Verificar el APK release firmado exacto: manifiesto fusionado, `debuggable=false`, URL HTTPS, instalación/actualización y pruebas físicas.
3. Confirmar y aplicar retención/rotación de logs en Render sin incorporar cuerpos, prompts, tokens o PII.
4. Obtener evidencia contractual si se desea afirmar cifrado en reposo de Render y retención/ZDR del proveedor IA.
5. Revisar y limpiar, con autorización, temporales locales no versionados del backend; nunca empaquetarlos ni publicarlos.
6. Valorar si los marcadores/UID de baja sensibilidad restantes en preferencias requieren cifrado adicional según el modelo de amenaza de la beta.

## Texto verificable para el aviso

> RehabiAnex protege la información durante su envío mediante conexiones HTTPS/TLS. En el dispositivo, los registros y las colas offline almacenados en la base local se cifran con SQLCipher; las claves de esa base, la sesión y determinados datos locales se protegen con AES-GCM y Android Keystore. Estas medidas no constituyen cifrado de extremo a extremo: el servidor y los proveedores autorizados, como Firebase y el proveedor de inteligencia artificial cuando se utiliza esa función, pueden acceder al contenido necesario para prestar el servicio. Ninguna medida elimina por completo los riesgos de seguridad ni sustituye la protección del dispositivo y de la cuenta.

## Reversión

Los cambios Android pueden revertirse por archivo sin tocar datos ni esquemas. Si se revierte `PatientIdentityStore`, las entradas ya migradas quedarán cifradas y una versión antigua no podrá leerlas; por ello, la reversión funcional debe conservar el lector cifrado o incluir una migración explícita y probada, nunca borrar preferencias. El endurecimiento del transporte backend puede revertirse en `AIService.php` y su prueba, aunque no se recomienda volver a permitir HTTP externo.
