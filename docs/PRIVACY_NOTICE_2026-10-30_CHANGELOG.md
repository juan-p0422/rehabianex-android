# Aviso de Privacidad 2026-10-30 — incorporación y límites

## Versión

- Versión: `2026-10-30`.
- Fecha de publicación: 30 de octubre de 2026.
- Versión anterior conservada: `2026-10-28`.

## Cambios respecto de 2026-10-28

1. Se aclara que no existe exportación de autoservicio en la beta.
2. Se conserva la atención de derechos por los canales ya definidos y `rehabianex@gmail.com` para eliminación definitiva.
3. Se distinguen la desactivación/borrado suave desde la app y la eliminación definitiva manual con verificación de identidad.
4. Se detalla el efecto comprobado sobre acceso, registros remotos, vínculos, consentimientos, solicitudes, colas offline y almacenamiento local.
5. Se aclara que respaldos, logs y datos procesados por terceros pueden conservarse bajo límites todavía no confirmados.
6. Se prohíbe enviar por correo contraseñas, tokens, notas personales, historial de consumo o datos de terceros.
7. Se describe HTTPS/TLS, SQLCipher, AES-GCM y Android Keystore conforme a la auditoría técnica, sin afirmar anonimato ni cifrado de extremo a extremo.
8. Se explica que las cuentas existentes confirman lectura de la versión nueva sin modificar aceptación de tratamiento, consentimiento sensible, supervisión ni permisos Android.

No se modificaron el responsable, el teléfono, la ubicación general ni los fines y datos previamente descritos.

## Evidencia de incorporación

- Android carga el activo `privacy-notice-2026-10-30.md` como copia local.
- `PRIVACY_NOTICE_VERSION` identifica la versión en registro y en el control de lectura.
- Laravel publica y valida la misma versión.
- La evidencia de lectura se conserva por UID y versión; una versión anterior no se transforma en confirmación de la nueva.
- La relectura desde perfil no presenta la casilla de confirmación.
- En emulador Android API 33 pasaron 4/4 pruebas instrumentadas de presentación, 18/18 pruebas del repositorio y 1/1 prueba del interceptor de lectura vigente.
- En Laravel pasaron 32 pruebas con 287 aserciones para aviso, lectura, versiones anteriores, fallos de persistencia y separación de consentimientos.

## Pendientes que el aviso no inventa

- Domicilio aplicable del responsable.
- Confirmación formal del responsable sobre la restricción de la beta a mayores de 18 años.
- Procedimiento aprobado para verificar identidad en solicitudes de eliminación definitiva.
- Plazos de atención, conservación y eliminación.
- Rotación y retención efectiva de logs en el entorno remoto.
- Región, transferencias, retención/ZDR del proveedor IA y condiciones verificadas de los demás proveedores.
- Cobertura real de respaldos de fabricantes y migración preservadora desde una base local histórica sin SQLCipher.

Mientras estos datos sigan pendientes, el aviso continúa identificado como borrador operativo para beta cerrada y no como certificación legal integral.
