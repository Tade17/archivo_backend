package com.municipalidadsanjose.archivo.mapper;

import com.municipalidadsanjose.archivo.dto.estante.EstanteRequestDTO;
import com.municipalidadsanjose.archivo.dto.estante.EstanteResponseDTO;
import com.municipalidadsanjose.archivo.entity.ArchivoCentral;
import com.municipalidadsanjose.archivo.entity.Estante;
import org.springframework.stereotype.Component;

@Component
public class EstanteMapper {

    public Estante toEntity(EstanteRequestDTO dto, ArchivoCentral archivoCentral) {
        Estante estante = new Estante();
        estante.setArchivoCentral(archivoCentral);
        estante.setCodigo(dto.codigo());
        return estante;
    }

    public EstanteResponseDTO toResponseDTO(Estante estante) {
        return new EstanteResponseDTO(
                estante.getId(),
                estante.getCodigo(),
                estante.getArchivoCentral().getId(),
                estante.getArchivoCentral().getNombre()
        );
    }
}
