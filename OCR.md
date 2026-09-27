# OCR automático

El sistema reconoce texto localmente con **RapidOCR**, usando detección y clasificación de orientación de PaddleOCR y el reconocedor latino **PP-OCRv5** sobre ONNX Runtime. Es la misma familia tecnológica identificada en ImageTrans, integrada como un servicio independiente y reproducible.

## Funcionamiento

1. El backend guarda el PDF, TIFF, JPG o PNG y crea el registro del documento.
2. La respuesta al usuario no espera al OCR.
3. Un worker envía el archivo al servicio `archivo-ocr`.
4. El servicio procesa todas las páginas y devuelve texto, confianza y cantidad de páginas.
5. El backend guarda el resultado en `ocr_texto`; el trigger existente actualiza `ocr_tsv` para que el contenido pueda encontrarse desde Explorar.

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
