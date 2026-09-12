package com.municipalidadsanjose.archivo.service;

import com.municipalidadsanjose.archivo.dto.tipodocumental.TipoDocumentalRequestDTO;
import com.municipalidadsanjose.archivo.dto.tipodocumental.TipoDocumentalResponseDTO;

import java.util.List;
import java.util.UUID;

public interface TipoDocumentalService {
    TipoDocumentalResponseDTO crear(TipoDocumentalRequestDTO dto);
    TipoDocumentalResponseDTO actualizar(UUID id, TipoDocumentalRequestDTO dto);
    TipoDocumentalResponseDTO buscarPorId(UUID id);
    List<TipoDocumentalResponseDTO> listarTodos();
    void eliminar(UUID id);
}
