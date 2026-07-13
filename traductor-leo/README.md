# Traductor Leo v1

Aplicación Android experimental para traducir al español el texto visible sobre otras aplicaciones.

## Comportamiento implementado

- Captura de pantalla mediante `MediaProjection`.
- OCR local con Google ML Kit.
- Traducción automática al español mediante el endpoint web de Google Translate, sin clave de API.
- Cuadros de traducción no táctiles y con opacidad compatible con el paso de gestos en Android 12 o posterior.
- Las traducciones anteriores permanecen mientras la pantalla cambia y se sustituyen cuando la imagen vuelve a estar estable.
- La superposición y el botón flotante se ocultan brevemente antes de capturar para evitar que la aplicación se reconozca a sí misma.
- Botón flotante `↻` arrastrable para forzar una actualización; pulsación larga para detener.

## Privacidad

El reconocimiento OCR se ejecuta en el dispositivo. El texto reconocido se envía por Internet a Google Translate para obtener la traducción. No se incluye analítica, publicidad, cuenta de usuario ni almacenamiento de capturas.

## Compilación

```bash
gradle :app:assembleDebug
```

Requiere JDK 17, Android SDK 35 y conexión a Maven/Google para descargar ML Kit.
