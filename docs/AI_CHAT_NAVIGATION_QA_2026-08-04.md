# Corrección y QA de navegación del Chat IA

Fecha: 2026-08-04  
Entorno: APK debug, emulador Android API 33, backend y Firebase locales.

## Alcance implementado

- `AiChatScreen` muestra un botón Atrás explícito y captura el botón Atrás del
  sistema mediante el mismo callback.
- La cabecera permite abrir Perfil supervisor.
- La parte inferior conserva el campo de pregunta y agrega la barra compartida
  de supervisor: Inicio, Pacientes y Chat.
- `onNavigate` se usa para Perfil y para todos los destinos inferiores.
- El grafo intenta `popBackStack()` al volver; si no existe destino previo,
  navega a Inicio supervisor.
- La barra compartida conserva el modo de acceso limitado: un supervisor no
  autorizado no recibe accesos nuevos a Pacientes o Chat.

No se modificaron `SupervisorChatRequest`, `ApiService`, el repositorio, el
ViewModel, la elegibilidad ni el endpoint `POST /ai/supervisor-chat`.

## Archivos

| Archivo | Cambio |
|---|---|
| `ui/screens/supervisor/AiChatScreen.kt` | Atrás, Perfil y barra inferior compartida. |
| `navigation/SupervisorNavGraph.kt` | Callback de retorno con fallback a Inicio. |
| `ui/components/RoleScaffolds.kt` | Barra inferior de supervisor reutilizable y lista de rutas verificable. |
| `test/.../SupervisorChatNavigationTest.kt` | Pruebas de rutas completas y acceso limitado. |
| `docs/AI_CHAT_ANDROID_ARCHITECTURE.md` | Decisión de navegación documentada. |

`BottomBars.kt` y las rutas existentes fueron auditados, pero no requirieron
cambios.

## Prueba manual

Se utilizaron cuentas y datos sintéticos creados en los emuladores locales. No
se guardaron credenciales en el repositorio ni en esta documentación.

| Caso | Resultado |
|---|---|
| Login de supervisor autorizado | PASS; abrió Inicio supervisor. |
| Abrir Chat desde barra inferior | PASS; se mostraron Atrás, Perfil y la barra inferior. |
| Enviar pregunta en modo `local` | PASS; backend respondió en modo `local`. |
| Aviso no clínico visible | PASS. |
| Volver con Atrás | PASS; regresó al flujo del supervisor y no cerró la actividad. |
| Ir a Inicio | PASS. |
| Ir a Pacientes | PASS. |
| Entrar nuevamente a Chat | PASS. |
| Abrir Perfil desde Chat | PASS. |
| Cerrar sesión desde Perfil | PASS; volvió a Login. |
| Tokens después de logout | PASS; no se encontraron claves `id_token` ni `refresh_token`. |
| Pregunta de prueba en Logcat | PASS; cero coincidencias. |
| Persistencia IA en Room | PASS; no existen entidades, DAOs ni referencias Room del Chat. |

Hallazgo fuera del alcance de esta corrección: en el conjunto sintético, Inicio
y Pacientes proyectaron al paciente como `Sin acceso`/consentimiento revocado,
mientras el endpoint de Chat reconoció el consentimiento `ai_chat_summary` activo
y respondió correctamente. No se maquilló ni se corrigió ese estado como parte
de este cambio de navegación; conviene auditar por separado la proyección de
estado que usa la lista del supervisor.

## Pruebas técnicas

Comando ejecutado:

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

Resultado: `BUILD SUCCESSFUL` en 1 min 3 s.

- Pruebas: 126 ejecutadas, 0 fallos, 0 errores, 0 omitidas.
- Ensamblado: `app-debug.apk` generado correctamente.

## Riesgos restantes

- La navegación se comprobó en API 33; conviene repetir el recorrido en un
  dispositivo físico antes de release.
- La barra de entrada y la barra global comparten el área inferior. Se verificó
  el envío con teclado en el emulador, pero se recomienda revisar tamaños de
  pantalla compactos y accesibilidad con fuente ampliada.
- Persiste el hallazgo independiente sobre la proyección visual del estado de
  consentimiento en Inicio/Pacientes descrito arriba.
