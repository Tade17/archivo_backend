# OCR automático

El sistema reconoce texto localmente con **RapidOCR**, usando detección y clasificación de orientación de PaddleOCR y el reconocedor latino **PP-OCRv5** sobre ONNX Runtime. Es la misma familia tecnológica identificada en ImageTrans, integrada como un servicio independiente y reproducible.

## Funcionamiento

1. El backend guarda el PDF, TIFF, JPG o PNG y crea el registro del documento.
2. La respuesta al usuario no espera al OCR.
3. Un worker envía el archivo al servicio `archivo-ocr`.
4. El servicio procesa todas las páginas y devuelve texto, confianza y cantidad de páginas.
5. Se genera un PDF que conserva las páginas escaneadas y añade una capa Unicode de texto seleccionable y buscable. El backend guarda el PDF derivado y la distribución de los fragmentos; el archivo original y su hash se conservan.
6. El backend guarda el resultado en `ocr_texto`; el trigger existente actualiza `ocr_tsv` para que el contenido pueda encontrarse desde Explorar.

## Corrección opcional desde la página

El PDF está disponible en cuanto termina el procesamiento, incluso si la confianza es baja. No hay una aprobación manual obligatoria.

1. Abre el documento y pulsa **Corregir texto en la página**.
2. Selecciona un recuadro del fragmento reconocido y escribe la corrección en el editor sobre la página.
3. Pulsa **Guardar PDF y búsqueda**. Se regenera el PDF con la capa corregida, se actualiza la búsqueda y se registra la modificación en auditoría.

Las correcciones modifican el texto seleccionable, no las letras fotografiadas del escaneo. **Descargar PDF con texto** obtiene la versión actualizada; **Descargar original** obtiene el archivo subido. La búsqueda interna del visor funciona sobre el texto del PDF.

La edición requiere rol administrador o gestor documental. Se controla la versión para evitar que dos operadores sobrescriban sus cambios. Los documentos antiguos sin correcciones y sin PDF se reprograman automáticamente al iniciar; las transcripciones antiguas revisadas se preservan y no se reprocesan automáticamente.

API: `GET /api/documentos-digitales/{id}/ocr/layout` devuelve `{layout, version}`; `PUT` sobre la misma ruta guarda los textos de los bloques y devuelve el documento actualizado. Las coordenadas y los identificadores no pueden alterarse. `GET /api/documentos-digitales/{id}/pdf` descarga el derivado y registra la descarga.

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

- **El documento queda en ERROR:** confirma que `docker compose ps` muestre `archivo-ocr` como saludable y utiliza **Reintentar OCR**.
- **Docker no está iniciado:** abre Docker Desktop y vuelve a ejecutar `docker compose up -d --build`.
- **Resultado con baja confianza:** la interfaz mostrará **Revisión recomendada** y permitirá corregir el texto sin volver a procesar el archivo.
- **PDF demasiado grande:** ajusta `OCR_MAX_PAGES` con prudencia; más páginas aumentan el tiempo y uso de memoria.
