# BurbujaGPT V4

Aplicación Android en Java que mantiene un globo configurable sobre otras apps y permite abrir los chats reales de ChatGPT sin utilizar la API de OpenAI.

## Modos de apertura

### App oficial emergente (predeterminado)

Usa la sesión y todos los chats de la aplicación oficial. Intenta abrirla con límites de ventana libre de Samsung.

### Navegador emergente

Abre `chatgpt.com` en Brave, Chrome u otro navegador instalado. Usa la sesión real que ya exista en ese navegador y permite el inicio con Google.

### Panel web flotante

Carga `chatgpt.com` dentro de una ventana propia, movible y redimensionable. Google bloquea deliberadamente OAuth dentro de WebView (`disallowed_useragent`), por lo que este modo requiere correo y contraseña de OpenAI. La app intercepta los accesos sociales y ofrece cambiar a la app oficial o al navegador.

## Mejoras de V4

- Selector entre app oficial, navegador y panel web al tocar el globo.
- Intento de ventana emergente con la app oficial de ChatGPT.
- Tamaño del globo ajustable entre 48 y 84 dp.
- Opacidad ajustable entre 45 y 100 %.
- Tres tamaños iniciales para el panel web.
- Panel movible arrastrando el título.
- Globo con animación al tocar y al ajustarse al borde.
- Zona roja de cierre al arrastrar el globo hacia la parte inferior.
- Pulsación prolongada para volver a los ajustes.
- Menú del panel para recargar, abrir navegador, abrir la app oficial y copiar el enlace.
- Botón de chat nuevo.
- Barra de recuperación cuando ChatGPT o la conexión devuelven un error.
- Selector de archivos, descargas y permiso de micrófono bajo demanda.
- Opción confirmada para borrar únicamente la sesión web local.
- Validación de destinos externos y bloqueo de contenido mixto.

## Uso

1. Instala el APK.
2. Abre **BurbujaGPT V4**.
3. Elige **Panel web flotante** o **App oficial emergente**.
4. Pulsa **Permitir globo flotante** y habilita **Aparecer encima**.
5. Pulsa **Activar globo**.
6. Toca el globo.

Gestos:

- Tocar: abrir el modo seleccionado.
- Mantener pulsado: abrir ajustes.
- Arrastrar: mover.
- Arrastrar hacia el círculo rojo: apagar.

## Límites

- OpenAI no ofrece una API pública para leer el historial privado de ChatGPT. El modo web muestra el sitio oficial autenticado.
- El WebView tiene una sesión independiente de Chrome y de la app oficial.
- Google rechaza el inicio de sesión dentro de WebView con `403: disallowed_useragent`. No se reintenta ni se falsea el navegador: se usa correo/contraseña, el navegador o la app oficial.
- Android no garantiza que una aplicación externa pueda forzar otra app a abrirse en ventana libre. En Samsung puede funcionar; si One UI ignora los límites, la app oficial se abre a pantalla completa.
- El proyecto es independiente y no está afiliado con OpenAI.

## Compilar

En GitHub: **Actions > Build APK > Run workflow**. El resultado se publica como `BurbujaGPT-V4-debug-apk` y contiene `app-debug.apk`.

En Android Studio: abre el proyecto y usa **Build > Build APK(s)**.

El proyecto utiliza `targetSdk 35` y `minSdk 23`.
