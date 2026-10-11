# RehabiAnex Android - Architecture audit

> Actualización: la estrategia incremental Room/offline-first está documentada
> en `docs/OFFLINE_FIRST_ANDROID.md`.

Fecha: 2026-07-07

Este diagnóstico describe el estado actual del proyecto Android después de la
fase conservadora de auditoría, centralización de networking y consolidación de
sesión. No se rediseñaron pantallas ni se agregaron nuevas funcionalidades de
paciente/supervisor.

## 1. Mapa de archivos principales

### Raíz de aplicación

- `MainActivity.kt`: punto de entrada Android. Inicializa `AppContainer` y monta
  `RehabianexNavHost`.
- `AppContainer.kt`: contenedor simple de dependencias. Crea `SessionManager`,
  `ApiService`, `AuthRepository` y `PatientRepository`.

### Core

- `core/network/ApiClient.kt`: punto central para construir Retrofit y
  OkHttpClient.
- `core/network/AuthInterceptor.kt`: agrega headers generales y token Bearer si
  existe sesión.
- `core/error/ErrorMapper.kt`: traduce errores de red/HTTP a mensajes
  amigables.
- `core/result/AppResult.kt`: resultado común para operaciones futuras.
- `core/ui/UiState.kt`: estado común para pantallas futuras.
- `core/session/SessionManager.kt`: contrato central de sesión.
- `core/session/UserSession.kt`: modelo central de sesión de usuario.

### Data

- `data/remote/ApiService.kt`: interfaz Retrofit con endpoints actuales de auth,
  paciente, notas, agenda, contactos, logros, solicitudes y consentimientos.
- `data/remote/ApiErrorParser.kt`: parser actual de errores JSON enviados por la
  API Laravel.
- `data/model/AuthModels.kt`: requests/responses de autenticación y sesión de
  auth.
- `data/model/PatientModels.kt`: DTOs y modelos usados por pantallas de paciente.
- `data/repository/AuthRepository.kt`: login/registro y guardado de sesión.
- `data/repository/PatientRepository.kt`: carga y mutación de datos del flujo
  paciente conectado a API.

### Session

- `session/SecureSessionManager.kt`: implementación cifrada con Android Keystore
  del contrato `core/session/SessionManager`.

### Navigation

- `navigation/Routes.kt`: nombres de rutas públicas.
- `navigation/RehabianexNavHost.kt`: NavHost principal; decide start destination
  por sesión y rol.
- `navigation/PatientNavGraph.kt`: rutas de paciente.
- `navigation/SupervisorNavGraph.kt`: rutas de supervisor.
- `navigation/NavigationExtensions.kt`: helpers para navegación singleTop y
  retorno a login limpiando back stack.

### UI/components

- `ui/components/AppLogo.kt`: logos locales.
- `ui/components/Buttons.kt`: botones reutilizables.
- `ui/components/Cards.kt`: cards base, warning, danger, blue, IA.
- `ui/components/Chips.kt`: chips simples y multiselección.
- `ui/components/Inputs.kt`: inputs y text area.
- `ui/components/Layout.kt`: scaffold/base layout.
- `ui/components/Metrics.kt`: tarjetas y visualizaciones simples.
- `ui/components/RoleScaffolds.kt`: scaffolds con bottom bar por rol.
- `ui/components/ScreenElements.kt`: elementos compartidos de pantalla.
- `ui/components/BottomBars.kt`: navegación inferior de paciente/supervisor.

### Auth screens

- `ui/screens/auth/SplashScreen.kt`
- `ui/screens/auth/LoginScreen.kt`
- `ui/screens/auth/RegisterScreen.kt`
- `ui/screens/auth/ConsentScreen.kt`
- `ui/screens/auth/RoleSelectionScreen.kt`
- `ui/screens/auth/AuthViewModel.kt`

### Patient screens

- `ui/screens/patient/PatientDataViewModels.kt`: agrupa ViewModels y estados de
  varias pantallas del paciente.
- Pantallas: home, registro emocional, métricas, logros, intervenciones, plan,
  red de apoyo, agenda, nuevo evento, directorio local, vinculación, notificaciones
  y perfil.

