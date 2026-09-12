package com.municipalidadsanjose.archivo.service;

import com.municipalidadsanjose.archivo.dto.documentodigital.DocumentoDigitalActualizarDTO;
import com.municipalidadsanjose.archivo.dto.documentodigital.DocumentoDigitalRequestDTO;
import com.municipalidadsanjose.archivo.dto.documentodigital.DocumentoDigitalResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface DocumentoDigitalService {
    // dto ya viene con rutaAlmacenamiento/tipoMime/hashSha256/nombreArchivo resueltos
    // por el Controller a partir del archivo real subido (ver FileStorageService).
    DocumentoDigitalResponseDTO crear(DocumentoDigitalRequestDTO dto);
    DocumentoDigitalResponseDTO actualizar(UUID id, DocumentoDigitalActualizarDTO dto);
    DocumentoDigitalResponseDTO buscarPorId(UUID id);
    Page<DocumentoDigitalResponseDTO> listarTodos(Pageable pageable);
    Page<DocumentoDigitalResponseDTO> listarPorExpediente(UUID expedienteId, Pageable pageable);
    // Deja registrada la descarga en auditoría y devuelve la metadata necesaria
    // para que el Controller arme la respuesta con el archivo real.
    DocumentoDigitalResponseDTO registrarDescarga(UUID id);
    void eliminar(UUID id);
}
