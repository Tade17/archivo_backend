package com.municipalidadsanjose.archivo.dto.nivel;

import java.util.UUID;

public record NivelResponseDTO(
        UUID id,
        String codigo,
        UUID estanteId,
        String estanteCodigo
) {
}
