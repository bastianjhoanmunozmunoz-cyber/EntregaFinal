# Buenas prácticas de seguridad (Best Practices)

**Proyecto:** Rumbo · Bitácora (`com.example.mapgps`)  
**Asignatura:** Programación Android · Actividad 2.5  
**Plataforma:** Android Studio, Java y Google Maps SDK

Este documento describe las medidas de seguridad incorporadas al proyecto, el riesgo que buscan reducir y sus limitaciones. Se distingue entre medidas presentes en el código y controles que todavía requieren pruebas en un dispositivo.

## 1. Protección de datos almacenados

### Problema
La versión inicial guardaba los lugares y las referencias a fotografías en `files/rumbo_places.json`, un archivo JSON legible dentro del almacenamiento privado de la aplicación. El almacenamiento privado reduce el acceso desde otras aplicaciones, pero no cifra por sí mismo el contenido.

### Implementación
La lectura y escritura de la bitácora se realiza mediante `EncryptedSharedPreferences` de AndroidX Security. Se crea una `MasterKey` con esquema AES-256-GCM; las claves se protegen con AES-256-SIV y los valores con AES-256-GCM. La aplicación guarda el JSON completo bajo la clave `rumbo_data`.

Al abrir una instalación que todavía contiene `rumbo_places.json`, la aplicación intenta leerlo, copiar su contenido al almacenamiento cifrado y eliminar el archivo antiguo después de guardar la copia cifrada.

**Archivos:** `app/src/main/java/com/example/mapgps/MainActivity.java`, `app/build.gradle.kts`.

### Cómo mejora la seguridad
El contenido de los lugares deja de almacenarse como texto JSON legible y queda protegido por claves gestionadas mediante Android Keystore.

### Fotografías
Las fotos se guardan cifradas con `EncryptedFile` (AES-256-GCM-HKDF-4KB, misma clave maestra del Keystore) en `files/photos_enc/`. La cámara escribe primero en `cache/capture/` mediante `FileProvider`; la app reduce y comprime la imagen, la cifra y borra el archivo temporal. Las fotos antiguas sin cifrar de `files/photos/` se migran y se eliminan tras guardar la copia cifrada.

### Limitaciones
Durante unos instantes la captura existe sin cifrar en el caché privado de la app. Si Android invalida la clave del Keystore (por ejemplo al restaurar el dispositivo), los datos cifrados no se pueden recuperar y el almacén se reinicia. La migración y el cifrado de fotos no se han probado todavía en un dispositivo.

## 2. Comunicación segura

### Problema
Una conexión HTTP sin cifrar puede permitir que alguien en la misma red intercepte o modifique el tráfico.

### Implementación
El proyecto incluye `app/src/main/res/xml/network_security_config.xml`, que establece `cleartextTrafficPermitted="false"` y confía en los certificados del sistema. El manifiesto enlaza esta configuración mediante `android:networkSecurityConfig` y declara `android:usesCleartextTraffic="false"`.

### Cómo mejora la seguridad
La configuración impide que la aplicación utilice conexiones HTTP en texto claro mediante las API de red compatibles con la política de seguridad de Android. Las comunicaciones del SDK de Google Maps observadas en el análisis dinámico utilizaron clases HTTPS/OkHttp.

### Limitaciones
El análisis dinámico de MobSF dejó las pruebas TLS específicas (cleartext, misconfiguración y pinning) como *Not Tested*. No se implementó certificate pinning ni se debe afirmar que fue probado. La configuración tampoco demuestra por sí sola que todos los servicios externos estén correctamente configurados.

**Archivos:** `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/network_security_config.xml`.

## 3. Protección de respaldos

### Problema
Una copia de seguridad podría trasladar datos personales de la aplicación fuera del dispositivo.

### Implementación
El manifiesto establece `android:allowBackup="false"`. Además, `backup_rules.xml` y `data_extraction_rules.xml` excluyen el archivo JSON antiguo, la carpeta de fotografías y las preferencias cifradas, tanto para respaldo en la nube como para transferencia entre dispositivos.

### Cómo mejora la seguridad
Se reduce la posibilidad de que las ubicaciones, las fotografías o las preferencias cifradas se incluyan en mecanismos de respaldo o transferencia compatibles.

### Limitaciones
El comportamiento exacto depende de la versión de Android y del mecanismo de respaldo del fabricante. Debe verificarse en los dispositivos objetivo; las reglas XML son una protección adicional y no sustituyen la configuración del manifiesto.

**Archivos:** `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml`.

## 4. Validación de entradas

Cada lugar tiene un nombre (máx. 40 caracteres) y una nota (máx. 200). `PlaceStore.sanitizeName` y `PlaceStore.sanitizeNote` eliminan caracteres de control, colapsan espacios y recortan la longitud antes de guardar; los campos de texto además limitan la longitud en la interfaz. Al leer los datos guardados se descartan coordenadas fuera de rango y nombres de archivo de foto con separadores de ruta o `..` (`PlaceStore.isSafeFileName`). Estas reglas tienen pruebas unitarias en `PlaceStoreTest`.

## Resumen

| Práctica | Estado en el proyecto | Evidencia principal |
|---|---|---|
| Cifrado de bitácora | Implementado en código; requiere prueba funcional y de migración | `MainActivity.java`, dependencia `security-crypto` |
| Bloqueo de tráfico HTTP | Implementado en configuración; pruebas TLS de MobSF pendientes | Manifiesto y `network_security_config.xml` |
| Exclusión de respaldos | Implementada en configuración; requiere comprobar en dispositivo | Manifiesto y reglas XML |
| Cifrado de fotografías | Implementado en código; falta probar en dispositivo | `SecureStorage.java` (`EncryptedFile`) |
| Validación de texto libre | Implementada (nombre y nota) y con pruebas unitarias | `PlaceStore.java`, `PlaceStoreTest.java` |

