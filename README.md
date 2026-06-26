# BurbujaGPT

App Android mínima en Java: muestra una burbuja flotante sobre otras apps. Al tocarla abre un panel para escribir/copiar texto y abrir ChatGPT, Gemini o WhatsApp.

## Qué hace

- Burbuja flotante movible.
- Panel flotante con campo de texto.
- Copia el texto al portapapeles.
- Abre ChatGPT oficial si está instalado; si no, abre chatgpt.com.
- Abre Gemini oficial si está instalado; si no, abre gemini.google.com.
- Abre WhatsApp.

## Qué no hace

- No incrusta ChatGPT/Gemini/WhatsApp dentro de la burbuja. Android no permite hacerlo de forma estable para apps externas.
- No usa API key.
- No lee la pantalla ni mensajes privados.

## Cómo compilar APK

### Desde GitHub Actions

1. Entra al repositorio.
2. Ve a **Actions**.
3. Abre **Build APK**.
4. Pulsa **Run workflow**.
5. Cuando termine, descarga el artefacto **BurbujaGPT-debug-apk**.
6. Dentro estará `app-debug.apk`.

### Desde Android Studio

1. Instala Android Studio.
2. Abre esta carpeta como proyecto.
3. Espera a que sincronice Gradle.
4. Ve a **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Instala el APK en tu Samsung.
6. Abre BurbujaGPT.
7. Da permiso: “Aparecer encima” / “Mostrar sobre otras apps”.
8. Pulsa “Activar burbuja”.

## Configuración recomendada Samsung

Ajustes > Aplicaciones > Acceso especial > Aparecer encima > BurbujaGPT > Permitir.

Si el sistema cierra la burbuja:

Ajustes > Batería > Límites de uso en segundo plano > Apps nunca suspendidas > añade BurbujaGPT.
