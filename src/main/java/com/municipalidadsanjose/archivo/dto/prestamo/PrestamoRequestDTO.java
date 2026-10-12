package com.municipalidadsanjose.archivo.dto.prestamo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

// Se presta un documento (expedienteId) o un archivador/folder (contenedorId): uno de los dos.
public record PrestamoRequestDTO(
        UUID expedienteId,
        UUID contenedorId,

        @NotBlank(message = "Indica la entidad o persona que solicita el préstamo")
        @Size(max = 200, message = "El nombre del solicitante no puede superar 200 caracteres")
        String solicitante,

        @Min(value = 1, message = "El plazo mínimo es de 1 día")
        @Max(value = 365, message = "El plazo máximo es de 365 días")
        Integer dias,

        @Size(max = 500, message = "La observación no puede superar 500 caracteres")
        String observaciones
) {
}
