# Checklist Android contra backend real o staging

Este checklist valida el contrato Android sin guardar credenciales ni tokens en el repositorio.

## Datos requeridos

- URL HTTPS del backend o staging terminada en `/api/`.
- Cuenta paciente de prueba.
- Cuenta supervisor autorizada de prueba.
- Supervisor con código corto disponible.
- Relación y consentimiento desechables para probar vinculación, pausa, reactivación, revocación y desvinculación.
- Confirmación del esquema de respuesta de `ai-chat-sessions` y `ai-chat-messages` si el historial será consumido por Android.

## Preparación

- [ ] Compilar la variante `staging`, que usa Render por HTTPS y se instala separada de debug.
- [ ] Si existe otro host HTTPS de staging, usar `-PSTAGING_API_BASE_URL=https://host/api/`.
- [ ] Confirmar que no se usa una cuenta ni información real.
- [ ] Limpiar Logcat y verificar que solo existe logging HTTP `BASIC` en debug.
- [ ] Registrar IDs de prueba fuera del repositorio.

## Autenticación

- [ ] Login paciente: persistir `id_token` y `refresh_token` desde `auth`, con fallback `tokens`.
- [ ] Login supervisor: confirmar rol, `authorized`, `verified` y `status`.
- [ ] Ejecutar `GET /auth/me` con ambos roles.
- [ ] Forzar un 401 y confirmar refresh y reintento único.

## Notificaciones del paciente

- [ ] `GET /notification-settings` sin `patient_uid`.
- [ ] `POST /notification-settings` sin `patient_uid`, `user_uid` ni `supervisor_uid`.
- [ ] Confirmar aliases `daily_check_in_*` y `daily_note_*` sincronizados.
- [ ] Confirmar persistencia de los tres campos de alertas y timezone.
- [ ] Si un backend anterior responde 422 por ID faltante, registrar incompatibilidad del backend; no cambiar el cliente final.

## Supervisión y consentimiento

- [ ] Crear solicitud con `type=link`.
- [ ] Confirmar lectura de solicitud legacy sin `type`.
- [ ] Crear solicitud con `type=unlink`.
- [ ] Probar `POST /supervisors/{supervisor_uid}/patients/{patient_uid}/unlink`.
- [ ] Pausar con `PATCH /consents/{id}` y `{ "status": "paused" }`.
- [ ] Confirmar 403 en notas y detalle durante pausa.
- [ ] Reactivar con `{ "status": "active" }`.
- [ ] Revocar con `{ "explicit_consent": false }`.
- [ ] Confirmar que notas, métricas y Chat IA quedan bloqueados tras revocación.

## Proyección segura del supervisor

- [ ] `GET /supervisors/{uid}/patients` funciona sin email ni teléfono.
- [ ] Con privacidad activa, Android no muestra `full_name` aunque llegue por compatibilidad.
- [ ] Confirmar prioridad visual: `nickname`, `display_name`, `patient_ref`, fallback amigable.
- [ ] Abrir detalle y confirmar que solo se muestran notas autorizadas.
- [ ] Confirmar que un 403 elimina el contenido anterior de la pantalla.

## Chat IA

- [ ] Modo `local` envía únicamente `supervisor_uid`, `question`, `mode`.
- [ ] Modo `ai` o `fallback_local` muestra `answer` no vacío.
- [ ] Pausa o revocación de `ai_chat_summary` bloquea nuevas preguntas.
- [ ] Confirmar que mensajes viven solo en memoria y no en Room.
- [ ] Validar 403 del historial cuando backend publique su esquema contractual.
- [ ] Confirmar aviso de limitación del historial por cambios de consentimiento.

## Evidencia a conservar

- Fecha, ambiente y versión backend.
- Código HTTP y resultado funcional de cada paso, sin tokens ni datos sensibles.
- Capturas sanitizadas de estados active, paused, revoked y unlinked.
- Resultado de compilación y versión del APK probado.
