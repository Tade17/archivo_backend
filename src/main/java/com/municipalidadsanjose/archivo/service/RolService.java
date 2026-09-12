package com.municipalidadsanjose.archivo.service;


import com.municipalidadsanjose.archivo.dto.rol.RolRequestDTO;
import com.municipalidadsanjose.archivo.dto.rol.RolResponseDTO;

import java.util.List;
import java.util.UUID;

public interface RolService {
    RolResponseDTO crear(RolRequestDTO dto);

    RolResponseDTO actualizar(UUID id, RolRequestDTO dto);

    RolResponseDTO buscarPorID(UUID id);

    List<RolResponseDTO> listarTodos();

    void eliminar(UUID id);

}
