package com.municipalidadsanjose.archivo.service;

import com.municipalidadsanjose.archivo.dto.auditoria.AuditoriaResponseDTO;

import java.util.List;
import java.util.UUID;

public interface AuditoriaService {
    AuditoriaResponseDTO buscarPorId(UUID id);
    List<AuditoriaResponseDTO> listarTodos();
    List<AuditoriaResponseDTO> buscarPorEntidad(String entidadAfectada, UUID entidadId);
}
