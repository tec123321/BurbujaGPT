# BurbujaGPT V3

Aplicación Android en Java que mantiene un globo arrastrable sobre otras apps. Al tocarlo abre `chatgpt.com` en una ventana flotante, usando la sesión real del usuario y no la API de OpenAI.

## Qué cambia frente a V2

- Elimina el campo de token y el cliente de la API.
- Muestra el sitio oficial de ChatGPT dentro de un WebView aislado.
- Conserva las cookies de esa ventana para mantener la sesión iniciada.
- Permite consultar el historial, buscar chats, abrir GPTs y crear conversaciones.
- Incluye selector de archivos, descargas y permiso de micrófono solicitado solo cuando la página lo necesita.
- El globo recuerda su posición y se ajusta al borde de la pantalla.
- La ventana puede minimizarse, recargarse o ampliarse.
- Incluye un acceso directo a la app oficial de ChatGPT.

## Cómo usar

1. Instala el APK.
2. Abre **BurbujaGPT V3**.
3. Pulsa **Permitir globo flotante** y activa **Aparecer encima**.
4. Vuelve y pulsa **Activar globo**.
5. Toca el globo de colores.
6. Inicia sesión en la página oficial de ChatGPT.

La sesión se guarda en el almacenamiento privado del WebView. El código no inyecta JavaScript, no lee contraseñas y no copia cookies desde Chrome ni desde la app oficial.

## Límites reales

- OpenAI no ofrece una API pública para leer el historial privado de ChatGPT. Esta versión lo muestra cargando el sitio oficial autenticado.
- La sesión del WebView es independiente de Chrome y de la app oficial; hay que iniciar sesión una vez dentro de la ventana.
- Google, Microsoft o Apple pueden bloquear el inicio de sesión en navegadores incrustados. Si ocurre, añade una contraseña a tu cuenta desde **ChatGPT web > Configuración > Cuenta** y entra mediante correo y contraseña.
- OpenAI puede cambiar el sitio o impedir su ejecución en WebView. En ese caso, el botón **Abrir la app oficial** seguirá disponible, pero no podrá conservarse la interfaz web dentro del globo hasta adaptar la app.
- La aplicación no puede incrustar directamente la actividad privada de la app oficial ni reutilizar sus cookies: Android aísla los datos de cada aplicación.
- Es un proyecto independiente, no oficial y no afiliado con OpenAI.

## Compilar el APK

### GitHub Actions

1. Abre la pestaña **Actions** del repositorio.
2. Entra en **Build APK**.
3. Ejecuta **Run workflow**.
4. Descarga el artefacto `BurbujaGPT-V3-debug-apk`.
5. Extrae e instala `app-debug.apk`.

### Android Studio

1. Abre el proyecto en Android Studio.
2. Espera la sincronización de Gradle.
3. Usa **Build > Build APK(s)**.

## Samsung

Para evitar que One UI cierre el globo:

1. Ve a **Ajustes > Aplicaciones > BurbujaGPT > Batería**.
2. Selecciona **Sin restricciones**.
3. Mantén habilitado **Aparecer encima**.

El proyecto apunta a Android 15 (`targetSdk 35`) y funciona desde Android 6 (`minSdk 23`).
