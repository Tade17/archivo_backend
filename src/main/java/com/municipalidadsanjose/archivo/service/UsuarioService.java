package com.municipalidadsanjose.archivo.service;

import com.municipalidadsanjose.archivo.dto.usuario.UsuarioRequestDTO;
import com.municipalidadsanjose.archivo.dto.usuario.UsuarioResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UsuarioService {

    UsuarioResponseDTO crear(UsuarioRequestDTO dto);

    UsuarioResponseDTO actualizar(UUID id, UsuarioRequestDTO dto);

    UsuarioResponseDTO buscarPorId(UUID id);

    Page<UsuarioResponseDTO> listarTodos(Pageable pageable);

    void desactivar(UUID id);
}
