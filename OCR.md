# OCR automático: operación y límites

El sistema utiliza **RapidOCR 3.9.2**, PP-OCRv5 latino y ONNX Runtime en CPU. Los documentos se procesan localmente, sin servicios OCR externos ni claves de API. La imagen descarga y valida los modelos durante su construcción.

Para instalar todo el sistema sigue [README.md](README.md); para actualizar conservando información, [ACTUALIZACION_BD_DOCKER.md](ACTUALIZACION_BD_DOCKER.md). Esta guía describe el código actual, no certifica el estado de un equipo concreto.

## Flujo visible

1. Al guardar, los PDF permanecen separados. Las imágenes PNG/JPEG/TIFF forman `imagenes.pdf` en el orden elegido; cada TIFF mantiene el orden interno de sus páginas. No se ordena por nombre.
2. La conversión de imágenes necesita el servicio disponible; ocurre antes de guardar. El OCR comienza después de confirmar la carga.
3. La cola PostgreSQL conserva documentos pendientes. La interfaz permite consultar fase y páginas procesadas.
4. Un único worker OCR reconoce una página a la vez y guarda un punto de control. Tras una interrupción retoma páginas pendientes sin repetir las ya confirmadas, si sigue disponible el trabajo y su configuración.
5. El PDF reconocido conserva el escaneo y añade una capa Unicode invisible, seleccionable y buscable. No permite editar texto.
6. El backend publica el resultado, actualiza el texto para búsqueda y confirma la recepción al servicio.

Los originales se conservan byte por byte. El PDF OCR es un derivado: su hash no sustituye al original. Para entradas PDF no se reconstruye cada página como imagen; se conserva el contenido original y se superpone texto.

Un error de OCR no exige volver a subir el documento. La acción de reintento genera un nuevo intento cuando corresponde; no duplica un documento que sigue pendiente o procesándose.

## Comprobar que está listo

Desde `archivo_backend`:

```powershell
docker compose ps
Invoke-RestMethod http://localhost:8091/health
Invoke-RestMethod http://localhost:8091/v2/jobs/config
```

Busca **`jobsReady: true`** y una configuración sin 404. Salud HTTP200 por sí sola no demuestra que un documento haya sido procesado.

Si recibes404 en la configuración, reconstruye la imagen actual con `docker compose up -d --build ocr`, espera salud y reinicia el backend actualizado. No elimines volúmenes. Si usas `OCR_HOST_PORT`, adapta las URLs y `OCR_URL` en Java.

## Estados y recuperación

| Estado del documento | Significado |
|---|---|
| `PENDIENTE` | Espera en PostgreSQL |
| `PROCESANDO` | Trabajo reclamado, con fase y páginas |
| `COMPLETADO` | Texto reconocido con confianza suficiente |
| `REQUIERE_REVISION` | Poco texto o confianza baja; el PDF puede consultarse sin aprobación manual obligatoria |
| `ERROR` | Fallo terminal o reintentos agotados; requiere decisión explícita |

Java reclama un documento cada vez con una lease y un heartbeat. Los trabajos interrumpidos pueden reclamarse al caducar la lease; no se reinician leases activas. Los errores transitorios tienen hasta cinco reintentos automáticos con espera creciente. Un trabajo remoto caducado no se reconoce otra vez silenciosamente.

El servicio guarda trabajos y avances en el volumen **`archivo_ocr_artifacts`**. Conserva resultados terminales durante una hora, salvo confirmación de recepción o limpieza aplicable. No cambies modelos, DPI ni hilos con trabajos pendientes: su huella debe coincidir para reutilizar puntos de control. No existe una garantía de recuperación después de borrar el volumen o caducar el trabajo.

## Transporte y almacenamiento

El backend recibe metadatos pequeños y descarga el PDF **binario**, no una cadena Base64 gigante en JSON. Valida tipo, firma, tamaño y SHA-256 antes de publicar atómicamente. Un fallo transitorio de descarga puede reintentar el mismo artefacto sin repetir el reconocimiento. Un resultado útil anterior se conserva hasta confirmar la actualización.

| Límite | Valor inicial o máximo |
|---|---:|
| Archivos por carga en la interfaz | 100 |
| Archivo original | 20 MB |
| Lote en la interfaz | 150 MB |
| Imágenes juntas y PDF combinado | 20 MB |
| Páginas OCR | 100 |
| Píxeles acumulados | 250 millones |
| Metadatos/puntos de control por trabajo | 16 MiB |
| PDF OCR derivado | 128 MiB |
| Caché compartida de trabajos y artefactos | 512 MiB |
| Trabajos admitidos | Hasta 8, sujetos al espacio disponible |
| Artefactos/reservas combinados | Hasta 32 |
| Retención terminal | 1 hora |

Se respetan límites configurados más bajos. El máximo de páginas no garantiza que cualquier documento de 100 páginas quepa: también cuentan píxeles y bytes. Con reservas máximas de entrada/salida caben aproximadamente tres trabajos completos; la cola no es ilimitada. La admisión puede rechazar temporalmente por capacidad en vez de borrar resultados válidos.

La memoria no es completamente constante: se libera la imagen de cada página, pero permanecen modelos, estructuras del PDF y metadatos.

## Configuración

