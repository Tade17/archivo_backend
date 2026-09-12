package com.municipalidadsanjose.archivo.service;

import com.municipalidadsanjose.archivo.dto.tag.TagRequestDTO;
import com.municipalidadsanjose.archivo.dto.tag.TagResponseDTO;

import java.util.List;
import java.util.UUID;

public interface TagService {
    TagResponseDTO crear(TagRequestDTO dto);
    TagResponseDTO actualizar(UUID id, TagRequestDTO dto);
    TagResponseDTO buscarPorId(UUID id);
    List<TagResponseDTO> listarTodos();
    void eliminar(UUID id);
}
