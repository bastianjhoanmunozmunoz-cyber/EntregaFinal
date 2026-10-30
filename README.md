# Rumbo · Bitácora

Aplicación móvil Android desarrollada en Java que permite consultar la ubicación del dispositivo, registrar lugares de interés sobre Google Maps y asociar fotografías a cada lugar. La aplicación ofrece una bitácora personal de ubicaciones, con almacenamiento local y protección de la información mediante mecanismos de cifrado de Android.

## Descripción del proyecto

Rumbo · Bitácora está diseñada para guardar y organizar lugares visitados o de interés desde un mapa interactivo. El usuario puede crear marcadores, agregarles un nombre y una nota, tomar fotografías asociadas y consultar posteriormente la información registrada.

La aplicación utiliza los servicios de ubicación de Android y Google Maps para facilitar el registro de lugares, junto con almacenamiento privado para conservar la bitácora en el dispositivo.

## Funcionalidades principales

- **Mapa interactivo:** visualización de Google Maps con un estilo personalizado.
- **Ubicación actual:** consulta de la ubicación del dispositivo mediante Fused Location Provider.
- **Gestión de permisos:** solicitud de permisos de ubicación y cámara durante el uso de la aplicación.
- **Creación de lugares:** registro de marcadores al seleccionar una posición en el mapa.
- **Edición de información:** modificación del nombre y la nota asociados a cada lugar.
- **Validación de entradas:** validación de longitud y limpieza de caracteres de control en los campos de texto.
- **Fotografías:** captura y asociación de imágenes a los lugares guardados.
- **Optimización de imágenes:** reducción de fotografías a un máximo de 1600 px y corrección de orientación EXIF.
- **Vista previa:** visualización de una miniatura de la última fotografía en el panel inferior.
- **Eliminación de registros:** eliminación individual de lugares o eliminación de todos los puntos y sus fotografías, con confirmación.
- **Continuidad del flujo de cámara:** conservación del estado de captura durante rotaciones y recreaciones del proceso.
- **Migración de información:** migración de datos de versiones anteriores al almacenamiento cifrado.

## Tecnologías utilizadas

- Java
- Android Studio
- Android SDK 37
- AndroidX Security Crypto
- Android Keystore
- Google Maps SDK for Android
- Fused Location Provider
- Gradle

## Requisitos

- Android Studio.
- JDK 11 o JDK integrado de Android Studio.
- Android SDK 37.
- Dispositivo o emulador Android compatible con `minSdk 30`.
- Google Play Services.
- Clave de Google Maps para Android.

## Configuración de Google Maps

Para habilitar el mapa es necesario configurar una clave de Google Maps para Android.

1. Crear un proyecto en Google Cloud.
2. Habilitar **Maps SDK for Android**.
3. Crear una clave de API y restringirla para aplicaciones Android mediante el nombre de paquete y la huella SHA-1 correspondientes.
4. En la raíz del proyecto, junto a `settings.gradle.kts`, copiar el archivo `secrets.properties.example` y nombrar la copia `secrets.properties`.
5. Agregar la clave al archivo:

   ```properties
   MAPS_API_KEY=TU_CLAVE_DE_GOOGLE_MAPS
   ```

6. Sincronizar el proyecto con Gradle.

También se puede proporcionar la clave mediante `-PMAPS_API_KEY=...` o la variable de entorno `MAPS_API_KEY`.

El archivo `secrets.properties` se utiliza para mantener la clave fuera del repositorio. La clave debe contar además con restricciones configuradas desde Google Cloud Console.

## Instalación y ejecución

1. Clonar el repositorio o descargar el código fuente.
2. Abrir en Android Studio la carpeta raíz del proyecto, donde se encuentra `settings.gradle.kts`.
3. Configurar la clave de Google Maps siguiendo los pasos anteriores.
4. Esperar a que finalice la sincronización de Gradle.
5. Seleccionar la configuración `app`.
6. Ejecutar la aplicación en un dispositivo o emulador Android compatible.
7. Conceder los permisos de ubicación y cámara cuando sean solicitados.

## Seguridad de la aplicación

Rumbo · Bitácora incorpora medidas de seguridad para proteger la información almacenada y controlar el acceso a los recursos del dispositivo:

- **Almacenamiento cifrado:** la bitácora se guarda mediante `EncryptedSharedPreferences`.
- **Administración de claves:** las claves criptográficas son gestionadas mediante Android Keystore.
- **Fotografías protegidas:** las imágenes se almacenan cifradas mediante `EncryptedFile`, utilizando AES-256-GCM.
- **Migración segura:** los datos y fotografías de versiones anteriores se migran al almacenamiento cifrado; los archivos originales se eliminan después de completar correctamente el guardado.
- **Protección de red:** el tráfico HTTP en claro está deshabilitado mediante `usesCleartextTraffic="false"` y `network_security_config.xml`.
- **Protección de respaldos:** las copias de seguridad de los datos de la aplicación están deshabilitadas y se definen reglas de exclusión.
- **Validación de información:** los campos de nombre y nota se validan antes de almacenarse.
- **Archivos temporales:** las imágenes utilizadas durante la captura se procesan en caché y se eliminan después de completar el cifrado.

## Pruebas

El proyecto incluye pruebas unitarias para comprobar el comportamiento del almacenamiento y la validación de datos.

Para ejecutar las pruebas:

```bash
./gradlew testDebugUnitTest
```

Para compilar la versión de depuración:

```bash
./gradlew assembleDebug
```

En Windows PowerShell se pueden utilizar los comandos equivalentes:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Las pruebas `PlaceStoreTest` consideran:

- Serialización de datos.
- Migración del formato anterior.
- Validación de nombres y notas.
- Protección frente a rutas que contienen `..`.

## Documentación del proyecto

En el repositorio se incluyen documentos complementarios sobre el desarrollo y la seguridad de la aplicación:

- [`vulnerabilities.md`](vulnerabilities.md) – identificación y descripción de vulnerabilidades.
- [`best_practices.md`](best_practices.md) – buenas prácticas de seguridad.
- [`security_tips.md`](security_tips.md) – recomendaciones de seguridad.
- [`security_improvement_program.md`](security_improvement_program.md) – programa de mejora de seguridad.
- [`vulnerability_report.pdf`](vulnerability_report.pdf) – informe de análisis de seguridad.

## Repositorio

[EntregaFinal – Rumbo · Bitácora](https://github.com/bastianjhoanmunozmunoz-cyber/EntregaFinal)
