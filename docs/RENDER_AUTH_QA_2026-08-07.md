# QA de autenticación Android contra Render — 7 de agosto de 2026

## Alcance

Diagnóstico sin modificar la lógica Android y sin utilizar ni registrar credenciales reales.

URL probada:

```text
https://rehabianex-api.onrender.com/api/
```

## Resultados

| Prueba | Resultado |
|---|---|
| `GET /health` | `200`, backend activo |
| `POST /auth/login` sin campos válidos | `422`, validación JSON operativa |
| `POST /auth/login` con campos sintácticamente válidos | `500` |
| `POST /auth/refresh` con token ficticio | `500` |
| Intentos de login desde el emulador | Android envía a Render y recibe `500` |

El mensaje Android “El servicio presenta un problema temporal” es el mapeo amigable esperado para HTTP 500. No se observó un fallo de URL, DNS, TLS, cleartext ni conectividad del emulador.

## Causa más probable

El backend usa `FIREBASE_WEB_API_KEY` antes de invocar Firebase Authentication tanto en login como en refresh. La coincidencia de `500` en ambos endpoints, mientras la validación `422` y `/health` funcionan, indica que esa variable no está configurada o no fue incorporada a la configuración cacheada del despliegue.

## Corrección requerida en Render

1. Configurar `FIREBASE_WEB_API_KEY` con la Web API Key del mismo proyecto indicado por `FIREBASE_PROJECT_ID`.
2. No usar en esa variable la service account, private key ni el JSON Base64.
3. Confirmar que `FIREBASE_AUTH_EMULATOR_HOST` y `FIRESTORE_EMULATOR_HOST` no estén definidos en Render.
4. Guardar las variables y desplegar nuevamente el último commit.
5. Limpiar/reconstruir la caché de configuración de Laravel durante el despliegue; no reutilizar una caché creada antes de definir la variable.
6. Revisar el log de Render en el momento de `POST /api/auth/login`. No copiar secretos al log ni a este repositorio.

## Criterio para repetir QA

- Credenciales inválidas deben producir `401` con JSON amigable, no `500`.
- Login válido debe producir `200` y los bloques `auth`, `profile` y `tokens`.
- Refresh inválido debe producir `401`, no `500`.
- Solo después de esos resultados deben probarse `auth/me`, perfil y navegación por rol.

