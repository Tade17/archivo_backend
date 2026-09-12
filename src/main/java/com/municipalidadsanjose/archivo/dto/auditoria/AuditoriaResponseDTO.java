package com.municipalidadsanjose.archivo.dto.auditoria;

import com.municipalidadsanjose.archivo.enums.AccionAuditoria;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record AuditoriaResponseDTO(
        UUID id,
        UUID usuarioId,
        String usuarioNombre,
        String entidadAfectada,
        UUID entidadId,
        AccionAuditoria accion,
        LocalDateTime fecha,
        Map<String, Object> detalle
) {
}
