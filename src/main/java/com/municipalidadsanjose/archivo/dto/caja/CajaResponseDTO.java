package com.municipalidadsanjose.archivo.dto.caja;

import java.util.UUID;

public record CajaResponseDTO(
        UUID id,
        String codigo,
        UUID nivelId,
        String nivelCodigo
) {
}
