package com.municipalidadsanjose.archivo.service;

import com.municipalidadsanjose.archivo.dto.documentodigital.DocumentoDigitalActualizarDTO;
import com.municipalidadsanjose.archivo.dto.documentodigital.DocumentoDigitalRequestDTO;
import com.municipalidadsanjose.archivo.dto.documentodigital.DocumentoDigitalResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface DocumentoDigitalService {
    DocumentoDigitalResponseDTO crear(DocumentoDigitalRequestDTO dto);
    DocumentoDigitalResponseDTO actualizar(UUID id, DocumentoDigitalActualizarDTO dto);
    DocumentoDigitalResponseDTO buscarPorId(UUID id);
    Page<DocumentoDigitalResponseDTO> listarTodos(Pageable pageable);
    Page<DocumentoDigitalResponseDTO> listarPorExpediente(UUID expedienteId, Pageable pageable);
    DocumentoDigitalResponseDTO registrarDescarga(UUID id);
    org.springframework.core.io.Resource cargarArchivoOriginal(UUID id);
    void eliminar(UUID id);
}
