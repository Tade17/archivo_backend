package com.municipalidadsanjose.archivo.service;

import com.municipalidadsanjose.archivo.dto.nivel.NivelRequestDTO;
import com.municipalidadsanjose.archivo.dto.nivel.NivelResponseDTO;

import java.util.List;
import java.util.UUID;

public interface NivelService {
    NivelResponseDTO crear(NivelRequestDTO dto);
    NivelResponseDTO actualizar(UUID id, NivelRequestDTO dto);
    NivelResponseDTO buscarPorId(UUID id);
    List<NivelResponseDTO> listarTodos();
    List<NivelResponseDTO> listarPorEstante(UUID estanteId);
    void eliminar(UUID id);
}
