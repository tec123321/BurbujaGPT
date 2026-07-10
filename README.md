# BurbujaGPT V7

Aplicacion Android en Java que permite abrir los chats reales de ChatGPT desde una burbuja, sin usar la API de OpenAI.

## Novedades de V7

- Nuevo modo **Burbuja nativa de Android** para Android 11 o posterior. Android y Samsung la reconocen como una conversacion en burbuja.
- Notificacion de conversacion con `BubbleMetadata`, acceso directo dinamico y actividad expandible.
- El WebView deja de pausarse al minimizar.
- La misma pagina web se conserva en memoria mientras el servicio en primer plano sigue activo, incluso si Android destruye la actividad expandida al colapsar una burbuja nativa.
- Acceso directo a los ajustes de burbujas de Android / Samsung.
- El modo nativo no necesita el permiso **Aparecer encima**; solo necesita notificaciones y burbujas habilitadas.
- Se conservan los modos de globo superpuesto, app oficial y navegador.

## Modos de apertura

### Burbuja nativa de Android

Usa el sistema oficial de burbujas de Android. Al expandirse muestra el panel web interno. Al colapsarse, el servicio conserva la pagina y su sesion en condiciones normales.

### App oficial emergente

Usa la sesion y todos los chats de la aplicacion oficial. Intenta abrirla con los limites de ventana libre disponibles en Samsung.

### Navegador emergente

Abre `chatgpt.com` en Brave, Chrome u otro navegador instalado y usa la sesion que ya exista alli.

### Panel web interno

Carga `chatgpt.com` dentro de una ventana propia, movible y redimensionable. Google bloquea OAuth dentro de WebView (`disallowed_useragent`), por lo que el acceso incrustado requiere correo y contrasena de OpenAI. La app intercepta los accesos sociales y ofrece cambiar a la app oficial o al navegador.

## Uso recomendado en Samsung

1. Instala y abre **BurbujaGPT V7**.
2. Selecciona **Burbuja nativa de Android**.
3. Pulsa **Configurar burbujas de Android / Samsung**.
4. Permite las notificaciones y las burbujas de la aplicacion.
5. Si Samsung lo solicita, activa **Ajustes > Notificaciones > Ajustes avanzados > Notificaciones flotantes > Burbujas**.
6. Vuelve a BurbujaGPT y pulsa **Activar globo**.

El globo superpuesto anterior sigue disponible para los otros tres modos y conserva sus gestos de tocar, mover, redimensionar y arrastrar hacia la zona roja para apagar.

## Limites

- OpenAI no ofrece una API publica para leer el historial privado de ChatGPT. El panel muestra el sitio oficial autenticado.
- El WebView tiene una sesion independiente de Chrome y de la app oficial.
- Google rechaza el inicio de sesion dentro de WebView con `403: disallowed_useragent`. No se falsea el navegador: se usa correo/contrasena, el navegador o la app oficial.
- Una burbuja nativa solo puede mostrar una actividad de BurbujaGPT. Android no permite incrustar dentro de ella la actividad privada de la app oficial de ChatGPT ni compartir sus cookies.
- El servicio en primer plano mejora mucho la continuidad, pero Android siempre puede finalizar una aplicacion bajo presion extrema, reinicio o ahorro de bateria agresivo.
- Android no garantiza que una aplicacion externa pueda forzar otra app a abrirse en ventana libre.
- El proyecto es independiente y no esta afiliado con OpenAI.

## Compilar

En GitHub: **Actions > Build APK > Run workflow**. El resultado se publica como `BurbujaGPT-V7-debug-apk` y contiene `app-debug.apk`.

En Android Studio: abre el proyecto y usa **Build > Build APK(s)**.

El proyecto utiliza `targetSdk 35` y `minSdk 23`.
