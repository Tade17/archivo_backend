package com.municipalidadsanjose.archivo.service;

import com.municipalidadsanjose.archivo.dto.expediente.ExpedienteRequestDTO;
import com.municipalidadsanjose.archivo.dto.expediente.ExpedienteResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

// Sin eliminar(): el expediente es el registro del documento archivado,
// nunca se borra de verdad. Su ciclo de vida se maneja con EstadoExpediente.
public interface ExpedienteService {
    ExpedienteResponseDTO crear(ExpedienteRequestDTO dto);
    ExpedienteResponseDTO actualizar(UUID id, ExpedienteRequestDTO dto);
    ExpedienteResponseDTO buscarPorId(UUID id);
    ExpedienteResponseDTO buscarPorCodigoUnico(String codigoUnico);
    Page<ExpedienteResponseDTO> listarTodos(Pageable pageable);
}
