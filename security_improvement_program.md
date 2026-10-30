# Programa de Mejora de Seguridad

**Proyecto:** Rumbo · Bitácora (`com.example.mapgps`)  
**Plataforma:** Android Studio, Java y Google Maps SDK  
**Versión del documento:** 1.2  
**Fecha:** 30/10/2026

## 1. Objetivo

Establecer una forma ordenada de revisar la seguridad de Rumbo · Bitácora, corregir problemas y comprobar que las mejoras funcionen. La aplicación utiliza ubicación, cámara, fotografías y almacenamiento local, por lo que debe proteger la privacidad y evitar configuraciones inseguras.

## 2. Línea base de seguridad

El análisis estático de MobSF del APK original, realizado el 27/10/2026, obtuvo 44/100 (Medium Risk, grado B), con dos alertas High y dos advertencias. Un reescaneo posterior, registrado en el informe técnico para el 29/10/2026, obtuvo 69/100 (Low Risk, grado A), aunque mantuvo las alertas de configuración indicadas en ese informe. Los puntajes corresponden a APK y ejecuciones diferentes; no demuestran por sí solos que todos los problemas estén corregidos.

El análisis dinámico se realizó en un emulador Pixel 5 con Android 11/API 30. Se probaron el inicio, el permiso de ubicación, el mapa, un marcador y la captura de una fotografía. Las pruebas TLS de MobSF quedaron como *Not Tested*, por lo que siguen pendientes.

## 3. Proceso de revisión

1. Revisar permisos, manifiesto, componentes exportados y configuración de red.
2. Generar un APK de prueba e identificar su hash para mantener trazabilidad.
3. Ejecutar análisis estático con MobSF y registrar puntaje, alertas y evidencia.
4. Ejecutar análisis dinámico en un emulador controlado y recorrer las funciones principales.
5. Probar permisos concedidos y rechazados, guardado de datos, fotografías y conectividad.
6. Corregir los problemas según su severidad.
7. Repetir los análisis después de los cambios y comparar los resultados.
8. Registrar fecha, responsable, evidencia, estado y resultado de cada revisión.

Se recomienda realizar una revisión general cada tres meses y otra después de cambios importantes en permisos, almacenamiento, red o dependencias.

## 4. Métricas de seguimiento

| Métrica | Objetivo | Línea base / estado conocido | Estado |
|---|---|---|---|
| Puntaje MobSF estático | Mejorar respecto de la línea base y revisar alertas | 44/100 original; reescaneo posterior 69/100 | Repetir con el APK actual |
| Alertas High | 0 antes de distribuir | El reporte original indicó 2; el reescaneo conservó alertas de configuración | Pendiente de verificar en APK final |
| Tráfico HTTP en claro | 0 | No se observó en el recorrido; pruebas TLS específicas sin ejecutar | Pendiente de prueba TLS |
| Datos de bitácora cifrados | Lectura y escritura mediante almacenamiento cifrado | Integración realizada en código | Probar guardado, lectura y migración |
| Fotografías cifradas | 100% si contienen información personal | Cifradas con `EncryptedFile` en `files/photos_enc/` | Integrado en código; probar en dispositivo |
| Datos incluidos en respaldos | 0 datos sensibles | Backup deshabilitado y reglas de exclusión configuradas | Verificar en dispositivo |
| Permisos sensibles | Solo los necesarios y respetando el rechazo | Ubicación y cámara declaradas | Probar concesión y rechazo |
| Dependencias | Sin vulnerabilidades conocidas relevantes | Revisión completa pendiente | Pendiente |

## 5. Mejoras incorporadas al proyecto

