# BurbujaGPT V8

Aplicacion Android en Java que abre los chats reales de ChatGPT desde una burbuja, sin usar la API de OpenAI.

## Correccion principal de V8

V7 podia cerrarse al registrar o expandir la burbuja en algunos dispositivos Samsung. V8 cambia el arranque para evitar ese cierre:

- La notificacion del servicio en primer plano y la notificacion de conversacion son independientes.
- La conversacion usa un acceso directo nuevo, permanente y compatible con Android 11 o posterior.
- La burbuja se crea desde el ID del acceso directo, como recomienda Android 11+.
- Se eliminan metadatos opcionales que podian provocar incompatibilidades de One UI.
- La aplicacion verifica si Samsung acepto realmente la notificacion como burbuja.
- Si Samsung la rechaza o ocurre una excepcion, la aplicacion no se cierra: activa automaticamente el globo superpuesto compatible.
- Los fallos de ejecucion se guardan y pueden copiarse desde la pantalla principal con **Copiar diagnostico**.
- **Reintentar burbuja nativa** limpia el bloqueo preventivo y registra nuevamente la conversacion.

## Continuidad y fluidez

- El WebView no se pausa al minimizar.
- El renderizador conserva prioridad alta cuando deja de estar visible.
- La pagina se mantiene en memoria mientras el servicio sigue activo.
- La ventana nativa evita una animacion duplicada al colapsarse.
- Si la ventana nativa falla al crearse, se abre un panel compatible con un WebView limpio.

## Uso en Samsung

1. Instala y abre **BurbujaGPT V8**.
2. Selecciona **Burbuja nativa de Android**.
3. Pulsa **Configurar burbujas de Android / Samsung**.
4. Permite notificaciones y burbujas.
5. Comprueba **Ajustes > Notificaciones > Ajustes avanzados > Notificaciones flotantes > Burbujas**.
6. Vuelve a la aplicacion y pulsa **Activar globo**.

Si One UI publica la conversacion solo como una notificacion normal, V8 mostrara el globo compatible despues de unos segundos. El permiso **Aparecer encima** debe estar activo para ese respaldo.

## Modos

- **Burbuja nativa de Android:** el sistema reconoce una conversacion con `MessagingStyle`, `Person`, un acceso directo permanente y `BubbleMetadata`.
- **App oficial emergente:** usa la sesion y los chats de la aplicacion oficial cuando Samsung permite abrirla en ventana libre.
- **Navegador emergente:** usa la sesion existente de Brave, Chrome u otro navegador.
- **Panel web interno:** muestra `chatgpt.com` dentro de la ventana propia de BurbujaGPT.

## Limites

- Google bloquea OAuth dentro de WebView con `403: disallowed_useragent`. Para una cuenta creada solo con Google se debe usar el navegador o la app oficial.
- Android no permite incrustar la actividad privada de la aplicacion oficial de ChatGPT dentro de una burbuja de otra aplicacion.
- OpenAI no ofrece una API publica para importar el historial privado de ChatGPT; el panel usa el sitio oficial autenticado.
- El servicio mejora la continuidad, pero Android puede finalizar cualquier proceso bajo presion extrema de memoria, reinicio o ahorro de bateria agresivo.
- El proyecto es independiente y no esta afiliado con OpenAI.

## Compilar

En GitHub: **Actions > Build APK > Run workflow**. El artefacto se publica como `BurbujaGPT-V8-debug-apk`.

El proyecto utiliza `targetSdk 35` y `minSdk 23`.

## Cronómetro lateral v1.4

El módulo independiente `:stopwatch` genera la aplicación `com.leonardo.edgestopwatch`.

- La barra contraída añade marcas negras por cada intervalo completado. El intervalo puede ser de 5, 10, 20 minutos o un valor personalizado entre 1 y 720 minutos.
- El panel admite hasta seis temporizadores configurables. Cada uno se puede mostrar u ocultar, iniciar, pausar y reiniciar por separado.
- Un interruptor general desactiva todos los temporizadores sin eliminar su configuración.
- La escala global ajusta el panel, los controles, los temporizadores, la barra contraída y la zona táctil.
- El menú usa una interfaz más oscura y el icono representa el cronómetro junto con la barra lateral de intervalos.

Para compilarlo en GitHub: **Actions > Build Edge Stopwatch APK > Run workflow**. El artefacto se publica como `Cronometro-Lateral-v1.4-debug-apk`.

El artefacto automático usa una firma temporal de CI. El APK que se distribuya debe volver a firmarse con la clave estable externa descrita en [`stopwatch/SIGNING.md`](stopwatch/SIGNING.md); la clave privada no se guarda en el repositorio público.
