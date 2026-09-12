package com.municipalidadsanjose.archivo.dto.estadoexpediente;

import java.util.UUID;

public record EstadoExpedienteResponseDTO(
        UUID id,
        String nombre
) {
}
