# OCR automático

El sistema reconoce texto localmente con **RapidOCR**, usando detección y clasificación de orientación de PaddleOCR y el reconocedor latino **PP-OCRv5** sobre ONNX Runtime. Es la misma familia tecnológica identificada en ImageTrans, integrada como un servicio independiente y reproducible.

## Funcionamiento

En una misma carga, las imágenes PNG/JPEG/TIFF se unen en `imagenes.pdf` en el orden recibido. Cada TIFF aporta sus fotogramas en su orden interno. Los PDF subidos permanecen separados; si se intercalan imágenes y PDF, el grupo ocupa la posición de la primera imagen. No se ordena por nombre.

Primero se convierte el lote de imágenes, sin OCR. Después de confirmar la transacción se encola el OCR automático del PDF combinado mediante el evento existente. La carga requiere que el servicio local OCR esté disponible para convertir las imágenes; si falla la conversión, se revierte el lote y se limpian sus archivos. La creación del expediente y su carga posterior siguen siendo operaciones independientes: un fallo de carga no elimina el expediente ya creado.

Los originales se conservan byte por byte, con nombre, tipo real detectado, tamaño, hash y posición en un manifiesto asociado al documento. `GET /api/documentos-digitales/{id}/originales` lista los metadatos; `GET /api/documentos-digitales/{id}/originales/{orden}` descarga una imagen (posición desde 1), requiere autenticación y registra auditoría. Las rutas internas no forman parte de esa lista. En documentos históricos sin manifiesto, la lista está vacía y `/archivo` mantiene su comportamiento original. Eliminar el documento elimina el PDF combinado, el PDF OCR y sus imágenes originales solo después de confirmar la eliminación en base de datos.

La descarga individual de las imágenes originales está disponible mediante esas API autenticadas. Este cambio no añade un nuevo control de descarga de originales en la interfaz.

Límites: 100 archivos por carga, 20 MB por archivo, 20 MB en total para las imágenes y para el PDF combinado, 100 páginas y 250 millones de píxeles. Los límites configurados más bajos también se respetan. El tipo MIME declarado por el navegador no decide el formato: el backend comprueba firmas y el conversor valida y decodifica el contenido de cada imagen.

Despliegue de este cambio: reconstruir solo OCR y reiniciar el backend actualizado. Flyway aplicará automáticamente `V12__documento_originales.sql`, que añade una columna de manifiesto nullable, sin convertir ni modificar documentos históricos. No aplicar la migración manualmente ni reiniciar la base de datos.

1. El backend valida los archivos y guarda cada PDF separado o el PDF combinado y sus imágenes originales; crea un registro por documento resultante.
2. La respuesta al usuario no espera al OCR.
3. Un worker envía el archivo al servicio `archivo-ocr`.
4. El servicio procesa todas las páginas y devuelve texto, confianza y cantidad de páginas.
5. Se genera un PDF que conserva las páginas escaneadas y añade una capa Unicode de texto seleccionable y buscable. El backend guarda el PDF derivado y la distribución de los fragmentos; el archivo original y su hash se conservan.
6. El backend guarda el resultado en `ocr_texto`; el trigger existente actualiza `ocr_tsv` para que el contenido pueda encontrarse desde Explorar.

## Transporte binario v2: preparado, pendiente de despliegue

El backend actualizado solicita `/v2/ocr`: recibe texto, distribución y una referencia pequeña del PDF, no una cadena Base64 del PDF completo. Después descarga el binario desde `/v2/artifacts/{id}` con una capacidad independiente en `X-Artifact-Capability`. No se aceptan direcciones de descarga proporcionadas por la respuesta ni redirecciones HTTP que puedan reenviar esa capacidad.

El archivo descargado se publica atómicamente solo después de validar tipo PDF, firma, tamaño declarado y real, y SHA-256. Los archivos parciales se limpian; un PDF anterior útil se conserva hasta que la actualización del documento se confirma. Si falla una transferencia por E/S, se intenta descargar el mismo artefacto una vez más sin ejecutar de nuevo el reconocimiento.

| Límite | Valor máximo |
|---|---:|
| Metadatos JSON | 16 MiB |
| PDF derivado individual | 128 MiB |
| Datos PDF en caché, incluidos parciales interrumpidos | 512 MiB |
| Artefactos o reservas en caché | 32 |
| Retención | 1 hora |

