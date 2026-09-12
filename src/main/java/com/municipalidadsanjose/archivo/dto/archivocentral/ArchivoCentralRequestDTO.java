package com.municipalidadsanjose.archivo.dto.archivocentral;

import jakarta.validation.constraints.NotBlank;

public record ArchivoCentralRequestDTO(
        @NotBlank(message = "El nombre es obligatorio")
        String nombre
) {
}
