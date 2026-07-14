# Globo WhatsApp V1.3

Aplicación Android independiente que publica WhatsApp oficial como contenido raíz de una burbuja nativa.

## Corrección de V1.3

La prueba en Samsung confirmó que V1 y V1.2 sí iniciaban WhatsApp, pero la aplicación forzaba su propia tarea y aparecía detrás de `NativeBubbleActivity`. La pantalla anfitriona seguía visible aunque `startActivity()` no devolviera ningún error.

V1.3 elimina completamente `NativeBubbleActivity`. El `PendingIntent` mutable de `BubbleMetadata` ahora apunta directamente al componente launcher de `com.whatsapp`. Así SystemUI, no una actividad intermedia, intenta colocar WhatsApp como raíz de la ventana flotante.

## Comportamiento

- El botón **Crear globo de WhatsApp** publica un globo manual y solicita que se expanda.
- Cada notificación entrante crea o actualiza un globo por conversación.
- Al tocar el globo, Android ejecuta directamente la actividad oficial de WhatsApp.
- No existe una interfaz propia dentro del globo.
- WhatsApp oficial permanece intacto.

## Privacidad

Android concede al servicio acceso técnico a todas las notificaciones. El servicio filtra por código antes de procesarlas y solo acepta `com.whatsapp`. Usa el título y el texto únicamente para publicar la burbuja; no mantiene historial ni solicita permiso de Internet.

## Uso

1. Instala V1.3 encima de la versión anterior.
2. Abre Globo WhatsApp y pulsa **Eliminar todos los globos**.
3. Quita WhatsApp de aplicaciones recientes.
4. Pulsa **Crear globo de WhatsApp** y abre el globo nuevo.
5. Para globos automáticos, activa el acceso a notificaciones y permite **Todas las conversaciones**.

## Límite de Android 15

Android exige que la actividad utilizada como contenido de una burbuja sea redimensionable y permita ser embebida. V1.3 usa la última arquitectura disponible mediante APIs públicas: WhatsApp como destino directo del `PendingIntent`. Si la actividad launcher instalada de WhatsApp rechaza el modo embebido, una aplicación externa no puede cambiar su manifiesto ni forzarla dentro de la burbuja. Android 17 incorpora un modo para añadir cualquier aplicación a la interfaz de burbujas, pero esa función no existe en Android 15.

## Compilar

Ejecuta `gradle assembleRelease` con JDK 17 y Android SDK 35. El workflow **Build Globo WhatsApp APK** compila, valida permisos, verifica el ZIP y alinea el APK release sin firma.
