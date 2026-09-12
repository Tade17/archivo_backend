package com.municipalidadsanjose.archivo.dto.arearesponsable;

import java.util.UUID;

public record AreaResponsableResponseDTO(
        UUID id,
        String nombre,
        String codigo
) {
}
