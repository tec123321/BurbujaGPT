# Globo WhatsApp V2

Aplicación Android experimental que ejecuta WhatsApp oficial en una pantalla virtual privilegiada y muestra esa superficie dentro de una burbuja nativa.

## Por qué cambia la arquitectura

V1.3 entregaba directamente la actividad launcher de `com.whatsapp` a `BubbleMetadata`. En One UI, WhatsApp rechazó ese contenedor: Android cerró el panel y abrió la aplicación como una tarea normal.

V2 mantiene una actividad propia y redimensionable como contenido del globo. Dentro de ella:

1. Shizuku crea una pantalla virtual con identidad shell.
2. La aplicación oficial de WhatsApp se inicia o mueve a esa pantalla.
3. Un `SurfaceView` dibuja la pantalla virtual dentro del globo.
4. Los toques, desplazamientos y Atrás se reenvían al display.
5. Al minimizar el globo, el display se desconecta de la superficie sin liberar la sesión.

No usa WebView ni modifica, clona o vuelve a firmar WhatsApp.

## Conversaciones

- El botón **Crear globo de WhatsApp** abre la aplicación oficial dentro del globo.
- El servicio de notificaciones crea un globo por conversación.
- Cuando aún existe la notificación original, V2 ejecuta su `PendingIntent` en el display virtual para intentar abrir el chat exacto.
- Si ese intento ya no está disponible, abre la pantalla principal de WhatsApp.
- Todos los globos comparten un display persistente para evitar varias copias incompatibles de la tarea única de WhatsApp.

## Requisitos y uso

1. Android 11 o posterior.
2. WhatsApp oficial instalado.
3. Shizuku instalado, iniciado y autorizado para Globo WhatsApp.
4. Notificaciones permitidas para que Android publique la burbuja.
5. Burbujas habilitadas para **Todas las conversaciones** de Globo WhatsApp.
6. Acceso a notificaciones solo si se desean globos automáticos por chat.

Después de instalar:

1. Abre Shizuku y pulsa **Iniciar**.
2. Abre Globo WhatsApp y pulsa **Preparar Shizuku**.
3. Autoriza la aplicación.
4. Pulsa **Crear globo de WhatsApp**.
5. Abre el globo nuevo.

Shizuku debe volver a iniciarse después de reiniciar el teléfono.

## Privacidad y alcance del permiso

El servicio de notificaciones filtra por `com.whatsapp`, usa temporalmente título y texto para construir la conversación de Android y no guarda historial. La APK no solicita `INTERNET`, contactos, almacenamiento, micrófono, superposición ni Accesibilidad.

Shizuku concede capacidad de nivel shell. V2 la usa para crear el display, localizar o mover la tarea de WhatsApp e inyectar eventos de entrada. El código fuente se incluye para auditoría.

## Limitaciones

- Es una solución experimental dependiente de Shizuku y de los servicios internos de Android/One UI.
- El teclado del display secundario no es fiable en todos los Samsung; el botón de teclado del globo incluye un compositor de respaldo que inserta texto sin enviarlo automáticamente.
- Llamadas, cámara, selector de archivos, permisos y ventanas del sistema pueden aparecer fuera del display virtual según la versión de One UI.
- La compilación comprueba estructura y permisos, pero el comportamiento visual final solo puede confirmarse instalándola en el teléfono objetivo.

## Instalación sobre V1.3

La clave privada usada para firmar V1.3 no forma parte del repositorio ni del archivo fuente conservado. El primer APK V2 usa una clave permanente nueva, por lo que Android requiere desinstalar **solo Globo WhatsApp V1.3** antes de instalar V2. WhatsApp oficial y sus chats no se desinstalan.

## Compilar

Ejecuta `gradle assembleRelease` con JDK 17, Gradle 8.9 y Android SDK 35. El workflow **Build Globo WhatsApp APK** compila, analiza permisos, verifica el ZIP y alinea el APK release sin firma.

El motor adaptado se distribuye bajo GPL-3.0. Consulta `THIRD_PARTY_NOTICES.md`.
