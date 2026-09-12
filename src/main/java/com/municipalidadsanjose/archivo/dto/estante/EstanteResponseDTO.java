package com.municipalidadsanjose.archivo.dto.estante;

import java.util.UUID;

public record EstanteResponseDTO(
        UUID id,
        String codigo,
        UUID archivoCentralId,
        String archivoCentralNombre
) {
}
