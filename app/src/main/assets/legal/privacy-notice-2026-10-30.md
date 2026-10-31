# Aviso de Privacidad de RehabiAnex

Versión 2026-10-30. Fecha de publicación: 30 de octubre de 2026.

## Responsable y contacto

El responsable del proyecto es Juan Oswaldo Emilio Olivares Pantoja, quien radica en Tonalá, Jalisco, México. El canal de atención informado para esta beta cerrada es el teléfono 33 2535 6657.

No se ha proporcionado un domicilio aplicable. La referencia a Tonalá, Jalisco, México indica solamente la ubicación general informada por el responsable y no sustituye un domicilio. Este aviso no atribuye responsabilidad del tratamiento al CETI. Mientras esos datos y el procedimiento de atención no estén completos, el texto debe considerarse un borrador operativo para beta cerrada y no una certificación de cumplimiento legal integral.

Para solicitar específicamente la eliminación definitiva de una cuenta se habilitó el correo rehabianex@gmail.com. Este correo no sustituye el teléfono definido para la atención general y no demuestra por sí solo la identidad de quien escribe.

## Participantes de la beta

Se ha propuesto que la primera beta cerrada se limite a personas de 18 años o más, pero el autor todavía debe confirmar expresamente esa condición antes de incorporar participantes reales. Como medida preventiva, el backend rechaza registros nuevos de pacientes menores de 18 años. Esta restricción técnica no autoriza por sí sola la beta ni sustituye el procedimiento de selección de participantes.

La participación de menores permanece fuera de alcance hasta que se definan el fundamento, la intervención de quienes ejerzan patria potestad o tutela, el papel de los supervisores y mecanismos de verificación adecuados. Un supervisor de la aplicación no se convierte automáticamente en representante legal.

## Datos tratados

RehabiAnex trata los datos que la persona proporciona para crear y utilizar su cuenta: nombre, correo, teléfono, edad, rol y datos de perfil. Cuando la persona utiliza el seguimiento, puede tratar información sensible relacionada con ánimo, ansiedad, deseo de consumo o craving, sueño, energía, fecha de sobriedad, recaídas autorreportadas, detonantes, acciones de afrontamiento y notas personales.

También puede tratar eventos de agenda, contactos de apoyo, relaciones entre paciente y supervisor, solicitudes, estados y alcances de supervisión, preferencias de privacidad y notificaciones, consultas al chatbot y el contexto mínimo autorizado para responderlas. Para operar y proteger el servicio se procesan identificadores técnicos de cuenta y dispositivo, tokens de sesión y notificación, versión o plataforma de la aplicación, fechas de operación, estados de sincronización y registros técnicos de errores. El código revisado no incorpora analítica publicitaria ni seguimiento comercial.

## Finalidades necesarias

Los datos se utilizan para autenticar la cuenta, mostrar el perfil, conservar y sincronizar registros, operar la agenda y recordatorios, presentar recursos de apoyo, gestionar solicitudes iniciadas por la persona usuaria, proteger la sesión, prevenir duplicados y atender solicitudes sobre sus datos.

La aceptación legal del aviso y la confirmación de lectura se registran por versión. Para pacientes nuevos, el tratamiento de datos sensibles de seguimiento requiere un consentimiento expreso separado. La participación en la beta, la lectura del aviso y una casilla general no sustituyen ese consentimiento.

## Funciones opcionales y supervisión

Son opcionales la supervisión, cada alcance compartido, las notificaciones y el uso del chatbot. Aceptar o leer este aviso no activa un supervisor, no concede alcances y no otorga permisos de Android.

El código de supervisor únicamente permite crear una solicitud. El supervisor debe aceptarla y el paciente debe activar de forma explícita los alcances elegidos. El paciente puede pausar o revocar el consentimiento o solicitar la desvinculación. Estas acciones bloquean consultas futuras según su estado, pero no borran información que ya haya sido consultada. La supervisión es opcional y no sustituye atención profesional, servicios de emergencia ni representación legal.

La privacidad visual reduce la exposición de identidad en algunas pantallas, pero no implica anonimato total. Los identificadores técnicos y los datos de cuenta siguen siendo necesarios para operar el servicio.

## Proveedores y consultas de inteligencia artificial

La aplicación utiliza Firebase Authentication para identidad, Cloud Firestore para información remota y Firebase Cloud Messaging para notificaciones. El backend se aloja en Render. Cuando se utiliza el chatbot, el backend usa OpenRouter y el modelo configurado pertenece actualmente a la familia Google Gemini.

El backend envía al proveedor de inteligencia artificial la consulta y solamente el contexto autorizado y minimizado. No debe incluir alcances retirados ni ampliar permisos por instrucciones de la persona usuaria o del modelo. La API revisada no guarda un historial de las preguntas y respuestas del chatbot, pero el proveedor puede procesar temporalmente la solicitud conforme a sus propias condiciones. Los proveedores pueden procesar datos fuera de México. Las regiones contratadas, las cláusulas de transferencia y los periodos de conservación del proveedor siguen pendientes de confirmación.

## Persistencia y conservación

