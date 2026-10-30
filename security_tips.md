# Security Tips

**Proyecto:** Rumbo · Bitácora (`com.example.mapgps`)  
**Plataforma:** Android Studio, Java y Google Maps SDK

Este documento reúne consejos de seguridad relacionados con la aplicación. Se indica cuándo una medida existe en el proyecto y cuándo un tema no aplica a su arquitectura actual.

## 1. Evitar ataques de intermediario (MITM)

**Riesgo:** una conexión HTTP sin cifrar puede exponer o permitir modificar la información transmitida.

**Medida en el proyecto:** `network_security_config.xml` bloquea el tráfico en texto claro. El manifiesto enlaza esa configuración y declara `usesCleartextTraffic="false"`.

**Beneficio:** se evita que la aplicación utilice HTTP sin cifrar mediante las API de red que respetan la configuración de Android.

**Pendiente:** MobSF dejó sus pruebas TLS específicas como *Not Tested*. No se implementó certificate pinning. Por tanto, no se afirma que la protección TLS haya sido validada de extremo a extremo.

## 2. Solicitar permisos solo cuando se necesitan

**Riesgo:** los permisos de ubicación y cámara dan acceso a información y funciones sensibles del dispositivo.

**Medida en el proyecto:** la aplicación declara permisos de ubicación precisa, ubicación aproximada y cámara. `MainActivity.java` comprueba los permisos antes de activar la ubicación o abrir la cámara y utiliza solicitudes de permiso en tiempo de ejecución.

**Beneficio:** Android solicita el consentimiento del usuario y la aplicación puede comprobar si el permiso fue concedido antes de utilizar la función.

**Prueba recomendada:** ejecutar la aplicación, rechazar cada permiso y comprobar que no se cierre ni intente utilizar la función protegida.

## 3. Compartir fotografías mediante FileProvider

**Riesgo:** compartir rutas de archivos directamente o exponer un proveedor puede permitir accesos no deseados a fotografías.

**Medida en el proyecto:** el manifiesto declara `FileProvider` con `android:exported="false"` y `android:grantUriPermissions="true"`. `file_paths.xml` limita las rutas compartidas a la carpeta temporal `cache/capture/`; la foto definitiva se cifra y se guarda fuera de ella.

**Beneficio:** la cámara recibe un URI `content://` con permiso temporal, en lugar de una ruta de archivo accesible directamente.

## 4. Proteger la clave de Google Maps

**Riesgo:** subir una clave de API al repositorio permite que terceros la copien y consuman la cuota del proyecto.

**Medida en el proyecto:** Gradle lee `MAPS_API_KEY` desde `secrets.properties` y el archivo está excluido mediante `.gitignore`.

**Beneficio:** la clave local no se publica accidentalmente junto al código fuente.

**Importante:** la clave utilizada por Maps para Android queda incorporada en el APK. Por eso también debe restringirse en Google Cloud por nombre de paquete y huella SHA-1. Mantenerla fuera de Git no reemplaza esas restricciones.

## 5. Protección contra inyección SQL: no aplica

La aplicación no utiliza SQLite, Room ni consultas SQL. Los lugares se guardan como JSON local y no se construyen sentencias SQL con datos del usuario. Por eso, la inyección SQL no es una superficie de ataque presente en esta versión. Si se incorpora una base de datos en el futuro, se deberán usar consultas parametrizadas y evitar concatenar entradas en sentencias.

## 6. Autenticación y autorización: no implementadas

La aplicación no tiene cuentas, inicio de sesión ni un servidor propio. Por lo tanto, no existe actualmente un flujo de autenticación o autorización que implementar. Si en el futuro se agrega un backend, se deberá autenticar a los usuarios con un mecanismo estándar y validar los permisos de cada operación en el servidor, no solo en la interfaz Android.

## Resumen

| Consejo | Estado | Próximo paso |
|---|---|---|
| Bloquear HTTP y reducir riesgo MITM | Configuración implementada | Ejecutar pruebas TLS y ampliar recorrido dinámico |
| Solicitar permisos en tiempo de ejecución | Presente en el proyecto | Probar rechazo y revocación de permisos |
| Compartir fotos con FileProvider | Presente en el proyecto | Comprobar acceso temporal desde la cámara |
| Mantener la clave fuera de Git | Configurado | Restringir la clave en Google Cloud |
| Evitar inyección SQL | No aplica: no hay base SQL | Revisar si se agrega una base de datos |
| Autenticación y autorización | No aplica: no hay cuentas ni backend | Diseñar controles si se agrega un servidor |

