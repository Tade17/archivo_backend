package com.municipalidadsanjose.archivo.service;

import com.municipalidadsanjose.archivo.dto.archivocentral.ArchivoCentralRequestDTO;
import com.municipalidadsanjose.archivo.dto.archivocentral.ArchivoCentralResponseDTO;

import java.util.List;
import java.util.UUID;

public interface ArchivoCentralService {
    ArchivoCentralResponseDTO crear(ArchivoCentralRequestDTO dto);
    ArchivoCentralResponseDTO actualizar(UUID id, ArchivoCentralRequestDTO dto);
    ArchivoCentralResponseDTO buscarPorId(UUID id);
    List<ArchivoCentralResponseDTO> listarTodos();
    void eliminar(UUID id);
}
