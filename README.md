# Globo GPT V13

Aplicación Android independiente para abrir ChatGPT desde un globo flotante sin
modificar, clonar ni volver a firmar el APK oficial de OpenAI.

## Modos

### Burbuja nativa con panel web persistente

- Android reconoce la notificación como una conversación y la muestra como burbuja.
- La actividad expandida contiene `https://chatgpt.com/`.
- Cookies, almacenamiento local, URL y sesión se conservan.
- La misma instancia de `WebView` permanece viva mientras el servicio está activo,
  aunque la burbuja se contraiga.
- Si Samsung rechaza la burbuja del sistema, se utiliza el globo superpuesto como
  respaldo cuando el permiso **Aparecer encima** está concedido.

Google no permite autenticarse dentro de WebView. Las cuentas que dependen de
Google Login deben usar el modo oficial o el navegador.

### Aplicación oficial en ventana emergente

- Mantiene el paquete `com.openai.chatgpt`, su firma y su veredicto de Play Integrity.
- Conserva Google Login, Plus, historial, voz y todas las funciones oficiales.
- Con Shizuku iniciado y autorizado, solicita a Samsung/Android el modo de ventana
  libre. Si One UI lo rechaza, se intenta el modo compatible del sistema.

### Modos de respaldo

- **Panel web compatible:** abre la misma sesión web en una actividad flotante.
- **Navegador:** abre ChatGPT usando las cookies de Chrome, Brave u otro navegador.

## Instalación y uso

1. Instala y abre **Globo GPT V13**.
2. Elige el modo.
3. Para la burbuja nativa, habilita notificaciones y burbujas para Globo GPT.
4. Opcionalmente concede **Aparecer encima** para el respaldo compatible.
5. Pulsa **Activar globo**.

Toca el globo para abrir o minimizar. Mantén pulsado para abrir los ajustes.
Arrástralo hacia la zona **×** para detener el servicio.

## Compilar

En GitHub: **Actions > Build APK > Run workflow**. El artefacto se publica como
`Globo-GPT-V13-native-session-debug-apk`.

El proyecto utiliza `targetSdk 35`, `minSdk 24` y Java 17.
