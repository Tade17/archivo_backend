package com.municipalidadsanjose.archivo.service;

import com.municipalidadsanjose.archivo.dto.estante.EstanteRequestDTO;
import com.municipalidadsanjose.archivo.dto.estante.EstanteResponseDTO;

import java.util.List;
import java.util.UUID;

public interface EstanteService {
    EstanteResponseDTO crear(EstanteRequestDTO dto);
    EstanteResponseDTO actualizar(UUID id, EstanteRequestDTO dto);
    EstanteResponseDTO buscarPorId(UUID id);
    List<EstanteResponseDTO> listarTodos();
    List<EstanteResponseDTO> listarPorArchivoCentral(UUID archivoCentralId);
    void eliminar(UUID id);
}
