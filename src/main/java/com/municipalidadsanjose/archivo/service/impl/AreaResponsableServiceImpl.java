package com.municipalidadsanjose.archivo.service.impl;

import com.municipalidadsanjose.archivo.audit.Auditable;
import com.municipalidadsanjose.archivo.dto.arearesponsable.AreaResponsableRequestDTO;
import com.municipalidadsanjose.archivo.dto.arearesponsable.AreaResponsableResponseDTO;
import com.municipalidadsanjose.archivo.entity.AreaResponsable;
import com.municipalidadsanjose.archivo.enums.AccionAuditoria;
import com.municipalidadsanjose.archivo.exception.RecursoDuplicadoException;
import com.municipalidadsanjose.archivo.exception.RecursoEnUsoException;
import com.municipalidadsanjose.archivo.exception.RecursoNoEncontradoException;
import com.municipalidadsanjose.archivo.mapper.AreaResponsableMapper;
import com.municipalidadsanjose.archivo.repository.AreaResponsableRepository;
import com.municipalidadsanjose.archivo.repository.ExpedienteRepository;
import com.municipalidadsanjose.archivo.service.AreaResponsableService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AreaResponsableServiceImpl implements AreaResponsableService {

    private final AreaResponsableRepository areaResponsableRepository;
    private final AreaResponsableMapper areaResponsableMapper;
    private final ExpedienteRepository expedienteRepository;

    public AreaResponsableServiceImpl(AreaResponsableRepository areaResponsableRepository,
                                       AreaResponsableMapper areaResponsableMapper,
                                       ExpedienteRepository expedienteRepository) {
        this.areaResponsableRepository = areaResponsableRepository;
        this.areaResponsableMapper = areaResponsableMapper;
        this.expedienteRepository = expedienteRepository;
    }

    @Override
    @Transactional
    @Auditable(entidad = "AreaResponsable", accion = AccionAuditoria.CREAR)
    public AreaResponsableResponseDTO crear(AreaResponsableRequestDTO dto) {
        if (areaResponsableRepository.existsByNombre(dto.nombre())) {
            throw new RecursoDuplicadoException("Ya existe un área con el nombre: " + dto.nombre());
        }
        if (areaResponsableRepository.existsByCodigo(dto.codigo())) {
            throw new RecursoDuplicadoException("Ya existe un área con el código: " + dto.codigo());
        }
        AreaResponsable guardada = areaResponsableRepository.save(areaResponsableMapper.toEntity(dto));
        return areaResponsableMapper.toResponseDTO(guardada);
    }

    @Override
    @Transactional
    @Auditable(entidad = "AreaResponsable", accion = AccionAuditoria.MODIFICAR)
    public AreaResponsableResponseDTO actualizar(UUID id, AreaResponsableRequestDTO dto) {
        AreaResponsable area = areaResponsableRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("AreaResponsable", id));

        if (!area.getNombre().equals(dto.nombre()) && areaResponsableRepository.existsByNombre(dto.nombre())) {
            throw new RecursoDuplicadoException("Ya existe un área con el nombre: " + dto.nombre());
        }
        if (!area.getCodigo().equals(dto.codigo()) && areaResponsableRepository.existsByCodigo(dto.codigo())) {
            throw new RecursoDuplicadoException("Ya existe un área con el código: " + dto.codigo());
        }

        area.setNombre(dto.nombre());
        area.setCodigo(dto.codigo());
        return areaResponsableMapper.toResponseDTO(area);
    }

    @Override
    @Transactional(readOnly = true)
    public AreaResponsableResponseDTO buscarPorId(UUID id) {
        return areaResponsableMapper.toResponseDTO(
                areaResponsableRepository.findById(id)
                        .orElseThrow(() -> new RecursoNoEncontradoException("AreaResponsable", id)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AreaResponsableResponseDTO> listarTodos() {
        return areaResponsableRepository.findAll().stream()
                .map(areaResponsableMapper::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional
    @Auditable(entidad = "AreaResponsable", accion = AccionAuditoria.ELIMINAR)
    public void eliminar(UUID id) {
        if (!areaResponsableRepository.existsById(id)) {
            throw new RecursoNoEncontradoException("AreaResponsable", id);
        }
        if (expedienteRepository.existsByAreaDestinoId(id)) {
            throw new RecursoEnUsoException("No se puede eliminar el área: hay expedientes dirigidos a ella");
        }
        areaResponsableRepository.deleteById(id);
    }
}
