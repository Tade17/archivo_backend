package com.municipalidadsanjose.archivo.service;

import com.municipalidadsanjose.archivo.dto.estadoexpediente.EstadoExpedienteRequestDTO;
import com.municipalidadsanjose.archivo.dto.estadoexpediente.EstadoExpedienteResponseDTO;

import java.util.List;
import java.util.UUID;

public interface EstadoExpedienteService {
    EstadoExpedienteResponseDTO crear(EstadoExpedienteRequestDTO dto);
    EstadoExpedienteResponseDTO actualizar(UUID id, EstadoExpedienteRequestDTO dto);
    EstadoExpedienteResponseDTO buscarPorId(UUID id);
    List<EstadoExpedienteResponseDTO> listarTodos();
    void eliminar(UUID id);
}