| Mejora | Archivos principales | Estado |
|---|---|---|
| Guardar la bitácora mediante `EncryptedSharedPreferences` y Android Keystore | `MainActivity.java`, `app/build.gradle.kts` | Integrada en código; falta prueba funcional y de migración |
| Migrar el JSON antiguo al almacenamiento cifrado y eliminarlo tras guardar la copia | `MainActivity.java` | Integrada en código; falta probar con datos antiguos |
| Bloquear tráfico HTTP en texto claro | `AndroidManifest.xml`, `network_security_config.xml` | Configurada; pruebas TLS pendientes |
| Deshabilitar copias de seguridad y definir exclusiones | `AndroidManifest.xml`, `backup_rules.xml`, `data_extraction_rules.xml` | Configurada; falta verificar comportamiento en dispositivo |
| Compartir con la cámara mediante `FileProvider` no exportado, solo la carpeta temporal `cache/capture/` | `AndroidManifest.xml`, `file_paths.xml` | Presente en el proyecto |
| Cifrar las fotografías (`EncryptedFile`) y migrar las antiguas | `SecureStorage.java` | Integrada en código; falta prueba funcional |
| Validar nombre y nota de cada lugar | `PlaceStore.java`, `PlaceStoreTest.java` | Implementada y con pruebas unitarias |
| Mantener la clave de Maps fuera del repositorio | `app/build.gradle.kts`, `secrets.properties`, `.gitignore` | Configurado; se recomienda restringir la clave en Google Cloud |

## 6. Problemas y mejoras pendientes

- Generar y revisar un APK release firmado con un certificado de producción y confirmar `debuggable=false` en el APK final.
- Verificar las alertas de configuración del certificado y del manifiesto que reportó MobSF.
- Confirmar en el APK final que `ProfileInstallReceiver` ya no aparece (se elimina del manifiesto con `tools:node="remove"`).
- Ejecutar las pruebas TLS de MobSF y ampliar los recorridos de red.
- Probar la migración de `rumbo_places.json` al almacenamiento cifrado en una instalación con datos previos.
- Probar en dispositivo el cifrado de las fotografías y la migración de las antiguas.
- Probar que los datos sensibles no se incluyan en respaldos ni transferencias.
- Revisar las dependencias y sus versiones.

## 7. Plan de acción

| Prioridad | Acción | Plazo sugerido | Criterio de verificación |
|---|---|---|---|
| Alta | Compilar APK release y revisar certificado y `debuggable` | Antes de entregar/publicar | Inspección del manifiesto y firma del APK |
| Alta | Probar lectura, escritura y migración de la bitácora cifrada | Antes de entregar | Los datos se conservan y el JSON antiguo se elimina solo tras migrar correctamente |
| Alta | Probar permisos de ubicación y cámara, incluyendo rechazo | Primera semana | La app no se cierra ni usa funciones sin permiso |
| Media | Ejecutar pruebas TLS de MobSF | Primera semana | Reporte TLS adjunto con resultados reales |
| Media | Verificar backup y transferencia de datos | Primer mes | Comprobar que no se restauren los archivos excluidos |
| Media | Probar cifrado de fotografías y migración de las antiguas | Primer mes | Foto tomada, mostrada tras reiniciar la app y sin copia sin cifrar en `files/` |
| Media | Revisar `ProfileInstallReceiver` y dependencias | Primer mes | Hallazgos documentados y correcciones justificadas |
| Continua | Repetir revisión de seguridad | Cada tres meses y tras cambios importantes | Nuevo reporte comparado con la línea base |

## 8. Seguimiento

Cada revisión debe registrar la fecha, versión del APK, hash, herramienta y versión utilizada, entorno de prueba, hallazgos, prioridad, responsable, acción correctiva y evidencia. Un problema solo se marca como verificado cuando existe una prueba que lo respalde.

## 9. Conclusión

El proyecto incorpora medidas de protección para el almacenamiento local, las comunicaciones y los respaldos. También mantiene controles existentes para permisos, fotografías y la clave de Google Maps. Aun así, el cifrado y la migración deben probarse en ejecución, el cifrado de fotografías es nuevo y tampoco se ha probado en ejecución, y las pruebas TLS continúan pendientes. El programa permite dar seguimiento a esos puntos sin presentar como resueltas medidas que todavía no se han comprobado.
