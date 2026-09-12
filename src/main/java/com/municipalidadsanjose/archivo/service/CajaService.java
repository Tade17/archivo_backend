package com.municipalidadsanjose.archivo.service;

import com.municipalidadsanjose.archivo.dto.caja.CajaRequestDTO;
import com.municipalidadsanjose.archivo.dto.caja.CajaResponseDTO;

import java.util.List;
import java.util.UUID;

public interface CajaService {
    CajaResponseDTO crear(CajaRequestDTO dto);
    CajaResponseDTO actualizar(UUID id, CajaRequestDTO dto);
    CajaResponseDTO buscarPorId(UUID id);
    List<CajaResponseDTO> listarTodos();
    List<CajaResponseDTO> listarPorNivel(UUID nivelId);
    void eliminar(UUID id);
}
