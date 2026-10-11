# Alcance de autenticación Android

Actualizado: 2026-08-05.

## Alcance vigente

- La versión Android muestra y utiliza exclusivamente autenticación por correo electrónico y contraseña.
- El acceso visual de Google se retiró porque Google Sign-In no forma parte de esta versión.
- El proyecto no declara dependencias de Google Sign-In, Credential Manager ni Firebase Authentication.
- El formulario de registro tampoco ofrece autenticación con Google.

## Recuperación de contraseña

El enlace `¿Olvidaste tu contraseña?` se mantiene oculto. En el estado actual solo estaba conectado a un callback vacío: no existe pantalla, método Retrofit ni flujo backend de recuperación integrado en Android. Debe implementarse y probarse como una fase independiente antes de volver a exponerlo.

## Cambios realizados

- Se eliminó el botón deshabilitado `Continuar con Google (próximamente)` de `LoginScreen`.
- Se eliminaron los callbacks vacíos de Google y recuperación de contraseña de `LoginScreen` y `RehabianexNavHost`.
- Se retiró la excepción de red sin uso para `/auth/google` del interceptor de autenticación.
- Se conservaron sin cambios el login y el registro por correo/contraseña.
