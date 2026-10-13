# Etapa 6: contrato de herramientas de apoyo

## Contactos de apoyo

- `POST /api/support-contacts` y `PATCH /api/support-contacts/{id}` conservan sus rutas.
- Los teléfonos se envían normalizados; para México se usa `+52` seguido de 10 dígitos nacionales.
- `priority` acepta enteros de 1 a 5 y `5` significa mayor prioridad.
- Valores históricos 6–10 se muestran como históricos. Al editar otros campos, Android omite `priority` del `PATCH` si el usuario no la modifica; nunca la reduce silenciosamente a 5.
- La acción de apoyo abre la aplicación Teléfono mediante `ACTION_DIAL`, muestra destinatario y canal antes de abrirla y requiere confirmación fuera de RehabiAnex.

## Directorio local

- La respuesta existente admite, de forma aditiva y nullable, `source_name`, `source_url` y `reviewed_at`.
- La ausencia de metadatos se presenta como `No disponible`.
- `updated_at` es una marca técnica del recurso. La última sincronización de caché se conserva y muestra por separado en Android.
- La revisión de datos es informativa y no representa acreditación ni evaluación de calidad clínica.

## Agenda y notificaciones

- El contrato CRUD de agenda no cambia.
- Los recordatorios de agenda y horarios configurados en el dispositivo se identifican como locales; los avisos FCM se identifican como remotos.
- La demostración FCM no aparece en la navegación normal. Su pantalla solo se registra en compilaciones de diagnóstico (`BuildConfig.DEBUG`).
- `POST /api/notifications/test` permanece como diagnóstico autenticado y limitado por rol, entorno y frecuencia; producción lo rechaza incluso en modo de simulación.
- Esta etapa no añade alertas automáticas.
