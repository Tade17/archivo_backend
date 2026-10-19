package com.municipalidadsanjose.archivo.service;

import com.municipalidadsanjose.archivo.dto.prestamo.PrestamoRequestDTO;
import com.municipalidadsanjose.archivo.dto.prestamo.PrestamoResponseDTO;

import java.util.UUID;

// Los préstamos no se editan ni se borran: se registran y se devuelven.
public interface PrestamoService {
    PrestamoResponseDTO crear(PrestamoRequestDTO dto, UUID registradoPorId);
    PrestamoResponseDTO devolver(UUID id);
}
