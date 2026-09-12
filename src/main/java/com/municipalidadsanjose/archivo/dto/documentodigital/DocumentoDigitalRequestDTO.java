package com.municipalidadsanjose.archivo.dto.documentodigital;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DocumentoDigitalRequestDTO(
        @NotNull(message = "El expediente es obligatorio")
        UUID expedienteId,

        @NotBlank(message = "El nombre del archivo es obligatorio")
        String nombreArchivo,

        @NotBlank(message = "La ruta de almacenamiento es obligatoria")
        String rutaAlmacenamiento,

        @NotBlank(message = "El tipo MIME es obligatorio")
        String tipoMime,

        @NotBlank(message = "El hash SHA-256 es obligatorio")
        String hashSha256,

        @NotNull(message = "El técnico responsable es obligatorio")
        UUID tecnicoResponsableId,

        String escanerUtilizado,

        Integer resolucionDpi,

        String formatoSalida,

        String ocrTexto
) {
}
