# Globo GPT V11

Aplicación Android ligera que mantiene instalada la aplicación oficial de ChatGPT y la abre desde un globo flotante. No modifica, clona ni redistribuye el APK de OpenAI.

## Cambio principal

V11 elimina el WebView como modo predeterminado. Al tocar el globo:

- abre o recupera la aplicación oficial `com.openai.chatgpt`;
- conserva el inicio con Google, Plus, historial, voz y las funciones nativas;
- solicita a Samsung/Android una ventana libre con tamaño emergente;
- si One UI rechaza la vista emergente, abre ChatGPT normalmente sin perder la sesión;
- evita `CLEAR_TOP` y usa reordenamiento de tarea para no reiniciar innecesariamente la app oficial.

El nombre visible es **Globo GPT** y el identificador sigue siendo `com.leonardo.burbujagpt`, por lo que se instala junto a ChatGPT oficial.

## Uso

1. Instala y abre **Globo GPT V11**.
2. Pulsa **Activar globo**.
3. Permite **Aparecer encima** y las notificaciones.
4. Toca el globo para abrir o recuperar ChatGPT.
5. Mantén pulsado el globo para volver a la configuración.
6. Arrástralo hacia la zona **×** para apagarlo.

## Límite técnico

`ActivityOptions.setLaunchBounds()` solo tiene efecto cuando el dispositivo admite ventanas libres. La app también realiza una petición de mejor esfuerzo al modo freeform de Samsung, pero Android no ofrece una API pública que garantice forzar otra aplicación a vista emergente. La alternativa segura es abrir la app oficial en pantalla normal.

## Compilar

En GitHub: **Actions > Build APK > Run workflow**. El artefacto se publica como `Globo-GPT-V11-debug-apk`.

El proyecto utiliza `targetSdk 35` y `minSdk 23`.
