# Globo GPT V13

Aplicación Android que publica una conversación compatible con las burbujas nativas del sistema y abre la actividad de la aplicación oficial de ChatGPT desde la tarea de esa burbuja.

## Qué cambió

- Eliminado por completo el WebView.
- Eliminados Shizuku, la superposición y el modo de ventana libre/emergente.
- Eliminados los permisos de Internet, micrófono y “Aparecer encima”.
- La burbuja se crea con `Notification.BubbleMetadata`, un acceso directo de conversación persistente y una actividad `allowEmbedded`/redimensionable.
- Al expandirla, la actividad anfitriona inicia `com.openai.chatgpt` sin `NEW_TASK`, para conservarla dentro de la tarea de la burbuja.
- La APK oficial no se modifica: mantiene su firma, sesión, Google Login, Plus, historial, voz y actualizaciones.

## Uso

1. Instala ChatGPT oficial y **Globo GPT V13**.
2. Abre Globo GPT y pulsa **Activar burbuja nativa**.
3. Permite las notificaciones.
4. Si One UI deja la conversación como notificación, pulsa **Permitir burbujas en Android** y habilita las burbujas para Globo GPT.
5. Toca el icono de burbuja de la conversación “ChatGPT”.

## Compatibilidad

Requiere Android 11 o posterior. En Android 11–16 el sistema solo garantiza el contenedor de burbuja para la actividad anfitriona; que una actividad de otro paquete permanezca en esa misma tarea depende de su modo de lanzamiento y de la implementación del fabricante. Android 17 incorpora oficialmente la posibilidad de añadir aplicaciones completas a la interfaz de burbujas.

## Compilar

En GitHub: **Actions > Build APK > Run workflow**. El artefacto se publica como `Globo-GPT-V13-native-app-bubble`.