### Supervisor screens

- `SupervisorHomeScreen.kt`
- `PatientsListScreen.kt`
- `PatientDetailScreen.kt`
- `AiChatScreen.kt`
- `SupervisorProfileScreen.kt`

Actualmente son mayormente visuales/mock; el módulo supervisor real debe
conectarse en una fase posterior.

### Validation

- `validation/AuthValidator.kt`: validaciones de login/registro.

## 2. Responsabilidad actual de cada bloque

### Auth

Gestiona login, registro, validación de formularios, lectura de respuesta de la
API, creación de sesión local y navegación por rol.

### Patient

Combina UI Compose, ViewModels con `StateFlow`, repositorio de datos reales y
algunas pantallas todavía visuales. Las pantallas principales conectadas a datos
reales son inicio, registro emocional, métricas, logros, red de apoyo, agenda,
perfil y vinculación con supervisor.

### Supervisor

Pantallas visuales preparadas para el flujo supervisor. Falta repositorio,
ViewModels y llamadas reales para solicitudes, pacientes autorizados, detalle,
intervenciones y chat IA.

### Networking

Retrofit y OkHttp ahora se crean desde `core/network/ApiClient.kt`.
`AuthInterceptor` central agrega:

- `Accept: application/json`
- `Content-Type: application/json` cuando la request tiene body
- `Authorization: Bearer <token>` cuando existe token y no es endpoint público de
  auth

### Session

`SessionManager` define el contrato común. `SecureSessionManager` mantiene datos
cifrados con Android Keystore:

- id token
- refresh token
- uid
- email
- role
- display name

### UI/components

Contiene componentes visuales reutilizables. La UI existente no fue rediseñada.

### Navigation

Mantiene rutas existentes. La navegación inicial usa sesión activa y rol:

- `patient` -> `patient_home`
- `supervisor` -> `supervisor_home`
- sin sesión -> `splash`

### Models

Los modelos actuales mezclan DTOs de red y algunos modelos de UI simples. Esto es
aceptable para el tamaño actual, pero debe separarse gradualmente si crece el
dominio.

## 3. Deuda técnica detectada

1. `PatientDataViewModels.kt` agrupa demasiados ViewModels y estados en un solo
   archivo. Es entendible para prototipo, pero conviene dividirlo por pantalla.
2. Hay estados de UI duplicados:
   - `AuthUiState`
   - `PatientHomeUiState`
   - `MetricsUiState`
   - `AchievementsUiState`
   - `SupportContactsUiState`
   - `AgendaEventsUiState`
   - `PatientProfileUiState`
   - `SupervisorLinkUiState`
3. Ya existe `core/ui/UiState.kt`, pero todavía no se migraron pantallas para
   evitar refactor masivo.
4. Hay resultados duplicados:
   - `AuthResult`
   - `PatientDataResult`
   - `core/result/AppResult`
5. `PatientRepository` concentra bastante lógica:
   - llamadas de red;
   - refresh de sesión en 401;
   - cálculo de métricas;
   - cálculo de días sobrios;
   - mapeo DTO -> UI;
   - validaciones/sanitización.
6. El refresh automático por 401 está en `PatientRepository`, no aún en una capa
   global de networking. Moverlo requiere cuidado para no alterar auth/login.
7. `ApiService` concentra endpoints de varias áreas. Es útil ahora, pero a futuro
   conviene dividirlo en `AuthApi`, `PatientApi`, `SupervisorApi`, etc.
8. Hay pantallas supervisor con datos mock/visuales. Deben conectarse cuando se
   implemente el módulo supervisor real.
9. Algunos valores visibles siguen hardcoded porque pertenecen a pantallas aún no
   conectadas.
10. Algunos mensajes de error siguen definidos dentro de repositorios aunque ya
    existe `ErrorMapper`.

## 4. Duplicaciones detectadas

### Endpoints

No se detectaron clientes Retrofit duplicados. Sí existe un solo `ApiService`
grande.

### Clientes Retrofit