| Dónde | Claves |
|---|---|
| Terminal Java | `OCR_ENABLED`, `OCR_URL`, `OCR_TIMEOUT_SECONDS`, `OCR_REVIEW_THRESHOLD`, `OCR_ARTIFACT_MAX_BYTES`, `OCR_POLL_MILLIS`, `OCR_LEASE_SECONDS` |
| Servicio Compose | `OCR_MAX_BYTES`, `OCR_MAX_PAGES`, `OCR_PDF_DPI`, `OCR_ORT_INTRA_THREADS`, `OCR_ORT_INTER_THREADS`, `OCR_ARTIFACT_DIR`, `OCR_ARTIFACT_MAX_BYTES`, `OCR_ARTIFACT_TOTAL_BYTES`, `OCR_ARTIFACT_MAX_COUNT`, `OCR_ARTIFACT_TTL_SECONDS` |
| Sustitución Compose | `OCR_HOST_PORT` |

La configuración actual usa **220 DPI, intra 2/inter 1 y un motor/un trabajo a la vez**, sin GPU ni omisión automática por encontrar texto. Los hilos admiten 1 o 2. Cambiar un `.env` Compose no cambia las variables del proceso Java.

No aumentes motores, páginas o concurrencia sin medir memoria y tiempo con un corpus representativo. El timeout es de comunicación, no una promesa de tiempo total por documento.

## Rendimiento medido

Pruebas sintéticas locales aisladas, límite de 2 CPU/3 GiB, páginas JPEG de 1000×1400 y 220 DPI. La referencia reconstruía el fondo; el candidato conserva el PDF y añade texto. Medias de dos ejecuciones con un hilo en ambas variantes:

| Páginas | Referencia | PDF conservado | Tamaño referencia / conservado |
|---|---:|---:|---:|
| 1 | 1,57 s | 1,27 s | 853629 / 117683 bytes |
| 10 | 15,62 s | 12,27 s | 8333026 / 979728 bytes |
| 40 | 62,77 s | 48,22 s | 33235908 / 3850618 bytes |

Con dos hilos, 10 páginas promediaron 9,22 s en dos muestras; una muestra de 40 páginas tardó 35,77 s. Esto orientó la configuración CPU, **no garantiza ese rendimiento en expedientes reales** ni incluye toda la cadena HTTP/Java/base/interfaz. No se midieron percentiles ni el máximo de píxeles. Los resultados sintéticos conservaron orden, texto esperado y apariencia visible.

Las pruebas se pueden ampliar con `ocr_service/benchmark_ocr.py` en un entorno aislado; no uses documentos privados ni el servicio compartido para experimentar sin autorización.

## API y privacidad

- `/v2/jobs/config`: huella y configuración.
- `/v2/jobs/{id}`: creación, estado y cancelación; capacidad privada del trabajo.
- `/v2/jobs/{id}/result`: resultado y confirmación de recepción.
- `/v2/artifacts/{id}`: descarga binaria protegida por capacidad; los artefactos de trabajos durables reutilizan la capacidad del trabajo.
- Las rutas antiguas `/ocr`, `/pdf` y `/images/pdf` se mantienen para compatibilidad; no son la vía de recuperación del cliente actual.

No publiques capacidades ni accedas directamente al OCR desde redes externas. Su puerto local no reemplaza una política de acceso para producción.

El backend exige autenticación y registra auditoría en las descargas: `/api/documentos-digitales/{id}/archivo`, `/pdf` y `/originales/{orden}`. La consulta `/originales` lista metadatos y requiere autenticación; no es una descarga de archivo. Los documentos históricos sin manifiesto tienen una lista de originales vacía. Las rutas privadas y capacidades no se exponen en la respuesta pública.

## Problema de selector Java en Windows

Si Tomcat falla con `Unable to establish loopback connection` o `Invalid argument: connect`, comprueba primero JDK17 y puertos. En un equipo se observó un problema del selector de Java 21/24; no era un fallo OCR.

Solo para ese diagnóstico, se probó una propiedad de JVM temporal que fuerza la alternativa TCP cuando el directorio de sockets Unix no existe. Desde el repositorio:

```powershell
$selectorPath = Join-Path (Get-Location).Path '.selector-path-must-not-exist'
if (Test-Path -LiteralPath $selectorPath) { throw 'La ruta debe permanecer inexistente. No continuar con esta alternativa.' }
$jvmArgument = '-Djdk.net.unixdomain.tmpdir="' + $selectorPath + '"'
.\mvnw.cmd "-Dspring-boot.run.jvmArguments=$jvmArgument" spring-boot:run
```

**No crees esa carpeta.** La propiedad solo afecta esa ejecución; no cambies seguridad del sistema ni variables persistentes. Es una alternativa específica, no una exigencia normal de instalación ni una garantía para todos los equipos. Si vuelve a fallar, conserva el error y solicita diagnóstico; no pruebes lanzadores para evadir restricciones del entorno.

## Incidentes anteriores y límites de diagnóstico

- Una imagen antigua utilizaba una operación no soportada por pypdfium2; reconstruir OCR incorporó el cierre explícito de recursos.
- Se reprodujo sintéticamente un fallo al interpretar un PDF Base64 enorme; el transporte binario evita esa cadena. No se conservó la excepción del documento original, por lo que esa causa exacta no quedó confirmada para aquel caso.
- Los registros muestran códigos, tamaños y duración, no deben incluir texto reconocido, rutas privadas ni capacidades. Redáctalos antes de compartir.

Consulta el [seguimiento técnico](odd/tasks/ocr-scalability.md) para evidencia histórica; sus estados describen cada etapa de desarrollo y no sustituyen la verificación del despliegue actual.
