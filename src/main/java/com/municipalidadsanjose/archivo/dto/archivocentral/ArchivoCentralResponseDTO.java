package com.municipalidadsanjose.archivo.dto.archivocentral;

import java.util.UUID;

public record ArchivoCentralResponseDTO(
        UUID id,
        String nombre
) {
}
