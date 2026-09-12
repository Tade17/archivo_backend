package com.municipalidadsanjose.archivo.mapper;

import com.municipalidadsanjose.archivo.dto.archivocentral.ArchivoCentralRequestDTO;
import com.municipalidadsanjose.archivo.dto.archivocentral.ArchivoCentralResponseDTO;
import com.municipalidadsanjose.archivo.entity.ArchivoCentral;
import org.springframework.stereotype.Component;

@Component
public class ArchivoCentralMapper {

    public ArchivoCentral toEntity(ArchivoCentralRequestDTO dto) {
        ArchivoCentral archivoCentral = new ArchivoCentral();
        archivoCentral.setNombre(dto.nombre());
        return archivoCentral;
    }

    public ArchivoCentralResponseDTO toResponseDTO(ArchivoCentral archivoCentral) {
        return new ArchivoCentralResponseDTO(archivoCentral.getId(), archivoCentral.getNombre());
    }
}
