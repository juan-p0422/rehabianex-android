# Etapa 10 — Accesibilidad y regresión Android

Fecha de ejecución: 2026-10-13 (America/Mexico_City).

Este documento registra una repetición adaptada de los 75 casos del informe. No reemplaza ni modifica sus resultados históricos. `Aprobado` significa que el criterio indicado quedó cubierto por la evidencia citada; no equivale a una certificación integral del producto.

## Versión y entorno

- Base Android: `30f23c7` (`feat: add opt-in adaptive support rules`).
- Rama: `codex/revisiones-rehabianex-stage10`.
- Aplicación: `1.0` (`versionCode 1`), `compileSdk 36`, `targetSdk 36`, `minSdk 26`.
- Emulador: `Copy_of_Pixel_4_API_33`, Android 13 / API 33, 1080 × 2280.
- Escalas de fuente comprobadas: 1.5 para toda la suite instrumentada y 2.0 para las seis pruebas enfocadas de accesibilidad. Al finalizar se restauró 1.0.
- API 26: no ejecutada; no existe imagen ni AVD API 26 instalado. La declaración `minSdkVersion:'26'` sí se verificó en el APK.
- TalkBack manual: no ejecutado. Se verificaron semánticas con Compose UI Test, pero esto no sustituye un recorrido humano con lector y audio.
- Datos: exclusivamente estados y datos sintéticos; no se usaron las cuentas compartidas ni se realizaron eliminaciones, suspensiones o avisos reales.

## Leyenda de evidencia

- `U`: pruebas unitarias JVM (223/223).
- `I`: pruebas instrumentadas Compose/Room en API 33 a fuente 150 % (29/29).
- `I200`: pruebas enfocadas de accesibilidad a fuente 200 % (6/6).
- `S`: inspección estática, compilación, lint o metadatos del APK.
- `M`: recorrido/medición con ADB en emulador.
- `B`: corresponde principalmente al backend y se entrega al agente responsable.

## Matriz 001–075

