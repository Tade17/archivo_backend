package com.municipalidadsanjose.archivo.dto.nivel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record NivelRequestDTO(
        @NotNull(message = "El estante es obligatorio")
        UUID estanteId,

        @NotBlank(message = "El código es obligatorio")
        String codigo
) {
}