Los manifiestos privados añaden un máximo acotado de 4 KiB por artefacto. Los valores configurados más bajos se respetan. La cuota no expulsa descargas activas; cuando se agota, se rechaza la admisión en lugar de borrar un artefacto válido. El límite de originales y del PDF combinado de imágenes sigue siendo 20 MB; 128 MiB se aplica únicamente al PDF OCR derivado.

Al caducar, se deniegan nuevas descargas. La limpieza de artefactos caducados y parciales interrumpidos se realiza al admitir otro artefacto o reiniciar la caché; las descargas ya activas se conservan hasta terminar.

La caché utiliza el volumen independiente `archivo_ocr_artifacts`. Identificadores y capacidades son aleatorios e independientes; la comparación de capacidades es de tiempo constante y los manifiestos almacenan solamente su hash. No se siguen enlaces simbólicos ni rutas aportadas por el cliente. No borrar volúmenes para actualizar el servicio. El diseño actual conserva un único worker de servicio OCR; recuperación durable de trabajos y puntos de control por página corresponden a tareas posteriores, no a este transporte.

Los diagnósticos incluyen referencia, código, categoría de excepción, tamaños y duración, sin texto reconocido, nombres/rutas de documentos ni capacidades. No se aumentan globalmente los límites de Jackson. El HTTP 200 seguido de error de parseo observado es compatible con una cadena Base64 demasiado grande (reproducción sintética confirmada); no se conservó la excepción del documento original, por lo que su causa exacta todavía no está confirmada.

### Orden de activación y compatibilidad

1. Después de verificar y autorizar el despliegue, reconstruir únicamente OCR. Mantiene `/ocr`, `/pdf` y `/images/pdf` para clientes anteriores.
2. Reiniciar el backend actualizado con el procedimiento operativo de este equipo indicado abajo. El cliente nuevo requiere `/v2/ocr`; no degrada a una respuesta Base64 grande.
3. Verificar con un PDF sintético pequeño: metadatos acotados, descarga repetida idéntica y PDF con texto buscable. No reprocesar documentos históricos ni lanzar trabajos largos sin autorización.

Solo se probó el candidato en contenedores efímeros aislados y pruebas Java sin base de datos. El OCR/backend que ya están en ejecución no se actualizaron durante S1 ni S2. La recuperación durable de páginas y trabajos y la optimización medida siguen pendientes.

## PDF original y memoria por página: preparado, pendiente de despliegue

Para entradas PDF, el candidato añade solamente una capa invisible de texto Unicode: no vuelve a dibujar el escaneo. Conserva los flujos comprimidos de imágenes, el orden, las dimensiones, el recorte y la rotación de las páginas. Los bytes y el hash del original no cambian. La regeneración antigua mediante `/pdf` utiliza el mismo procedimiento; `/ocr` y `/images/pdf` siguen siendo compatibles.

Antes de renderizar, se comprueban los límites de 100 páginas y 250 millones de píxeles acumulados. El reconocimiento decodifica una página cada vez y libera su imagen, mapa de bits y página antes de continuar, también ante errores. Los fotogramas TIFF mantienen su orden. Las entradas de imagen siguen formando un PDF, escribiendo sus páginas progresivamente. Esto reduce la retención de imágenes decodificadas, pero **no garantiza memoria total constante**: permanecen modelos, estructuras del PDF, páginas codificadas y metadatos.

