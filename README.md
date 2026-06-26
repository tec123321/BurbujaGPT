# BurbujaGPT

App Android mínima en Java: muestra una burbuja flotante sobre otras apps. La V2 permite escribir una pregunta y recibir una respuesta de GPT dentro del panel flotante usando la API de OpenAI.

## Qué hace

- Burbuja flotante movible.
- Panel flotante con campo para token API.
- Campo para escribir una pregunta.
- Botón **Enviar a GPT**.
- Respuesta dentro de la burbuja.
- Copiar pregunta o respuesta.
- Abrir ChatGPT oficial como opción secundaria.

## Límites

- No incrusta la app oficial de ChatGPT dentro de la burbuja. Android no permite hacerlo de forma estable para apps externas.
- Usa la API de OpenAI, no tu sesión de ChatGPT Plus.
- El token se guarda localmente en el celular mediante SharedPreferences. No lo pegues en chats ni lo publiques.
- No lee la pantalla ni mensajes privados.

## Cómo usar

1. Instala el APK.
2. Abre **BurbujaGPT**.
3. Pulsa **Dar permiso de burbuja**.
4. Activa **Aparecer encima**.
5. Vuelve a la app.
6. Pulsa **Activar burbuja**.
7. Toca la burbuja **GPT**.
8. Pega tu token API de OpenAI.
9. Pulsa **Guardar token**.
10. Escribe una pregunta.
11. Pulsa **Enviar a GPT**.

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

## Configuración recomendada Samsung

Ajustes > Aplicaciones > Acceso especial > Aparecer encima > BurbujaGPT > Permitir.

Si el sistema cierra la burbuja:

Ajustes > Batería > Límites de uso en segundo plano > Apps nunca suspendidas > añade BurbujaGPT.
