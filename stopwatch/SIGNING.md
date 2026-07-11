# Firma de Cronómetro lateral

El APK distribuible se firma fuera del repositorio con la clave privada estable `Cronometro_Lateral_signing_v1.p12`. La clave y su contraseña no deben publicarse en este repositorio.

- Paquete: `com.leonardo.edgestopwatch`
- Alias: `cronometro-lateral`
- SHA-256 del certificado: `2F:AF:AE:69:41:06:B1:DE:B8:63:0D:E3:F0:68:7E:72:E4:41:1A:34:C1:85:A8:5F:89:56:50:06:27:71:9B:76`

El artefacto de GitHub Actions es una compilación de CI con una clave de depuración temporal. Antes de distribuir una versión, hay que volver a firmarla con la clave estable y comprobarla con `apksigner verify --verbose --print-certs`.

La v1.3 se firmó con otra clave temporal. Por eso el cambio desde v1.3 a v1.4 requiere una desinstalación única. Las versiones firmadas con la clave estable v1 sí podrán actualizarse entre sí.
