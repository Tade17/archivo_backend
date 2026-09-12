package com.municipalidadsanjose.archivo.dto.estante;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EstanteRequestDTO(
        @NotNull(message = "El archivo central es obligatorio")
        UUID archivoCentralId,

        @NotBlank(message = "El código es obligatorio")
        String codigo
) {
}
