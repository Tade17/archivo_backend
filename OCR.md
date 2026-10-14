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

Solo se probó el candidato en contenedores efímeros aislados y pruebas Java sin base de datos. El OCR/backend que ya están en ejecución no se actualizaron durante S1. La página larga, la cola durable y la optimización medida siguen pendientes.

## Consulta del PDF reconocido

El PDF está disponible en cuanto termina el procesamiento, incluso si la confianza es baja. No hay una aprobación manual obligatoria.

La interfaz permite seleccionar, copiar y buscar el texto reconocido, pero no editarlo. El PDF conserva la apariencia del escaneo. **Generar PDF con OCR** permite reintentar un procesamiento fallido sin volver a subir el documento.

`GET /api/documentos-digitales/{id}/pdf` descarga el derivado y registra la descarga. `GET /api/documentos-digitales/{id}/archivo` obtiene el archivo principal subido o el PDF combinado. Para las imágenes de un lote combinado, utiliza las API de originales indicadas arriba.

Los documentos antiguos sin correcciones y sin PDF se reprograman automáticamente al iniciar; las transcripciones antiguas revisadas se preservan y no se reprocesan automáticamente. No se expone una API para editar el texto ni su posición.

Estados posibles:

- `PENDIENTE`: esperando un worker.
- `PROCESANDO`: el OCR está trabajando.
- `COMPLETADO`: texto reconocido con confianza suficiente.
- `REQUIERE_REVISION`: no se encontró texto suficiente o la confianza quedó debajo del umbral.
- `ERROR`: el servicio no pudo procesar el archivo; puede reintentarse desde la interfaz.

Los trabajos pendientes se recuperan al reiniciar el backend. Un procesamiento que quedó interrumpido también vuelve automáticamente a la cola.

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
