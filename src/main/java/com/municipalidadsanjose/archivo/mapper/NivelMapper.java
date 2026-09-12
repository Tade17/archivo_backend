package com.municipalidadsanjose.archivo.mapper;

import com.municipalidadsanjose.archivo.dto.nivel.NivelRequestDTO;
import com.municipalidadsanjose.archivo.dto.nivel.NivelResponseDTO;
import com.municipalidadsanjose.archivo.entity.Estante;
import com.municipalidadsanjose.archivo.entity.Nivel;
import org.springframework.stereotype.Component;

@Component
public class NivelMapper {

    public Nivel toEntity(NivelRequestDTO dto, Estante estante) {
        Nivel nivel = new Nivel();
        nivel.setEstante(estante);
        nivel.setCodigo(dto.codigo());
        return nivel;
    }

    public NivelResponseDTO toResponseDTO(Nivel nivel) {
        return new NivelResponseDTO(
                nivel.getId(),
                nivel.getCodigo(),
                nivel.getEstante().getId(),
                nivel.getEstante().getCodigo()
        );
    }
}
