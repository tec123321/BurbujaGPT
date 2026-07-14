# Globo WhatsApp V1.2

Aplicación Android independiente que convierte las notificaciones de WhatsApp oficial en burbujas nativas y abre la aplicación oficial desde la tarea de la burbuja.

## Corrección de V1.2

La V1 ejecutaba primero el `PendingIntent` original de la notificación. Android aceptaba esa llamada, pero la enviaba a la tarea normal de WhatsApp; por eso el panel del globo quedaba vacío y nunca se utilizaba el lanzamiento que sí funcionaba en Globo GPT.

V1.2 elimina por completo esa ruta y aplica directamente el mismo mecanismo de Globo GPT:

- Android crea una tarea nativa para `NativeBubbleActivity`.
- La actividad obtiene el launcher oficial de `com.whatsapp`.
- Reemplaza los flags del launcher por `FLAG_ACTIVITY_REORDER_TO_FRONT | FLAG_ACTIVITY_NO_ANIMATION`, eliminando `FLAG_ACTIVITY_NEW_TASK`.
- La actividad oficial hereda la tarea de la burbuja.
- No usa WebView, Shizuku, superposición ni ventana múltiple de Samsung.

## Comportamiento

- El botón **Crear globo de WhatsApp** abre un globo manual.
- Cada notificación entrante crea o actualiza un globo por conversación.
- Al tocar el globo se inicia la aplicación oficial de WhatsApp desde esa tarea.
- La aplicación oficial `com.whatsapp` permanece intacta.
- Los globos se pueden eliminar desde la pantalla principal.

## Privacidad

Android concede al servicio acceso técnico a todas las notificaciones. El servicio filtra por código antes de procesarlas y solo acepta `com.whatsapp`. Usa el título y el texto únicamente para publicar la burbuja; no mantiene un historial ni solicita permiso de Internet.

## Uso

1. Instala Globo WhatsApp V1.2 encima de la versión anterior.
2. Abre la aplicación y pulsa **Eliminar todos los globos** para descartar las tareas antiguas.
3. Pulsa **Crear globo de WhatsApp** para probar el lanzamiento manual.
4. Para los globos automáticos, activa **Globos de mensajes** y permite **Todas las conversaciones** en los ajustes de Android.

## Límite que requiere prueba real

El código replica la ruta de lanzamiento de Globo GPT. La compilación puede comprobar la estructura, flags, permisos y firma, pero solo el teléfono puede confirmar cómo la versión instalada de WhatsApp declara y reutiliza su actividad principal en esa versión concreta de One UI.

## Compilar

Ejecuta `gradle assembleRelease` con JDK 17 y Android SDK 35. El workflow **Build Globo WhatsApp APK** compila, valida los permisos, verifica el ZIP y alinea el APK release sin firma.
