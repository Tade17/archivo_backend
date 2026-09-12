package com.municipalidadsanjose.archivo.dto.expediente;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record ExpedienteResponseDTO(
        UUID id,
        String codigoUnico,
        String numeroDocumento,
        String remitente,
        String areaDestinoNombre,
        String tipoNombre,
        String estadoNombre,
        LocalDate fechaDocumento,
        String asunto,
        String glosa,
        UUID cajaId,
        String cajaCodigo,
        Integer numeroFolios,
        String creadoPorNombre,
        LocalDateTime fechaRegistro,
        LocalDateTime fechaActualizacion,
        Set<String> tags
) {
}