| Caso | Nombre | Estado actual Android | Evidencia / límite |
|---:|---|---|---|
| 001 | Registro anónimo integral | Aprobado | U: validación, contrato y cambio de usuario; sin cuenta real. |
| 002 | Aviso de privacidad previo y consultable | Aprobado | U/I: presentación y persistencia del flujo. |
| 003 | Separación de consentimientos y permisos | Aprobado | U/I: consentimiento de supervisión y permisos se mantienen separados. |
| 004 | Rechazo de permiso opcional | Aprobado | U/S: la denegación no bloquea el flujo principal. |
| 005 | Cambio posterior de privacidad y consentimiento | Aprobado | U/I: estados posteriores y privacidad visual. |
| 006 | Configuración inicial del perfil | Aprobado | U: validación y contratos de perfil. |
| 007 | Validación de configuración incompleta | Aprobado | U: campos inválidos y mensajes. |
| 008 | Rectificación del perfil | Aprobado | U: actualización y sincronización segura. |
| 009 | Persistencia segura del perfil | Aprobado | I: Room cifrado sin encabezado SQLite plano. |
| 010 | Perfil sin conexión | Aprobado | I/U: caché por propietario y estado offline. |
| 011 | Registro emocional completo | Aprobado | U/I: validación y presentación. |
| 012 | Registro sin nota libre | Aprobado | U: nota opcional. |
| 013 | Selección consciente de escalas | Aprobado | I: escalas inicialmente sin selección y diferencia frente a 0. |
| 014 | Craving alto y apoyo adaptativo | Aprobado | U/I: regla experimental opt-in y explicación. |
| 015 | Registro emocional offline | Aprobado | I/U: pendiente por propietario y reconexión. |
| 016 | Historial semanal | Aprobado | U/I: siete días naturales y rango visible. |
| 017 | Historial mensual | Aprobado | U/I: selector y rango mensual. |
| 018 | Intervención por ansiedad alta | Aprobado | U: recurso breve configurado. |
| 019 | Biblioteca manual de intervenciones | Aprobado | U/I: navegación a recursos existentes. |
| 020 | Regla por patrón o inactividad | Aprobado | U: umbrales de prueba configurables e inactividad neutral. |
| 021 | Alta de reunión | Aprobado | U/I: formulario y selector de fecha. |
| 022 | Alta de cita con nota opcional | Aprobado | U: contrato de agenda. |
| 023 | Recordatorio local de agenda | Aprobado | U: política de programación y cancelación. |
| 024 | Permiso de notificaciones desactivado | Aprobado | U/S: banner, Ajustes y flujo sin bloqueo; no se envió notificación real. |
| 025 | Recordatorio adaptativo JITAI | Aprobado | U: opt-in, frecuencia, horario y desactivación. |
| 026 | Datos verificables del directorio | Aprobado | U/I: fuente y fechas cuando existen. |
| 027 | Filtro por tipo de recurso | Aprobado | U: filtrado local. |
| 028 | Directorio sin internet | Aprobado | U: caché offline. |
| 029 | Recurso con datos incompletos | Aprobado | I: muestra `No disponible`. |
| 030 | Actualización al recuperar conexión | Aprobado | U: refresco y control de solicitudes concurrentes. |
| 031 | Alta y consulta de contacto | Aprobado | U: modelos, formato y repositorio. |
| 032 | Validación de contacto | Aprobado | U: teléfono, prioridad y texto legítimo. |
| 033 | Solicitud de apoyo discreta | Aprobado | U: selección determinista de destinatario y canal. |
| 034 | Apoyo sin contactos | Aprobado | I/U: acción deshabilitada y acceso a configuración. |
| 035 | Alerta automática opcional | No ejecutado | Requiere ampliación de alcance y consentimiento específico; no está implementada. |
| 036 | Cálculo de días sobrios | Aprobado | U: fechas y zona horaria. |
| 037 | Actualización temporal del contador | Aprobado | U: recálculo temporal. |
| 038 | Cambio de fecha de sobriedad | Aprobado | U: confirmación e impacto. |
| 039 | Logros por hitos | Aprobado | U/S: hitos existentes y estado textual, no solo color. |
| 040 | Métricas de eventos y asistencia | Aprobado | U/I: agendado, completado y asistencia autorreportada. |
| 041 | Cifrado local de datos sensibles | Aprobado | I/U: base cifrada y política de clave. |
| 042 | Exportación de datos | Aprobado | I/U: URI privada temporal, permiso de lectura y estructura; contenido sintético. |
| 043 | Rectificación de datos | Aprobado | U: campos editables y conflicto de red. |
| 044 | Eliminación de datos | Aprobado | U/I: reautenticación, frase reforzada y limpieza solo tras bloqueo remoto simulado; no hubo borrado real. |
| 045 | Denegación por rol y consentimiento | Aprobado | U: autorización negativa en cliente/repositorio. |
| 046 | Sincronización con red | Aprobado | U: carga remota y persistencia local. |
| 047 | Conservación pendiente sin red | Aprobado | I/U: cola por propietario. |
| 048 | Reintento al recuperar red | Aprobado | U: reconexión acotada. |
| 049 | Control de duplicados y aislamiento | Aprobado | I/U: identificadores, tombstones y separación por usuario. |
| 050 | Fallo temporal del backend | Aprobado | U: conserva sesión/datos y muestra error recuperable. |
| 051 | Resolución de supervisor habilitado | Aprobado | U: resolución previa sin mutación. |
| 052 | Solicitud confirmada por el paciente | Aprobado | U: confirmación y ruta oficial. |
| 053 | Bloqueo antes de aceptación | Aprobado | U: estado pendiente no concede acceso. |
| 054 | Selección de datos compartidos | Aprobado | U/I: empieza sin alcances y envía solo selección explícita. |
| 055 | Consulta limitada a permisos vigentes | Aprobado | U: proyección por alcance. |
| 056 | Pausa y revocación de acceso | Aprobado | U/I: estados diferenciados. |
| 057 | Bloqueo posterior a revocación | Aprobado | U/I: contenido previo se reemplaza y no se ofrece reactivación silenciosa. |
| 058 | Autorización administrativa | Aprobado | U: transición administrativa y refresco, con API simulada. |
| 059 | Suspensión y reactivación administrativa | Aprobado | U: transiciones y errores, con API simulada. |
| 060 | Autorización sin acceso automático | Aprobado | U: autorización no crea vínculo ni consentimiento. |
| 061 | Elegibilidad del Chat IA | Aprobado | U/I: rol, habilitación y paciente con alcance. |
| 062 | Resumen con periodo indicado | Aprobado | U/I: periodo visible. |
| 063 | Resumen con datos faltantes | Aprobado | U/I: límites de datos visibles. |
| 064 | Inactividad sin inferir recaída | Aprobado | U/I: texto neutral y prueba de política. |
| 065 | Validación previa en backend | No ejecutado | B: no puede concluirse desde Android; coordinación con agente backend. |
| 066 | Intento de ampliar permisos por prompt | No ejecutado | B: control autoritativo del backend; cliente no amplía campos. |
| 067 | Rechazo de diagnóstico | No ejecutado | B: control clínico autoritativo del backend. |
| 068 | Rechazo de medicación o prescripción | No ejecutado | B: control clínico autoritativo del backend. |
| 069 | Demora o indisponibilidad de IA | Aprobado | U/I: timeout, error y fallback identificado, sin reintento facturable automático. |
| 070 | Límites de consumo y secretos | Aprobado parcial | U/I/S: cliente muestra cuota y no contiene clave del proveedor; el límite autoritativo se verifica en backend. |
| 071 | Tiempo de inicio frío y caliente | No ejecutado | M: 3 fríos 2808/2671/2324 ms; llamada ya visible `TotalTime=0`, `WaitTime=14 ms`, `LaunchState=UNKNOWN`. Sin umbral no hay veredicto de cumplimiento. |
| 072 | Compatibilidad Android 8.0 | No ejecutado | S: APK declara minSdk 26; falta instalación/recorrido en API 26 por ausencia de AVD. |
| 073 | Accesibilidad básica | No ejecutado | I/I200/S: semánticas, áreas táctiles, contraste calculado, fuente 150/200 % pasan; falta recorrido manual con TalkBack. |
| 074 | Estabilidad y recuperación | No ejecutado | M/U/I: no hubo crash/ANR en esta ejecución y `exit-info` solo mostró force-stop; una ejecución no demuestra ausencia general de ANR ni reemplaza prueba prolongada. |
| 075 | Navegación y organización arquitectónica | Aprobado | U/I/S: rutas por rol, retorno y separación ViewModel/repositorio. |

