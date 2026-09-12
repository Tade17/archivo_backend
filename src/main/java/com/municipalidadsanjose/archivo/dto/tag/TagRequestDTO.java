package com.municipalidadsanjose.archivo.dto.tag;

import jakarta.validation.constraints.NotBlank;

public record TagRequestDTO(
        @NotBlank(message = "El nombre es obligatorio")
        String nombre
) {
}
