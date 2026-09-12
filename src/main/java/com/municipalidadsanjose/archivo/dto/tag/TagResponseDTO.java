package com.municipalidadsanjose.archivo.dto.tag;

import java.util.UUID;

public record TagResponseDTO(
        UUID id,
        String nombre
) {
}