Resumen Android: 66 aprobados, 0 fallidos y 9 no ejecutados/no concluyentes. Los casos con evidencia parcial se mantienen conservadoramente como `No ejecutado` cuando falta un criterio obligatorio.

## Accesibilidad enfocada

- Navegación inferior: rol `Tab`, estado seleccionado anunciado, etiqueta única y objetivo mínimo de 48 dp.
- Menú y selectores de hora: objetivo mínimo de 48 dp; acciones `Aumentar/Disminuir` con contexto.
- Formularios y Chat IA: etiquetas persistentes, respuesta larga compuesta y controles alcanzables a 150/200 %.
- Agenda: celdas con altura flexible y descripción de fecha, número de eventos y estado `hoy`.
- Gráfica: alternativa textual con todos los valores, además del color/altura.
- Contraste WCAG calculado para combinaciones modificadas: negro/verde 4.78:1, negro/azul 7.47:1, blanco/rojo oscuro 6.54:1, blanco/verde oscuro 5.82:1 y gris de texto/blanco 5.74:1.

## Riesgos pendientes

1. Falta recorrido manual de TalkBack, orden de foco auditivo y confirmación de anuncios dinámicos.
2. Falta instalación y recorrido en API 26 real/emulado.
3. No existe umbral aprobado para tiempo de inicio; el valor caliente de 0 ms refleja precisión/método de `am start -W`, no inicio instantáneo.
4. La ejecución corta sin ANR no permite afirmar estabilidad general; se recomienda prueba prolongada con trazas y perfiles de arranque.
5. Los controles autoritativos de los casos 065–070 deben consolidarse con la evidencia del backend.

## Reversión

Revertir únicamente el commit de etapa 10 restaura los componentes y pruebas anteriores. No se requieren migraciones de base de datos ni cambios de contrato HTTP.
