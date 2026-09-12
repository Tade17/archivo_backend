package com.municipalidadsanjose.archivo.dto.prestamo;

import com.municipalidadsanjose.archivo.enums.TipoSolicitud;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record PrestamoRequestDTO(
        @NotNull(message = "El expediente es obligatorio")
        UUID expedienteId,

        @NotNull(message = "El solicitante es obligatorio")
        UUID solicitanteId,

        @NotNull(message = "El tipo de solicitud es obligatorio")
        TipoSolicitud tipoSolicitud,

        LocalDate fechaDevolucionPrevista
) {
}
