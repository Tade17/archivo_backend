package com.municipalidadsanjose.archivo.service;

import com.municipalidadsanjose.archivo.dto.contenedor.ContenedorRequestDTO;
import com.municipalidadsanjose.archivo.dto.contenedor.ContenedorResponseDTO;

import java.util.UUID;

public interface ContenedorService {
    ContenedorResponseDTO crear(ContenedorRequestDTO dto, UUID creadoPorId);
    ContenedorResponseDTO actualizar(UUID id, ContenedorRequestDTO dto);
    ContenedorResponseDTO completar(UUID id);
    ContenedorResponseDTO reabrir(UUID id);
    void eliminar(UUID id);
}
