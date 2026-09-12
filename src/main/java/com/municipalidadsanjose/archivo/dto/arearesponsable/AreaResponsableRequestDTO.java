package com.municipalidadsanjose.archivo.dto.arearesponsable;

import jakarta.validation.constraints.NotBlank;

public record AreaResponsableRequestDTO(
        @NotBlank(message = "El nombre es obligatorio")
        String nombre,

        @NotBlank(message = "El código es obligatorio")
        String codigo
) {
}
