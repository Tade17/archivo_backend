package com.municipalidadsanjose.archivo.mapper;

import com.municipalidadsanjose.archivo.dto.auditoria.AuditoriaResponseDTO;
import com.municipalidadsanjose.archivo.entity.Auditoria;
import org.springframework.stereotype.Component;

// Sin toEntity(): los registros de auditoría los va a crear el aspecto AOP
// directamente, no un cliente pegándole a un endpoint POST.
@Component
public class AuditoriaMapper {

    public AuditoriaResponseDTO toResponseDTO(Auditoria auditoria) {
        return new AuditoriaResponseDTO(
                auditoria.getId(),
                auditoria.getUsuario().getId(),
                auditoria.getUsuario().getNombre(),
                auditoria.getEntidadAfectada(),
                auditoria.getEntidadId(),
                auditoria.getAccion(),
                auditoria.getFecha(),
                auditoria.getDetalle()
        );
    }
}
