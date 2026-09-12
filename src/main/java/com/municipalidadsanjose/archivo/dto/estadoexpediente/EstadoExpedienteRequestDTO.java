package com.municipalidadsanjose.archivo.dto.estadoexpediente;

import jakarta.validation.constraints.NotBlank;

public record EstadoExpedienteRequestDTO(
        @NotBlank(message = "El nombre es obligatorio")
        String nombre
) {
}