La dependencia fijada es [pypdf 6.20.1, BSD-3-Clause](https://pypi.org/project/pypdf/6.20.1/), usando sus [transformaciones de superposición](https://pypdf.readthedocs.io/en/stable/user/add-watermark.html). Solo se instaló en la imagen candidata aislada, no globalmente ni en el servicio activo.

Verificación del candidato: 46 pruebas aprobadas, incluida la suite histórica que requería pypdf; igualdad exacta de píxeles visibles en casos sintéticos con recortes y rotaciones de 0/90/180/270 grados. Una ejecución real del modelo con diez páginas sintéticas conservó imágenes y aspecto: 232466 bytes originales y 258089 bytes derivados (1,11 veces). No es una medición comparativa de rendimiento ni prueba con documentos del usuario. La activación sigue pendiente de verificación y autorización del coordinador.

## Trabajos recuperables: preparado, pendiente de despliegue

El candidato incorpora una cola local durable en el subdirectorio privado `jobs` del volumen OCR existente. Un único worker guarda el original y confirma un punto de control justo después de reconocer cada página. Al reiniciar, continúa las páginas pendientes sin repetir el reconocimiento de las ya confirmadas. Conserva el resultado terminado para volver a descargarlo sin ejecutar OCR. No utiliza Redis, Celery ni una base de datos nueva.

La admisión reserva espacio para original, puntos de control y PDF derivado junto con los artefactos anteriores: 512 MiB compartidos, hasta ocho trabajos y 32 posiciones combinadas. Con los máximos iniciales caben aproximadamente tres trabajos completos. Cada página confirma como máximo 256 KiB; puntos de control y metadatos del resultado comparten 16 MiB por trabajo. No se amplían los límites de páginas, píxeles ni tamaño original. Si no hay capacidad se rechaza temporalmente la carga; no se crea una cola ilimitada.

Los identificadores y capacidades independientes los genera el cliente; el servicio guarda únicamente el hash de la capacidad. Comprueba SHA-256 del original y una huella de configuración/modelos. Una repetición idéntica del mismo identificador devuelve su estado; un cambio de contenido o configuración se rechaza. Los diagnósticos no incluyen texto, rutas ni capacidades.

Los estados son `QUEUED`, `RUNNING`, `COMPLETED`, `ERROR` y `CANCELLED`, con fase y páginas confirmadas/total. Los trabajos activos y las descargas abiertas no caducan mientras están en uso; los terminales se conservan una hora y luego se limpian al admitir, consultar o reiniciar. La cancelación actúa entre páginas y antes de publicar; no elimina originales del backend. Un reintento intencional de un trabajo terminal requiere un identificador nuevo.

Las API nuevas son `/v2/jobs/config`, creación y consulta `/v2/jobs/{id}`, resultado `/v2/jobs/{id}/result` y cancelación mediante DELETE. Usan `X-Job-Capability`; la creación también verifica `X-Source-SHA256` y `X-OCR-Fingerprint`. La descarga mantiene `/v2/artifacts/{id}` y `X-Artifact-Capability`. Solo después de publicar correctamente el resultado, el backend podrá confirmar su recepción con DELETE `/v2/jobs/{id}/result`: devuelve 204 y libera el almacenamiento privado. Una descarga activa impide esa limpieza con 503; la confirmación fallida conserva el resultado. La repetición después de limpiar devuelve 404, que el cliente puede tratar como ya liberado. La limpieza confirmada puede completarse al reiniciar si hubo un fallo de disco posterior. El contrato completo y sus códigos están en `odd/tasks/ocr-scalability.md`. Las rutas antiguas siguen disponibles, pero rechazan sobrecarga con 503 en vez de esperar sin límite. `/health` distingue disponibilidad de la cola mediante `jobsReady`.

Verificación aislada: 70 pruebas aprobadas y recuperación sintética de tres páginas con el modelo real: tres reconocimientos en total, ninguno repetido para la página ya confirmada. Todos los workers de prueba se cerraron. **No está desplegado ni integrado todavía con la cola Java**; el progreso visible y la recuperación del backend corresponden a las tareas siguientes. No borrar el volumen para actualizar o revertir: una versión anterior no puede continuar estos trabajos.

## Cola del backend y progreso: preparado, pendiente de despliegue

El candidato Java consulta continuamente la cola PostgreSQL, reclamando un documento cada vez. No envía todos los documentos a una cola limitada de threads: los registros pendientes permanecen en la base hasta ser atendidos. Una lease identifica al worker y caduca si deja de renovarse; un heartbeat independiente la mantiene durante comunicaciones y descargas. El inicio no reinicia leases activas ni reintenta automáticamente documentos históricos en ERROR.

El identificador, la capacidad y la huella del trabajo OCR se guardan de forma privada antes de enviarlo. Los reintentos de comunicación conservan esa identidad y los puntos de control Python. Se permiten hasta cinco reintentos automáticos transitorios, con espera creciente; archivos inválidos y errores terminales requieren una decisión explícita. Si un trabajo ya enviado caducó en Python, no se vuelve a reconocer silenciosamente. El botón de reintento no puede duplicar un documento pendiente o procesándose.

El resultado solo se publica si siguen vigentes la lease y el identificador del trabajo. Primero se confirma la actualización en PostgreSQL; después se limpia el PDF anterior y se confirma la recepción en Python. Una confirmación remota fallida no revierte el documento publicado. La eliminación usa el mismo bloqueo y cancela el trabajo remoto solo después de confirmar su transacción.

La respuesta incorpora `ocrPaginasProcesadas`, `ocrPaginasTotales`, `ocrFase` y `ocrProximoReintento`, todos compatibles con valores null históricos. No expone capacidades, propietarios de lease ni rutas privadas; el campo antiguo `rutaAlmacenamiento` queda null y las rutas de descarga autenticadas no cambian. La interfaz con este progreso corresponde a S5, todavía pendiente.

`V13__ocr_jobs_progress.sql` añade columnas nullable y un índice de cola, sin modificar originales. Se verificó únicamente en PostgreSQL desechable con una marca de propiedad y un puerto/base distintos de producción. **Antes del despliegue se necesita una copia actual de producción y detener el backend anterior**; después se actualiza OCR y arranca el backend nuevo con el procedimiento operativo de este equipo. No ejecutar versiones vieja y nueva simultáneamente ni borrar volúmenes o columnas para revertir. El despliegue y la migración real siguen pendientes.

## Consulta del PDF reconocido

El PDF está disponible en cuanto termina el procesamiento, incluso si la confianza es baja. No hay una aprobación manual obligatoria.

La interfaz permite seleccionar, copiar y buscar el texto reconocido, pero no editarlo. El PDF conserva la apariencia del escaneo. **Generar PDF con OCR** permite reintentar un procesamiento fallido sin volver a subir el documento.

`GET /api/documentos-digitales/{id}/pdf` descarga el derivado y registra la descarga. `GET /api/documentos-digitales/{id}/archivo` obtiene el archivo principal subido o el PDF combinado. El servicio resuelve internamente el recurso asociado al documento; no utiliza la ruta null de la respuesta pública. La descarga requiere autenticación, conserva auditoría y no permite caché compartida. Para las imágenes de un lote combinado, utiliza las API de originales indicadas arriba.

Los documentos antiguos sin correcciones y sin PDF se reprograman automáticamente al iniciar; las transcripciones antiguas revisadas se preservan y no se reprocesan automáticamente. No se expone una API para editar el texto ni su posición.

Estados posibles:

- `PENDIENTE`: esperando un worker.
- `PROCESANDO`: el OCR está trabajando.
- `COMPLETADO`: texto reconocido con confianza suficiente.
- `REQUIERE_REVISION`: no se encontró texto suficiente o la confianza quedó debajo del umbral.
- `ERROR`: el servicio no pudo procesar el archivo; puede reintentarse desde la interfaz.

Los trabajos pendientes permanecen en PostgreSQL. En el candidato de cola durable, un procesamiento interrumpido se reclama cuando su lease caduca; no se reinicia un worker cuya lease sigue activa.

## Iniciar el entorno

```bash
docker compose up -d --build
```

La primera construcción tarda más porque instala RapidOCR y descarga los modelos dentro de la imagen. Las ejecuciones posteriores reutilizan la imagen local.

Verificación:

```bash
docker compose ps
docker compose logs -f ocr
```

El servicio publica solamente en `127.0.0.1:8091`. Su comprobación de salud está disponible en `http://localhost:8091/health`.

Después se inicia el backend normalmente:

```bash
# Windows
.\mvnw.cmd spring-boot:run

# Linux/macOS
./mvnw spring-boot:run
```

### Reinicio en este equipo Windows: recuperación del selector JDK

En este equipo, los intentos iniciales con Java 24 y Java 21 fallaron al arrancar Tomcat con `Unable to establish loopback connection` y `Invalid argument: connect`. Cambiar el proveedor de selector tampoco resolvió el problema. Se verificó por separado `Selector.open()` y la alternativa TCP que utiliza `PipeImpl` cuando no puede crear el socket de dominio Unix.

Desde `C:/Users/Bienvenido/OneDrive/Documentos/SistemaDeBusqueda2.0/archivo_backend`, el reinicio operativo probado con Java 24 es:

```powershell
.\mvnw.cmd "-Dspring-boot.run.jvmArguments=-Djdk.net.unixdomain.tmpdir=C:/Users/Bienvenido/OneDrive/Documentos/SistemaDeBusqueda2.0/output/backups/ocr-image-upload/selector-probe-do-not-create" spring-boot:run
```

**No crear el directorio `selector-probe-do-not-create`.** Su inexistencia permite la alternativa TCP comprobada. La propiedad se aplica solo a esa ejecución: no se modificaron variables persistentes, seguridad del equipo ni configuración del proyecto. No es una corrección del código de la aplicación ni una recomendación general para otros equipos.

El reinicio controlado aplicó Flyway V12 y validó `originales_manifest` como columna `TEXT` nullable. Backend en 8080, documentación API HTTP 200, descarga de originales sin autenticación HTTP 401, frontend en 4200 HTTP 200 y OCR saludable. Esto verifica disponibilidad y despliegue, no una carga completa autenticada desde el navegador.

Antes de la migración se validó una copia PostgreSQL formato personalizado (`PGDMP`, 112 entradas, 55 461 bytes):

- Copia: `C:/Users/Bienvenido/OneDrive/Documentos/SistemaDeBusqueda2.0/output/backups/ocr-image-upload/archivo_sanjose-pre-v12-20261014-120932.dump`.
- Inicio recuperado: `C:/Users/Bienvenido/OneDrive/Documentos/SistemaDeBusqueda2.0/output/backups/ocr-image-upload/backend-recovery-tcp.stdout.log`.
- Intentos fallidos: `backend-20261014-120932.stdout.log` y `backend-recovery-java21.stdout.log` en el mismo directorio.

No restaurar esa copia sobre datos actuales sin autorización y una evaluación de los cambios posteriores. La columna añadida puede permanecer al revertir código; eliminarla después de cargar imágenes agrupadas perdería sus asociaciones.

## Configuración

| Variable | Valor inicial | Uso |
|---|---:|---|
| `OCR_ENABLED` | `true` | Activa el procesamiento automático. |
| `OCR_URL` | `http://localhost:8091` | Dirección interna del servicio OCR. |
| `OCR_TIMEOUT_SECONDS` | `300` | Tiempo máximo por documento. |
| `OCR_REVIEW_THRESHOLD` | `0.75` | Confianza mínima para marcar el resultado como completado. |
| `OCR_MAX_PAGES` | `100` | Máximo de páginas que procesa el contenedor. |
| `OCR_PDF_DPI` | `220` | Resolución utilizada al convertir las páginas de un PDF a imagen. |

## Privacidad

Los documentos se procesan localmente. No se envían a servicios externos ni requieren claves de API. El texto reconocido queda dentro de PostgreSQL y los archivos originales permanecen en `storage/documentos`.

## Errores frecuentes

- **Servicio saludable pero el PDF falla al procesarse:** la salud comprueba disponibilidad, no la conversión. Si cambió `ocr_service/app.py`, reconstruye solo OCR con `docker compose up -d --build ocr`; esto no modifica PostgreSQL ni los documentos guardados. Después reintenta el documento existente, sin volver a subirlo. La imagen anterior usaba un context manager no soportado por pypdfium2 4.30; la versión actual cierra explícitamente documento, páginas y mapas de bits.
- **Error de procesamiento, tamaño o tiempo máximo:** el backend distingue rechazo del archivo (HTTP 400/422), límite de tamaño (413), error interno (5xx), conexión y tiempo agotado. No muestra respuestas internas del servicio ni rutas privadas. Un error de procesamiento no implica que el contenedor esté apagado.
- **El documento queda en ERROR:** confirma que `docker compose ps` muestre `archivo-ocr` como saludable y utiliza **Reintentar OCR**.
- **Docker no está iniciado:** abre Docker Desktop y vuelve a ejecutar `docker compose up -d --build`.
- **Resultado con baja confianza:** revisa visualmente el escaneo y el texto seleccionado; la capa reconocida sigue siendo de solo lectura.
- **PDF demasiado grande:** ajusta `OCR_MAX_PAGES` con prudencia; más páginas aumentan el tiempo y uso de memoria.

## Rendimiento medido y configuración de CPU (S6)

La configuración recomendada mantiene **un motor y un trabajo a la vez**, 220 DPI, sin GPU y sin omitir páginas por encontrar texto extraíble. Se establecen `OCR_ORT_INTRA_THREADS=2` y `OCR_ORT_INTER_THREADS=1`; ambos aceptan únicamente 1 o 2. Las claves verificadas en RapidOCR 3.9.2 son `EngineConfig.onnxruntime.intra_op_num_threads` e `EngineConfig.onnxruntime.inter_op_num_threads` ([configuración oficial](https://github.com/rapidai/rapidocr/blob/main/python/rapidocr/config.yaml), [aplicación en ONNX Runtime](https://github.com/rapidai/rapidocr/blob/main/python/rapidocr/inference_engine/onnxruntime/main.py)). Se comprobó su valor efectivo en las tres sesiones de detección, clasificación y reconocimiento. Estos parámetros forman parte de la huella de configuración: nunca reutilizar checkpoints de otra configuración; drenar/retener trabajos antes de cambiarla.

Prueba local aislada: un contenedor por caso, sin red, límite de 2 CPU/3 GiB, 180 segundos por caso. Corpus generado de páginas JPEG 1000×1400, texto conocido y 220 DPI. La referencia reconstruye la lista de rásteres y exportación que redibuja el fondo, usando el mismo motor/modelos/corpus; **no es una comparación de dos despliegues completos**. El candidato conserva el PDF original y añade la capa invisible. Medias descriptivas de dos ejecuciones, con un hilo en ambas variantes para separar la estrategia PDF de la configuración CPU:

| Páginas | Referencia: segundos / páginas por minuto | Candidato: segundos / páginas por minuto | PDF referencia / candidato | RSS máximo referencia / candidato |
|---|---|---|---|---|
| 1 | 1,57 / 38,18 | 1,27 / 47,33 | 853.629 / 117.683 bytes | 509,7 / 502,0 MiB |
| 10 | 15,62 / 38,41 | 12,27 / 48,91 | 8.333.026 / 979.728 bytes | 569,3 / 608,8 MiB |
| 40 | 62,77 / 38,23 | 48,22 / 49,78 | 33.235.908 / 3.850.618 bytes | 678,4 / 521,3 MiB |

El candidato de 10 páginas registró 51,79 s con hilos automáticos, 12,27 s con un hilo y 9,22 s con dos (dos muestras por ajuste; dos hilos: 65,07 páginas/minuto y RSS máximo 612,1 MiB). Una comprobación adicional de 40 páginas con dos hilos obtuvo 35,77 s, 67,09 páginas/minuto y 511,8 MiB; es **una muestra**, no un promedio estadístico. Tres imágenes ordenadas con un hilo: dos muestras, media 4,72 s, PDF 2.553.445 bytes, RSS máximo 512,2 MiB; la conversión de imágenes aún codifica sus fondos y merece una optimización separada, no se elimina ni altera silenciosamente.

Todos los casos conservaron el orden de etiquetas y reconocieron exactamente el texto sintético (CER 0). Los PDF candidatos conservaron los bytes/hash del original, los streams comprimidos de imágenes y los píxeles visibles de primera/última página al renderizar a 72 DPI; la referencia rasterizada no conserva dichos streams/píxeles. El tamaño candidato/original PDF fue 1,2262 / 1,0285 / 1,0120 para 1/10/40 páginas, frente a 8,8947 / 8,7475 / 8,7346 en la referencia. Los campos inputBytes/hash de imagen-batch usan el PDF sintético de referencia; no representan el tamaño de la petición JSON ni el hash de cada JPEG.

El tiempo de generación, arranque del modelo y calentamiento se registra por separado; el RSS incluye estos pasos y la validación, no toda la VM ni memoria constante. No hay p95, garantía de aceleración en expedientes reales, prueba de A4 pesado hasta 250 millones de píxeles ni medición HTTP/checkpoints/Java/base de datos/interfaz. No se activan dos motores: no se midió su coste ni ventaja en la VM de 8 GB. La omisión por texto existente queda diferida porque una capa parcial/invisible no prueba que todo el documento sea útil o buscable.

Resultados y registros recuperables: `C:/Users/Bienvenido/OneDrive/Documentos/SistemaDeBusqueda2.0/output/ocr-scalability-benchmark/summary.json` y archivos JSON/log por caso. No contienen documentos del usuario ni credenciales. El cambio de configuración está verificado localmente; la activación del sistema sigue siendo una operación separada.
