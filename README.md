# Rumbo · Bitácora

Aplicación móvil Android desarrollada en Java que permite consultar la ubicación del dispositivo, registrar lugares sobre Google Maps y asociar fotografías a esos lugares. La información de la bitácora se almacena localmente y utiliza mecanismos de cifrado respaldados por Android Keystore.

## Contenido

- [Descripción general](#descripción-general)
- [Funcionalidades](#funcionalidades)
- [Tecnologías y requisitos](#tecnologías-y-requisitos)
- [Configuración de Google Maps](#configuración-de-google-maps)
- [Instalación y ejecución](#instalación-y-ejecución)
- [Seguridad y almacenamiento](#seguridad-y-almacenamiento)
- [Pruebas](#pruebas)
- [Documentación y reportes](#documentación-y-reportes)
- [Limitaciones y aspectos pendientes](#limitaciones-y-aspectos-pendientes)

## Descripción general

Rumbo · Bitácora permite guardar lugares de interés en un mapa y asociarles información y fotografías. El usuario puede crear marcadores, editar sus datos y consultar los lugares registrados. La aplicación solicita los permisos necesarios durante el uso y mantiene los datos de la bitácora en el almacenamiento privado de la aplicación.

## Funcionalidades

- **Mapa interactivo:** visualización de Google Maps con un estilo personalizado.
- **Ubicación actual:** consulta de ubicación en vivo mediante Fused Location Provider.
- **Permisos durante el uso:** solicitud de permisos de ubicación y cámara. Si se deniegan, la aplicación informa al usuario y ofrece abrir los Ajustes.
- **Registro de lugares:** creación de marcadores al tocar el mapa, con opción para deshacer la acción.
- **Edición de lugares:** modificación del nombre y la nota desde el detalle del marcador.
- **Validación de datos:** control de longitud y limpieza de caracteres de control en los campos de texto.
- **Fotografías asociadas:** captura de fotografías para un lugar seleccionado o para uno creado en la ubicación actual.
- **Procesamiento de imágenes:** reducción de fotografías a un máximo de 1600 px y corrección de orientación EXIF.
- **Vista previa:** miniatura de la última fotografía en el panel inferior; al seleccionarla se abre el lugar asociado.
- **Eliminación:** borrado de un lugar individual o de todos los puntos y sus fotografías, con confirmación.
- **Continuidad de la cámara:** conservación del estado de una captura durante rotaciones y cuando Android recrea el proceso mientras la cámara está en uso.
- **Migración de información:** migración automática de datos de versiones anteriores al almacenamiento cifrado, según las condiciones descritas en la sección de seguridad.

## Tecnologías y requisitos

- Java.
- Android Studio.
- Android SDK 37.
- JDK 11 o el JDK integrado de Android Studio.
- Dispositivo o emulador Android compatible con `minSdk 30`.
- Google Play Services.
- Clave de Google Maps para Android.

## Configuración de Google Maps

La aplicación necesita una clave de API de Google Maps para Android.

1. Crear un proyecto en Google Cloud.
2. Habilitar **Maps SDK for Android**.
3. Crear una clave de API y restringirla para aplicaciones Android mediante el nombre de paquete y la huella SHA-1 correspondientes.
4. En la raíz del proyecto, junto a `settings.gradle.kts`, copiar `secrets.properties.example` con el nombre `secrets.properties`.
5. Editar `secrets.properties` y configurar la clave:

   ```properties
   MAPS_API_KEY=TU_CLAVE_DE_GOOGLE_MAPS
   ```

   También se admite proporcionar la clave mediante `-PMAPS_API_KEY=...` o la variable de entorno `MAPS_API_KEY`.

6. Sincronizar el proyecto con Gradle.

El archivo `secrets.properties` está excluido del repositorio mediante `.gitignore`; no debe publicarse ni compartirse. La clave de Maps se incorpora al APK para que el SDK pueda utilizarla, por lo que también debe mantenerse restringida desde Google Cloud. Guardarla fuera del repositorio evita exponerla en el código fuente, pero no impide que pueda extraerse del APK.

Si no hay una clave válida, el mapa puede mostrarse gris y la aplicación presenta un aviso al iniciar.

## Instalación y ejecución

1. Clonar o descargar el repositorio.
2. Abrir en Android Studio la carpeta raíz que contiene `settings.gradle.kts`.
3. Configurar `MAPS_API_KEY` siguiendo la sección anterior.
4. Esperar a que finalice la sincronización de Gradle.
5. Ejecutar la configuración `app` en un emulador o dispositivo Android compatible con Google Play Services.
6. Conceder los permisos de ubicación y cámara cuando la aplicación los solicite.
7. Comprobar el comportamiento de la aplicación tanto al conceder como al denegar los permisos.

## Seguridad y almacenamiento

La aplicación utiliza los siguientes mecanismos de protección, de acuerdo con la implementación documentada en el proyecto:

- **Bitácora cifrada:** los datos se guardan mediante `EncryptedSharedPreferences`, con una clave administrada por Android Keystore.
- **Fotografías cifradas:** las imágenes se almacenan en `files/photos_enc/` mediante `EncryptedFile` y cifrado AES-256-GCM, con una clave protegida por Android Keystore.
- **Archivos temporales de cámara:** durante la captura, la imagen pasa temporalmente por `cache/capture/` sin cifrar y se elimina después de completar el cifrado.
- **Migración desde versiones anteriores:** si existe `files/rumbo_places.json` o fotografías JPG antiguas sin cifrar, la aplicación intenta migrarlas al almacenamiento cifrado. Los originales se eliminan únicamente después de guardar correctamente la nueva copia.
- **Manejo de claves invalidadas:** si Android invalida la clave, el almacén cifrado se reinicia para evitar que la aplicación quede inutilizable.
- **Tráfico de red:** el manifiesto establece `usesCleartextTraffic="false"` y utiliza `network_security_config.xml` para bloquear tráfico HTTP en claro.
- **Copias de seguridad:** están deshabilitadas y existen reglas de exclusión para los datos de la aplicación.

La aplicación no utiliza SQL, no dispone de cuentas de usuario ni de un backend propio y no implementa autenticación. Por ello, los controles específicos de consultas SQL, autenticación de cuentas y protección de comunicaciones con un servidor no forman parte de la arquitectura actual.

## Pruebas

Para ejecutar las pruebas unitarias locales:

```bash
./gradlew testDebugUnitTest
```

Para compilar la variante de depuración:

```bash
./gradlew assembleDebug
```

En Windows se pueden ejecutar los mismos comandos desde PowerShell usando:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Las pruebas `PlaceStoreTest` cubren:

- Serialización de los datos.
- Migración del formato antiguo.
- Validación del nombre y la nota.
- Protección frente a rutas que contienen `..`.

La ejecución satisfactoria de estos comandos debe comprobarse en el entorno de desarrollo. No se debe considerar validada una función únicamente porque exista código o una prueba escrita para ella.

## Documentación y reportes

Los archivos de documentación incluidos en el repositorio son:

- [`vulnerabilities.md`](vulnerabilities.md): vulnerabilidades identificadas y sus recomendaciones.
- [`best_practices.md`](best_practices.md): buenas prácticas de seguridad aplicadas y recomendaciones.
- [`security_tips.md`](security_tips.md): recomendaciones de seguridad para el proyecto.
- [`security_improvement_program.md`](security_improvement_program.md): programa de mejora de seguridad.
- [`vulnerability_report.pdf`](vulnerability_report.pdf): reporte detallado de análisis.

## Limitaciones y aspectos pendientes

- Las pruebas TLS específicas de MobSF figuran como `Not Tested`; no se afirma que se haya implementado o verificado certificate pinning.
- El cifrado, la migración y el flujo de cámara deben validarse en un dispositivo o emulador, idealmente incluyendo datos provenientes de una instalación anterior.
- La optimización R8 del build release se encuentra desactivada (`optimization { enable = false }`). Activarla requiere revisar las reglas para Tink/security-crypto y probar el APK resultante.
- La clave de Google Maps debe configurarse localmente y mantenerse restringida. No debe incluirse en el repositorio.
- Los resultados de análisis de seguridad corresponden a los APK y fechas indicados en los reportes. No deben atribuirse a una compilación posterior sin volver a analizarla.

---

**Repositorio:** [EntregaFinal – GitHub](https://github.com/bastianjhoanmunozmunoz-cyber/EntregaFinal)
