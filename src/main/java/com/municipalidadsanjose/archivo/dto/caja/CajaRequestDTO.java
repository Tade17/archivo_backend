package com.municipalidadsanjose.archivo.dto.caja;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CajaRequestDTO(
        @NotNull(message = "El nivel es obligatorio")
        UUID nivelId,

        @NotBlank(message = "El código es obligatorio")
        String codigo
) {
}
