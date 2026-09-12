package com.municipalidadsanjose.archivo.dto.tipodocumental;

import java.util.UUID;

public record TipoDocumentalResponseDTO(
        UUID id,
        String nombre
) {
}
