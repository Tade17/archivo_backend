package com.municipalidadsanjose.archivo.dto.documentodigital;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentoDigitalResponseDTO(
        UUID id,
        UUID expedienteId,
        String expedienteCodigoUnico,
        String nombreArchivo,
        String rutaAlmacenamiento,
        String tipoMime,
        String hashSha256,
        LocalDateTime fechaDigitalizacion,
        String tecnicoResponsableNombre,
        String escanerUtilizado,
        Integer resolucionDpi,
        String formatoSalida,
        String ocrTexto,
        LocalDateTime fechaActualizacion
) {
}
