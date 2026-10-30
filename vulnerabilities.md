# Informe de vulnerabilidades — Rumbo · Bitácora

**Paquete:** `com.example.mapgps`  
**APK analizado originalmente:** `app-debug.apk` (15,01 MB)  
**MD5:** `50938cedc415a82a7e03b39c204c51e8`  
**SHA-256:** `9986aa1d08d05cbdb35a7ad4162c866decc8d8a74c0c89b0fa4e984b83ee51ad`  
**Herramientas:** MobSF 4.6.1 (estático) y MobSF 4.6.2 (dinámico)  
**Fecha:** 27/10/2026

## Resumen

El análisis estático del APK original obtuvo **44/100 (Medium Risk, grado B)** y reportó dos alertas High y dos advertencias de configuración. El análisis dinámico recorrió funciones básicas en un emulador Pixel 5 con Android 11/API 30. No confirmó vulnerabilidades nuevas durante ese recorrido. Las pruebas TLS de MobSF quedaron como **Not Tested**, por lo que no se concluye que la configuración TLS o el pinning hayan sido validados.

Un reescaneo posterior del 29/10/2026 se registró en el informe técnico con 69/100 (Low Risk, grado A), pero mantuvo alertas de configuración. Los resultados corresponden a los APK identificados en esos informes y no son un reescaneo de los cambios incluidos en esta entrega.

## Hallazgos del análisis estático

| # | Hallazgo | Resultado MobSF | Riesgo / acción |
|---|---|---|---|
| 1 | Firma con certificado Android Debug | High | No distribuir APK de depuración. Generar y revisar APK release firmado con certificado de producción. |
| 2 | `android:debuggable=true` en APK debug | High | Confirmar `debuggable=false` en el APK release final. |
| 3 | `android:allowBackup=true` en APK original | Warning | Definir política de respaldo y proteger datos personales. La versión de trabajo deshabilita backup y añade reglas de exclusión; falta reanalizar el APK resultante. |
| 4 | `ProfileInstallReceiver` exportado y protegido con `android.permission.DUMP` | Warning | Revisar el nivel de protección y si el componente es necesario; no se ha confirmado explotación. |
| 5 | Permisos de ubicación y cámara | Permisos sensibles | Son coherentes con las funciones de la aplicación y no constituyen vulnerabilidad por sí solos. Probar concesión y rechazo. |
| 6 | Cadena hexadecimal de alta entropía en DEX | Posible secreto, no verificado | Rastrear su origen. No rotar salvo que se confirme que es una credencial. |
| 7 | Ubicación y referencias de fotos serializadas | Observación de privacidad | El APK original guardaba datos en JSON local. La versión de trabajo incorpora almacenamiento cifrado; falta probar migración y volver a analizar. |

## Análisis dinámico

**Entorno:** MobSF 4.6.2 local, instrumentación Frida, emulador Pixel 5, Android 11/API 30.  
**Recorrido:** inicio, permiso de ubicación, mapa, marcador, apertura de cámara y captura de una fotografía de prueba.

| Categoría | Resultado observado | Interpretación |
|---|---|---|
| Red | 56 eventos `getInputStream`; evidencia con clases HTTPS/OkHttp de Google Maps | No se confirmó tráfico HTTP claro durante el recorrido. |
| Dominios | 14 entradas clasificadas como `good` | Resultado de reputación de esa ejecución, no auditoría completa. |
| Trackers | Sin trackers detectados en la tabla | No demuestra ausencia absoluta de rastreadores. |
| File I/O | 97 eventos | Se observaron operaciones de bibliotecas y escritura privada. |
| TLS | Cleartext, misconfiguración TLS y pinning: `Not Tested` | No se puede concluir sobre esos controles. |

## Cambios de seguridad incluidos en la versión de trabajo

Los cambios siguientes se incorporaron al código fuente después de los análisis originales. **No deben confundirse con resultados de MobSF ya verificados:**

- La bitácora se lee y escribe mediante `EncryptedSharedPreferences` y Android Keystore.
- Se intenta migrar el archivo JSON antiguo al almacenamiento cifrado y eliminarlo solo si la escritura cifrada termina correctamente.
- El manifiesto bloquea tráfico HTTP en claro y enlaza `network_security_config.xml`.
- Se deshabilitan los respaldos y se agregan exclusiones para archivos y preferencias.

Las fotografías ahora se guardan cifradas (`EncryptedFile`) y las antiguas se migran; esto tampoco está verificado todavía en ejecución. La aplicación no tiene SQL, cuentas ni backend, por lo que inyección SQL y autenticación/autorización no aplican a la arquitectura actual.

## Acciones pendientes

1. Compilar APK release y revisar firma y `debuggable`.
2. Probar guardado, lectura y migración de datos cifrados en emulador.
3. Probar permisos concedidos y rechazados.
4. Ejecutar las pruebas TLS de MobSF.
5. Verificar la exclusión de respaldos en el dispositivo objetivo.
6. Evaluar cifrado de fotografías.
7. Revisar `ProfileInstallReceiver`, la cadena hexadecimal y las dependencias.
8. Ejecutar un nuevo análisis estático y dinámico sobre el APK actualizado y guardar sus hashes y resultados.

**Alcance:** los resultados originales corresponden al APK, emulador, sesión y recorrido descritos. No representan una auditoría completa ni un análisis del APK actualizado.
