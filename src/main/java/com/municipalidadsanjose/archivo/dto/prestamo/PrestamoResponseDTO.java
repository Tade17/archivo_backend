package com.municipalidadsanjose.archivo.dto.prestamo;

import com.municipalidadsanjose.archivo.enums.EstadoPrestamo;
import com.municipalidadsanjose.archivo.enums.TipoSolicitud;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PrestamoResponseDTO(
        UUID id,
        UUID expedienteId,
        String expedienteCodigoUnico,
        String solicitanteNombre,
        TipoSolicitud tipoSolicitud,
        LocalDateTime fechaSolicitud,
        LocalDate fechaDevolucionPrevista,
        LocalDateTime fechaDevolucionReal,
        EstadoPrestamo estado,
        LocalDateTime fechaActualizacion
) {
}
