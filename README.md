# Globo WhatsApp V1.1

Aplicación Android independiente que convierte las notificaciones de WhatsApp oficial en minichats dentro de burbujas nativas.

## Corrección de V1.1

La V1 intentaba iniciar una actividad de WhatsApp dentro de la tarea de la burbuja. En Android 15 / One UI, WhatsApp recupera su tarea propia y deja visible únicamente la actividad anfitriona. La V1.1 elimina ese intento: el contenido del globo ahora es una interfaz de chat propia y funcional.

## Comportamiento

- Cada notificación entrante crea o actualiza un globo por conversación.
- El globo muestra los mensajes recibidos desde que el proceso está activo.
- Permite responder mediante `Notification.Action` y `RemoteInput`, la misma acción oficial **Responder** de la notificación de WhatsApp.
- El botón ↗ abre la conversación en la aplicación oficial, en pantalla completa.
- El globo manual sirve para comprobar la interfaz; una conversación real aparece cuando llega su notificación.
- La aplicación oficial `com.whatsapp` permanece intacta.

## Privacidad

Android concede al servicio acceso técnico a todas las notificaciones. El servicio filtra por código antes de procesarlas y solo acepta `com.whatsapp`. Los mensajes permanecen exclusivamente en memoria, con un máximo de 60 por conversación, y desaparecen al cerrarse el proceso. La APK no solicita permiso de Internet.

## Uso

1. Instala WhatsApp oficial y actualiza a **Globo WhatsApp V1.1**.
2. Abre Globo WhatsApp y permite sus notificaciones.
3. Pulsa **Activar globos de mensajes** y habilita `Globo WhatsApp: mensajes`.
4. Pulsa **Permitir burbujas en Android** y permite todas las conversaciones.
5. Recibe un mensaje: aparecerá un globo con el minichat y el campo para responder.

## Límites

- No reproduce toda la interfaz, el historial completo, imágenes, audios, llamadas o estados de WhatsApp.
- La respuesta depende de que WhatsApp incluya una acción `RemoteInput` activa en su notificación.
- Al desaparecer la notificación original, la acción de respuesta puede caducar; el siguiente mensaje la renueva.

## Compilar

Ejecuta `gradle assembleRelease` con JDK 17 y Android SDK 35. El workflow **Build Globo WhatsApp APK** compila, ejecuta Lint Vital, valida permisos y alinea el APK release sin firma.
