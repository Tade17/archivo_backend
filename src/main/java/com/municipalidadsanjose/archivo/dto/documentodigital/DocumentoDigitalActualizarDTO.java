package com.municipalidadsanjose.archivo.dto.documentodigital;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// Solo metadata editable. A propósito NO incluye rutaAlmacenamiento/tipoMime/
// hashSha256: esos quedan fijados por el archivo real subido en crear() y no
// se pueden sobreescribir a mano desde un PUT. Tampoco incluye ocrTexto: el
// texto reconocido por OCR es inmutable por requisito legal (ver OcrService /
// OcrWorker); ni siquiera el administrador puede editarlo desde este módulo.
public record DocumentoDigitalActualizarDTO(
        @NotBlank(message = "El nombre del archivo es obligatorio")
        String nombreArchivo,

        @NotNull(message = "El técnico responsable es obligatorio")
        UUID tecnicoResponsableId,

        String escanerUtilizado,

        Integer resolucionDpi,

        String formatoSalida
) {
}
