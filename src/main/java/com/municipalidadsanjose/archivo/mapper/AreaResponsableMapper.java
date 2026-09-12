package com.municipalidadsanjose.archivo.mapper;

import com.municipalidadsanjose.archivo.dto.arearesponsable.AreaResponsableRequestDTO;
import com.municipalidadsanjose.archivo.dto.arearesponsable.AreaResponsableResponseDTO;
import com.municipalidadsanjose.archivo.entity.AreaResponsable;
import org.springframework.stereotype.Component;

@Component
public class AreaResponsableMapper {

    public AreaResponsable toEntity(AreaResponsableRequestDTO dto) {
        AreaResponsable area = new AreaResponsable();
        area.setNombre(dto.nombre());
        area.setCodigo(dto.codigo());
        return area;
    }

    public AreaResponsableResponseDTO toResponseDTO(AreaResponsable area) {
        return new AreaResponsableResponseDTO(area.getId(), area.getNombre(), area.getCodigo());
    }
}
