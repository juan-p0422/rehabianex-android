# Aviso de privacidad y consentimientos separados

Última actualización: 2026-10-30.

## Versión vigente

La versión integrada en Android es `2026-10-30`. El registro convencional muestra el aviso antes de aportar datos personales y envía, después de la decisión explícita de la persona:

```json
{
  "privacy_notice_accepted": true,
  "privacy_notice_version": "2026-10-30"
}
```

`AuthValidator` rechaza el registro sin esa decisión y Laravel valida exactamente la versión vigente. Esto no sustituye el consentimiento separado para datos sensibles del seguimiento.

## Cuentas existentes y lectura

Una cuenta cuya evidencia corresponde a `2026-10-28` u otra versión anterior debe ver el aviso `2026-10-30` antes de volver al panel y confirmar únicamente su lectura. La evidencia se guarda por UID y versión después de que Laravel la confirme.

La confirmación fallida mantiene el aviso abierto y no concede acceso. Si ya existe evidencia local para la versión vigente y el servidor falla de manera transitoria, la política offline permite continuar con una advertencia; una evidencia antigua, de otro UID o invalidada por una respuesta autoritativa no permite continuar.

La relectura desde el perfil es informativa y no muestra controles de confirmación. Las evidencias históricas permanecen en backend; cambiar de versión no reescribe resultados anteriores.

## Separación de decisiones

Se registran y evalúan por separado:

- aceptación del aviso para el tratamiento necesario durante un registro nuevo;
- confirmación de lectura de la versión vigente para una cuenta existente;
- consentimiento expreso para datos sensibles de seguimiento;
- consentimiento y alcances de supervisión;
- permisos Android, incluidas notificaciones.

Confirmar la lectura de una versión nueva no activa retroactivamente un tratamiento, supervisor, alcance o permiso Android.

## Archivos responsables

- `app/src/main/assets/legal/privacy-notice-2026-10-30.md`: texto editable vigente.
- `data/model/AuthModels.kt`: versión enviada por Android.
- `validation/AuthValidator.kt`: validación defensiva del registro.
- `data/repository/PrivacyNoticeRepository.kt`: aviso remoto/local, evidencia por versión y política offline.
- `ui/screens/privacy/PrivacyNoticeScreen.kt`: lectura requerida y relectura informativa.
- `ui/screens/auth/RegisterScreen.kt`: acceso al aviso y decisión del registro.
- `core/network/PrivacyNoticeRequiredInterceptor.kt`: precondición de lectura vigente.

## Mantenimiento

Un cambio material exige actualizar coordinadamente Android, Laravel, el activo legal y las pruebas. No debe reutilizarse la evidencia de otra versión ni cambiarse el consentimiento sensible o de supervisión salvo que el propio cambio lo requiera y se explique por separado.
