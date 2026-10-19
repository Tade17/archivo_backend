package com.municipalidadsanjose.archivo.dto.documentodigital;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

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