Los datos de cuenta y operación se conservan remotamente en Firebase y, cuando la función lo requiere, localmente en el dispositivo. Los registros, perfiles en caché, agenda, contactos y colas offline de Room se almacenan en una base protegida con SQLCipher. La clave de esa base se genera de manera aleatoria y se protege con AES-GCM y Android Keystore. La sesión y determinados datos locales también se protegen con AES-GCM y Android Keystore. Estas medidas no convierten el servicio en cifrado de extremo a extremo: Laravel, Firebase y los proveedores autorizados pueden acceder al contenido que necesitan procesar.

Las bases locales, sesiones y preferencias sensibles conocidas se excluyen de las reglas de respaldo de Android. Esta configuración no garantiza el comportamiento de todos los fabricantes ni cubre archivos futuros que no se incorporen a las reglas. Los logs operativos revisados se limitan a estados, clases de error y referencias técnicas y no deben contener contraseñas, tokens, consultas personales ni cuerpos sensibles; su rotación y conservación en el entorno remoto siguen pendientes de confirmación. Las copias de seguridad, logs y datos ya procesados por terceros pueden estar sujetos a políticas y periodos fuera del control directo de la aplicación.

No existe un periodo general de conservación confirmado ni un borrado automático integral verificable. La opción de desactivación bloquea accesos y nuevas sincronizaciones, deshabilita la identidad cuando el proveedor lo permite, revoca tokens y marca los datos para revisión y eliminación. Es un borrado suave, no una certificación de eliminación física inmediata.

La eliminación definitiva requiere intervención manual. No se promete la desaparición inmediata de respaldos, registros de seguridad o información ya procesada por proveedores.

## Acceso, rectificación y eliminación

RehabiAnex no ofrece exportación de datos por autoservicio en esta beta cerrada. La persona puede consultar la información que la aplicación muestra y rectificar los campos habilitados. Para solicitudes de acceso, correcciones que no estén disponibles en la aplicación, datos legacy o dudas de conservación se conserva el teléfono 33 2535 6657. Para solicitar la eliminación definitiva se utiliza rehabianex@gmail.com.

Control de datos ofrece dos mecanismos distintos:

1. **Desactivar mi cuenta y solicitar eliminación.** Requiere autenticación reciente y confirmación reforzada. El servidor bloquea el acceso y las nuevas escrituras, revoca sesiones y notificaciones, inactiva vínculos y consentimientos, cancela solicitudes relacionadas e impide que colas offline anteriores vuelvan a sincronizar. Los registros remotos quedan marcados para el procedimiento posterior; esta acción no acredita su eliminación física inmediata. Android cancela trabajos y limpia Room, preferencias y estado local del UID solamente después de que el servidor confirme el bloqueo. Si la limpieza o el bloqueo son parciales, el estado requiere atención manual y no se presenta como eliminación definitiva. Archivos creados por versiones anteriores no se borran automáticamente.
2. **Solicitar eliminación definitiva.** La persona escribe a rehabianex@gmail.com con el asunto “Solicitud de eliminación de cuenta RehabiAnex”, indica el correo asociado y solicita la eliminación. El responsable debe verificar la identidad mediante el procedimiento autorizado antes de actuar y confirmar el resultado y sus límites al finalizar. Abrir el cliente de correo no significa que el mensaje haya sido enviado y conocer el correo asociado no basta para autorizar la eliminación.

No se deben enviar por correo contraseñas, tokens, notas emocionales, historial de consumo, copias completas de la información ingresada ni datos de terceros. Tampoco se deben enviar documentos de identidad salvo que exista un procedimiento justificado y previamente definido.

Limpiar la caché, cerrar sesión o desinstalar la aplicación no elimina por sí solo la información remota. No se ha aprobado un plazo general para completar la eliminación definitiva.

## Protección de la información

La variante beta revisada utiliza HTTPS/TLS para proteger el contenido durante su transmisión entre Android, Laravel y los proveedores autorizados, con validación normal del certificado y del nombre del servidor. No se permite HTTP externo en la variante distribuible. Firebase y el proveedor de inteligencia artificial utilizan conexiones HTTPS en la configuración revisada.

En el dispositivo, Room utiliza SQLCipher; la clave de la base, la sesión y determinados datos locales se protegen mediante AES-GCM y Android Keystore. También se aplican autenticación, controles de rol, vínculo, consentimiento y alcance, validación de entradas, límites de consumo y minimización del contexto enviado al chatbot. Un UID seudónimo no equivale a cifrado ni anonimato. Estas medidas no constituyen cifrado de extremo a extremo y no permiten garantizar seguridad absoluta.

## Cambios del aviso

Cada cambio material se publica con una versión y fecha nuevas. Las evidencias de versiones anteriores se conservan, pero confirmar una versión anterior no cuenta como lectura de una versión distinta. Las cuentas existentes deben leer y confirmar esta versión antes de volver al panel; esta confirmación de lectura no cambia retroactivamente la aceptación del tratamiento, no activa el tratamiento sensible y no modifica consentimientos de supervisión ni permisos Android.

## Límites de RehabiAnex

RehabiAnex es una herramienta de registro y apoyo. No realiza diagnósticos, no prescribe tratamientos, no sustituye atención médica, psicológica o terapéutica y no garantiza atención de emergencias. Ante una urgencia, la persona debe utilizar los servicios de emergencia o atención profesional disponibles en su localidad.
