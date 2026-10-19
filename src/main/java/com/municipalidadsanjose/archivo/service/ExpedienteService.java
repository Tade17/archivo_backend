package com.municipalidadsanjose.archivo.service;

import com.municipalidadsanjose.archivo.dto.expediente.ExpedienteRequestDTO;
import com.municipalidadsanjose.archivo.dto.expediente.ExpedienteResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ExpedienteService {
    ExpedienteResponseDTO crear(ExpedienteRequestDTO dto);
    ExpedienteResponseDTO actualizar(UUID id, ExpedienteRequestDTO dto);
    void eliminar(UUID id);
    ExpedienteResponseDTO buscarPorId(UUID id);
    ExpedienteResponseDTO buscarPorCodigoUnico(String codigoUnico);
    Page<ExpedienteResponseDTO> listarTodos(Pageable pageable);
}
