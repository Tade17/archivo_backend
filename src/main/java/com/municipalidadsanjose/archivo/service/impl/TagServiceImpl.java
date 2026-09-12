package com.municipalidadsanjose.archivo.service.impl;

import com.municipalidadsanjose.archivo.audit.Auditable;
import com.municipalidadsanjose.archivo.dto.tag.TagRequestDTO;
import com.municipalidadsanjose.archivo.dto.tag.TagResponseDTO;
import com.municipalidadsanjose.archivo.entity.Tag;
import com.municipalidadsanjose.archivo.enums.AccionAuditoria;
import com.municipalidadsanjose.archivo.exception.RecursoDuplicadoException;
import com.municipalidadsanjose.archivo.exception.RecursoEnUsoException;
import com.municipalidadsanjose.archivo.exception.RecursoNoEncontradoException;
import com.municipalidadsanjose.archivo.mapper.TagMapper;
import com.municipalidadsanjose.archivo.repository.ExpedienteRepository;
import com.municipalidadsanjose.archivo.repository.TagRepository;
import com.municipalidadsanjose.archivo.service.TagService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TagServiceImpl implements TagService {

    private final TagRepository tagRepository;
    private final TagMapper tagMapper;
    private final ExpedienteRepository expedienteRepository;

    public TagServiceImpl(TagRepository tagRepository,
                           TagMapper tagMapper,
                           ExpedienteRepository expedienteRepository) {
        this.tagRepository = tagRepository;
        this.tagMapper = tagMapper;
        this.expedienteRepository = expedienteRepository;
    }

    @Override
    @Transactional
    @Auditable(entidad = "Tag", accion = AccionAuditoria.CREAR)
    public TagResponseDTO crear(TagRequestDTO dto) {
        if (tagRepository.existsByNombre(dto.nombre())) {
            throw new RecursoDuplicadoException("Ya existe un tag con el nombre: " + dto.nombre());
        }
        Tag guardado = tagRepository.save(tagMapper.toEntity(dto));
        return tagMapper.toResponseDTO(guardado);
    }

    @Override
    @Transactional
    @Auditable(entidad = "Tag", accion = AccionAuditoria.MODIFICAR)
    public TagResponseDTO actualizar(UUID id, TagRequestDTO dto) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Tag", id));

        if (!tag.getNombre().equals(dto.nombre()) && tagRepository.existsByNombre(dto.nombre())) {
            throw new RecursoDuplicadoException("Ya existe un tag con el nombre: " + dto.nombre());
        }

        tag.setNombre(dto.nombre());
        return tagMapper.toResponseDTO(tag);
    }

    @Override
    @Transactional(readOnly = true)
    public TagResponseDTO buscarPorId(UUID id) {
        return tagMapper.toResponseDTO(
                tagRepository.findById(id)
                        .orElseThrow(() -> new RecursoNoEncontradoException("Tag", id)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagResponseDTO> listarTodos() {
        return tagRepository.findAll().stream()
                .map(tagMapper::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional
    @Auditable(entidad = "Tag", accion = AccionAuditoria.ELIMINAR)
    public void eliminar(UUID id) {
        if (!tagRepository.existsById(id)) {
            throw new RecursoNoEncontradoException("Tag", id);
        }
        if (expedienteRepository.existsByTagsId(id)) {
            throw new RecursoEnUsoException("No se puede eliminar el tag: hay expedientes que lo usan");
        }
        tagRepository.deleteById(id);
    }
}