Antes Retrofit/OkHttp se configuraban dentro de `AppContainer`. Ahora el punto
central es `core/network/ApiClient.kt`.

### Data classes equivalentes

- `AuthSession` y `UserSession` se parecen, pero tienen responsabilidades
  distintas:
  - `AuthSession` mantiene compatibilidad con el flujo auth actual.
  - `UserSession` es el modelo central para almacenamiento/navegación.

### Validaciones

- Auth tiene `AuthValidator`.
- Registro emocional valida dentro de `EmotionalCheckinViewModel`.
- Vinculación limpia el UID en UI y vuelve a validar en repositorio.

Conviene crear validadores por feature más adelante.

### Manejo de sesión

Se consolidó con `SessionManager`. La implementación real sigue en
`SecureSessionManager`.

### Estados de pantalla

Hay duplicación clara. `UiState<T>` quedó listo para migración gradual.

## 5. Propuesta de estructura futura

No se recomienda mover archivos masivamente todavía. Propuesta incremental:

```text
app/src/main/java/com/example/rehabianex/
  core/
    network/
      ApiClient.kt
      AuthInterceptor.kt
    session/
      SessionManager.kt
      UserSession.kt
    result/
      AppResult.kt
    error/
      ErrorMapper.kt
    ui/
      UiState.kt
  data/
    remote/
      ApiService.kt
      ApiErrorParser.kt
    dto/
    repository/
      AuthRepository.kt
      PatientRepository.kt
      SupervisorRepository.kt
  domain/
    model/
    repository/
    usecase/
  presentation/
    auth/
    patient/
    supervisor/
    chat/
    navigation/
  ui/
    components/
    theme/
  session/
    SecureSessionManager.kt
```

Siguiente refactor seguro sugerido:

1. Dividir `PatientDataViewModels.kt` por pantalla.
2. Migrar gradualmente estados de paciente a `UiState<T>`.
3. Dividir `ApiService` por áreas solo cuando se conecte supervisor.
4. Extraer mappers de `PatientRepository`.
5. Llevar refresh 401 a una capa central cuando existan pruebas suficientes.

## 6. Cambios aplicados en estas fases

- Se agregó `core/network/ApiClient.kt`.
- Se movió el interceptor de auth a `core/network/AuthInterceptor.kt`.
- Se agregó `core/result/AppResult.kt`.
- Se agregó `core/ui/UiState.kt`.
- Se agregó `core/error/ErrorMapper.kt`.
- Se agregó `core/session/SessionManager.kt`.
- Se agregó `core/session/UserSession.kt`.
- `SecureSessionManager` ahora implementa `SessionManager` y guarda también email
  y display name.
- `AppContainer` usa `ApiClient` como único punto de creación Retrofit/OkHttp.
- `AuthRepository`, `PatientRepository` y `RehabianexNavHost` dependen del
  contrato `SessionManager`, no de la implementación concreta.
- Se agregó `okhttp-logging-interceptor` para logging HTTP solo en debug.

## 7. Pendientes recomendados

1. Migrar ViewModels a `UiState<T>` de forma gradual.
2. Migrar `AuthResult` y `PatientDataResult` a `AppResult` cuando no rompa UI.
3. Crear repositorios por dominio:
   - `SupportContactsRepository`
   - `AgendaRepository`
   - `MetricsRepository`
   - `SupervisorRepository`
4. Conectar módulo supervisor real:
   - solicitudes pendientes;
   - aceptar/rechazar solicitudes;
   - pacientes autorizados;
   - detalle de paciente.
5. Integrar chatbot IA en fase separada.
6. Centralizar refresh 401 fuera de `PatientRepository`.
7. Agregar pruebas unitarias para:
   - `SessionManager`;
   - `ErrorMapper`;
   - validadores;
   - repositorios.
8. Revisar hardcoded values de pantallas supervisor visuales.

## 8. Validación

Comandos requeridos:

```powershell
./gradlew.bat :app:assembleDebug --no-daemon --console=plain
./gradlew.bat :app:testDebugUnitTest :app:lintDebug --no-daemon --console=plain
```

`assembleDebug` fue ejecutado durante esta fase y compiló correctamente.
