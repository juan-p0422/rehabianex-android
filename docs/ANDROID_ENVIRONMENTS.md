# Entorno de red Android

Actualizado: 29 de septiembre de 2026.

## Variantes y URL

| Variante | URL predeterminada | Uso |
|---|---|---|
| `debug` | `https://rehabianex-api.onrender.com/api/` | Desarrollo y QA remoto con el package Firebase base |
| `staging` | `https://rehabianex-api.onrender.com/api/` | QA remoto cuando exista cliente Firebase `.staging` |
| `release` | `https://rehabianex-api.onrender.com/api/` | Candidato futuro; panel admin deshabilitado |

La variante `staging` usa `applicationIdSuffix=.staging`. El
`google-services.json` local actual contiene únicamente el package base, por lo
que staging no debe usarse para FCM hasta registrar el cliente Firebase
correspondiente.

## Mecanismo de configuración

Todas las variantes usan exclusivamente la URL oficial de Render. El valor
predeterminado se define en `app/build.gradle.kts` y se materializa como
`BuildConfig.API_BASE_URL` para que Retrofit tenga una única fuente de verdad.

La propiedad Gradle sigue disponible para automatización reproducible, pero su
valor debe coincidir exactamente con la URL oficial:

```powershell
.\gradlew.bat :app:assembleDebug `
  "-PREHABIANEX_API_BASE_URL=https://rehabianex-api.onrender.com/api/" `
  --no-daemon --console=plain
```

Una URL diferente hace fallar la configuración Gradle. No se admite loopback,
localhost, IP LAN ni tráfico HTTP claro. No se guarda la URL en
`local.properties`.

## Estado Render para FCM

El backend desplegado responde `GET /api/health` con `200`. Las rutas de alta y
revocación FCM responden `401` sin autenticación, como exige el contrato.
Render mantiene `FCM_ENABLED=true`, `FCM_DRY_RUN=true`, cola `sync` y flags
sensibles deshabilitados.

Este estado permite preparar autenticación Android online-first. No autoriza
invocar `/api/notifications/test` ni afirmar que FCM está aprobado para
producción. El smoke autenticado espera coordinación explícita con backend.

## Seguridad

- No imprimir JWT, token FCM ni el contenido de `google-services.json`.
- Usar solamente cuentas técnicas.
- Mantener sanitización y textos canónicos.
- No crear fallback local para eventos sensibles.
- `release` no se publica hasta completar E2E Render y revisión de producción.
