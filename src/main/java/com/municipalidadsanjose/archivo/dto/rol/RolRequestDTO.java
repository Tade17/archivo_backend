package com.municipalidadsanjose.archivo.dto.rol;

import jakarta.validation.constraints.NotBlank;

public record RolRequestDTO(
        @NotBlank(message = "El nombre del rol es obligatorio")
        String nombre,

        String descripcion
) {
}
