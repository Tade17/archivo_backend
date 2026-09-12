package com.municipalidadsanjose.archivo.dto.tipodocumental;

import jakarta.validation.constraints.NotBlank;

public record TipoDocumentalRequestDTO(
        @NotBlank(message = "El nombre es obligatorio")
        String nombre
) {
}
