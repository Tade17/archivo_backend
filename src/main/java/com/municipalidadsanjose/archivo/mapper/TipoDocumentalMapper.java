package com.municipalidadsanjose.archivo.mapper;

import com.municipalidadsanjose.archivo.dto.tipodocumental.TipoDocumentalRequestDTO;
import com.municipalidadsanjose.archivo.dto.tipodocumental.TipoDocumentalResponseDTO;
import com.municipalidadsanjose.archivo.entity.TipoDocumental;
import org.springframework.stereotype.Component;

@Component
public class TipoDocumentalMapper {

    public TipoDocumental toEntity(TipoDocumentalRequestDTO dto) {
        TipoDocumental tipo = new TipoDocumental();
        tipo.setNombre(dto.nombre());
        return tipo;
    }

    public TipoDocumentalResponseDTO toResponseDTO(TipoDocumental tipo) {
        return new TipoDocumentalResponseDTO(tipo.getId(), tipo.getNombre());
    }
}
