package com.municipalidadsanjose.archivo.service;

import com.municipalidadsanjose.archivo.dto.arearesponsable.AreaResponsableRequestDTO;
import com.municipalidadsanjose.archivo.dto.arearesponsable.AreaResponsableResponseDTO;

import java.util.List;
import java.util.UUID;

public interface AreaResponsableService {
    AreaResponsableResponseDTO crear(AreaResponsableRequestDTO dto);
    AreaResponsableResponseDTO actualizar(UUID id, AreaResponsableRequestDTO dto);
    AreaResponsableResponseDTO buscarPorId(UUID id);
    List<AreaResponsableResponseDTO> listarTodos();
    void eliminar(UUID id);
}
