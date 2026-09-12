package com.municipalidadsanjose.archivo.service;

import com.municipalidadsanjose.archivo.dto.prestamo.PrestamoRequestDTO;
import com.municipalidadsanjose.archivo.dto.prestamo.PrestamoResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PrestamoService {
    PrestamoResponseDTO crear(PrestamoRequestDTO dto);
    PrestamoResponseDTO actualizar(UUID id, PrestamoRequestDTO dto);
    PrestamoResponseDTO buscarPorId(UUID id);
    Page<PrestamoResponseDTO> listarTodos(Pageable pageable);
    Page<PrestamoResponseDTO> listarPorExpediente(UUID expedienteId, Pageable pageable);
    Page<PrestamoResponseDTO> listarPorSolicitante(UUID solicitanteId, Pageable pageable);
    PrestamoResponseDTO devolver(UUID id);
    void eliminar(UUID id);
}
