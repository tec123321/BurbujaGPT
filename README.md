# Globo WhatsApp V1

Aplicación Android independiente que convierte las notificaciones de la aplicación oficial de WhatsApp en conversaciones compatibles con las burbujas nativas del sistema.

## Comportamiento

- Un botón crea un globo manual para abrir WhatsApp.
- Con acceso a notificaciones, cada conversación entrante crea o actualiza su propio globo.
- Al tocar un globo de mensaje, se reutiliza el `PendingIntent` original emitido por WhatsApp para intentar abrir esa conversación concreta.
- La aplicación oficial `com.whatsapp` permanece intacta, con su firma, sesión, historial, llamadas y actualizaciones.
- No usa WebView, Shizuku, superposición, Accesibilidad, Internet ni almacenamiento externo.

## Privacidad

Android concede al servicio acceso técnico a todas las notificaciones. El servicio filtra por código antes de procesarlas y solo acepta `com.whatsapp`. No guarda nombres ni mensajes, no los registra y la APK no solicita permiso de Internet. El texto visible se duplica únicamente en la notificación local que Android usa como burbuja.

## Uso

1. Instala WhatsApp oficial y **Globo WhatsApp**.
2. Abre Globo WhatsApp y permite sus notificaciones.
3. Pulsa **Activar globos de mensajes** y habilita `Globo WhatsApp: mensajes`.
4. Pulsa **Permitir burbujas en Android** y permite todas las conversaciones.
5. Usa **Crear globo manual** si quieres abrir WhatsApp sin esperar un mensaje.

## Límite de Android

En Android 11–16, el sistema solo garantiza que la actividad anfitriona permanezca dentro de la burbuja. WhatsApp puede reutilizar una tarea existente y abrir la conversación en pantalla completa. El código intenta conservar la tarea de la burbuja, pero una app externa no puede imponer ese comportamiento a WhatsApp mediante API públicas.

## Compilar

Ejecuta `gradle assembleDebug` con JDK 17 y Android SDK 35. El workflow **Build APK** también compila, verifica la firma y publica `Globo-WhatsApp-V1.apk`.
