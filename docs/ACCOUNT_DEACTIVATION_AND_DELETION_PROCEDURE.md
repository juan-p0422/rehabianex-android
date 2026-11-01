# RehabiAnex: desactivación y eliminación de cuentas

Estado: procedimiento operativo para QA interna. No autoriza borrados de cuentas reales ni sustituye una revisión jurídica.

## Flujos distintos

1. **Desactivar mi cuenta y solicitar eliminación**: acción autenticada dentro de Android. Produce una desactivación y borrado suave; bloquea acceso y escrituras, invalida relaciones activas y deja una solicitud para revisión operativa.
2. **Solicitar eliminación definitiva**: contacto manual a `rehabianex@gmail.com`, con asunto `Solicitud de eliminación de cuenta RehabiAnex`. Abrir el cliente de correo no significa que el mensaje haya sido enviado ni acredita identidad.

La exportación de autoservicio está retirada. Para acceso a información disponible, rectificaciones no ofrecidas en la aplicación, datos legacy y dudas de conservación se mantiene el canal telefónico definido para la beta.

## Contrato Android–Laravel

- Resumen: `GET /api/data-control/summary`.
- Desactivación: `POST /api/data-control/account-deactivation-requests`.
- Compatibilidad temporal: `POST /api/data-control/account-deletion-requests`; no debe anunciarse a clientes nuevos.
- Exportación heredada: `GET /api/data-control/export` responde `410 self_service_export_retired` cuando está deshabilitada y Android ya no la invoca.
- Cuerpo de desactivación:

```json
{
  "scope": "account",
  "confirmation": "DESACTIVAR MI CUENTA",
  "acknowledge_retention_limits": true
}
```

- Éxito idempotente: `202`, con `request.request_id`, `request.status`, `request.requested_at`, `request.deactivated_at`, `sync_blocked=true`, `local_cleanup_required=true`, `cleanup_status`, `manual_attention_required` y `retention_notice`.
- Errores: `401` sesión inválida; `403` autenticación reciente, rol o perfil incompatibles; `409/423` transición o solicitud ya en curso; `422` confirmación inválida; `5xx` fallo no confirmado. Android no limpia el UID ni muestra éxito ante estos errores.

## Secuencia recuperable de desactivación

1. Android exige contraseña actual, la frase exacta y una aceptación explícita de los límites de conservación.
2. Android reautentica y comprueba que el UID y el rol devueltos son los de la sesión activa.
3. Laravel, en una transacción, crea o reutiliza idempotentemente la solicitud, marca el perfil `disabled`, bloquea sincronización y conserva el folio.
4. Laravel inactiva vínculos, revoca consentimientos y cancela solicitudes pendientes relacionadas con el UID.
5. Laravel retira los tokens FCM, revoca refresh tokens e intenta deshabilitar la identidad de Firebase Authentication. Cada resultado queda en `cleanup_status`; un fallo parcial se marca para atención manual.
6. La API rechaza lecturas y escrituras posteriores aunque un ID token anterior todavía no haya expirado.
7. Solo después de recibir el `202` completo, Android suspende el registro FCM del dispositivo, cancela el trabajo único del UID, limpia Room y preferencias del propietario, cancela notificaciones y borra la sesión local.
8. Una cola offline anterior no puede reactivar la cuenta: fue cancelada localmente y cualquier reintento remoto queda bloqueado por el estado del perfil.

No existe recuperación automática de una cuenta desactivada. Un registro nuevo tampoco debe volver a asociar datos anteriores sin una decisión y un procedimiento explícitos.

## Inventario asociado

### Android

- Sesión y preferencias protegidas por UID.
- Room cifrado: perfil, resumen, notas, agenda, contactos, preferencias de notificaciones, logros y estados de sincronización.
- Trabajo de sincronización de notas identificado por UID.
- Recordatorios locales, notificaciones mostradas y deduplicación FCM.
- Estado adaptativo en memoria/local del propietario.
- Caché privada. Las exportaciones que pudieran existir de versiones anteriores no se borran automáticamente como parte de este cambio.

### Firebase y backend

- Identidad Firebase Authentication y tokens de sesión.
- Perfil de paciente o supervisor.
- Registros autorreportados, agenda, contactos, notificaciones y logros asociados al paciente.
- Solicitudes de supervisión, vínculos, consentimientos y alcances.
- Tokens FCM.
- Solicitud de desactivación, estado de limpieza y trazabilidad mínima.
- Logs operativos saneados, respaldos y datos ya procesados por proveedores, sujetos a la conservación real que se determine.

El inventario no autoriza eliminar colecciones compartidas ni registros de auditoría completos. Debe localizarse por UID e identificadores internos, evitando consultas por nombre o correo como única prueba de identidad.

## Procedimiento interno de eliminación definitiva

1. Recibir la solicitud en `rehabianex@gmail.com`. Nunca pedir contraseña, tokens, notas emocionales, historial de consumo ni copias completas de los datos.
2. Registrar un folio interno sin copiar información sensible innecesaria.
3. Verificar identidad mediante un mecanismo aprobado. Conocer el correo asociado no basta; no solicitar documentos de identidad salvo que exista un procedimiento justificado y definido.
4. Localizar la cuenta mediante UID y referencias internas. Confirmar el rol y el estado de desactivación.
5. Bloquear acceso y nuevas escrituras antes de eliminar. Si la persona todavía no usó la opción A, ejecutar primero el bloqueo seguro equivalente.
6. Enumerar y eliminar únicamente datos y relaciones del UID: perfil, registros propios, agenda, contactos, preferencias, tokens, vínculos, consentimientos y solicitudes. Conservar solo lo que tenga una razón técnica o legal documentada.
7. Registrar fallos parciales por almacén sin marcar el procedimiento como terminado. Reintentar de forma idempotente y comprobar que colas o sincronizaciones no recrean datos.
8. Revisar respaldos, logs y proveedores por separado. No prometer borrado inmediato donde no exista control o evidencia; documentar la limitación real.
9. Confirmar al titular únicamente el resultado y las limitaciones aplicables, sin revelar datos sensibles ni identificadores internos.

No se fija un plazo en este documento. Debe usarse el plazo que el responsable apruebe y que corresponda al procedimiento aplicable.

## Pruebas permitidas

Usar exclusivamente una cuenta sintética desechable en un entorno autorizado. Verificar cancelación, confirmación reforzada, reautenticación, doble toque, `202`, login posterior, token anterior, cola offline, reconexión, fallo parcial, cambio de cuenta y ausencia de exportación. No ejecutar este flujo sobre las cuentas compartidas de paciente, supervisor o administrador.
