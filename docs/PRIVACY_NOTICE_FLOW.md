# Aviso de privacidad y consentimiento de supervisión

Última actualización: 2026-08-03.

## Aviso de privacidad en registro

La versión vigente integrada en Android es `2026-08-01`. El registro queda
bloqueado hasta que la persona marque de forma explícita la aceptación del
tratamiento de datos. Al validar el formulario, Android envía siempre:

```json
{
  "privacy_notice_accepted": true,
  "privacy_notice_version": "2026-08-01"
}
```

La UI explica que esta aceptación permite tratar los datos necesarios para
crear y operar la cuenta. También muestra de forma visible que RehabiAnex no
sustituye atención médica, psicológica ni terapéutica.

Existe una doble protección:

1. El botón `Registrar` permanece deshabilitado sin aceptación.
2. `AuthValidator` rechaza cualquier intento programático con
   `acceptedPrivacy=false`, por lo que no se llama al repositorio ni al backend.

Si backend responde `422` para `privacy_notice_accepted` o
`privacy_notice_version`, `ApiErrorParser` asocia el detalle al control de
privacidad y la pantalla muestra el mensaje seguro entregado por backend.

## Separación respecto a supervisión

La aceptación del aviso no vincula supervisores, no concede acceso a datos y no
crea un consentimiento de supervisión.

Después del registro de paciente se presenta una configuración opcional con dos
resultados distintos:

- `Configurar supervisión`: abre el flujo de código/vinculación. El usuario aún
  debe revisar y confirmar la solicitud y sus permisos.
- `Continuar sin supervisor`: abre Patient Home sin crear vinculación ni
  consentimiento.

El consentimiento de supervisión continúa gestionándose mediante los endpoints
de supervisión y `consents`; no forma parte del payload de registro.

## Archivos responsables

- `data/model/AuthModels.kt`: campos y versión contractual.
- `validation/AuthValidator.kt`: bloqueo defensivo y construcción del request.
- `ui/screens/auth/RegisterScreen.kt`: aceptación explícita y textos legales.
- `ui/screens/auth/ConsentScreen.kt`: decisión opcional de supervisión.
- `navigation/RehabianexNavHost.kt`: destinos distintos para configurar u omitir.
- `data/remote/ApiErrorParser.kt`: errores `422` asociados al checkbox.

## Riesgos y mantenimiento

- Cuando cambie la versión oficial del aviso deben actualizarse conjuntamente el
  contrato backend, `PRIVACY_NOTICE_VERSION`, los textos legales aprobados y las
  pruebas.
- La ruta interna sigue llamándose `Consent` por compatibilidad de navegación,
  aunque su pantalla ahora representa configuración opcional de supervisión.
  Puede renombrarse en una migración posterior sin cambiar el comportamiento.
