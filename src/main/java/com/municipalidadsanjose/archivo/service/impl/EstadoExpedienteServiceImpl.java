package com.municipalidadsanjose.archivo.service.impl;

import com.municipalidadsanjose.archivo.audit.Auditable;
import com.municipalidadsanjose.archivo.dto.estadoexpediente.EstadoExpedienteRequestDTO;
import com.municipalidadsanjose.archivo.dto.estadoexpediente.EstadoExpedienteResponseDTO;
import com.municipalidadsanjose.archivo.entity.EstadoExpediente;
import com.municipalidadsanjose.archivo.enums.AccionAuditoria;
import com.municipalidadsanjose.archivo.exception.RecursoDuplicadoException;
import com.municipalidadsanjose.archivo.exception.RecursoEnUsoException;
import com.municipalidadsanjose.archivo.exception.RecursoNoEncontradoException;
import com.municipalidadsanjose.archivo.mapper.EstadoExpedienteMapper;
import com.municipalidadsanjose.archivo.repository.EstadoExpedienteRepository;
import com.municipalidadsanjose.archivo.repository.ExpedienteRepository;
import com.municipalidadsanjose.archivo.service.EstadoExpedienteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class EstadoExpedienteServiceImpl implements EstadoExpedienteService {

    private final EstadoExpedienteRepository estadoExpedienteRepository;
    private final EstadoExpedienteMapper estadoExpedienteMapper;
    private final ExpedienteRepository expedienteRepository;

    public EstadoExpedienteServiceImpl(EstadoExpedienteRepository estadoExpedienteRepository,
                                        EstadoExpedienteMapper estadoExpedienteMapper,
                                        ExpedienteRepository expedienteRepository) {
        this.estadoExpedienteRepository = estadoExpedienteRepository;
        this.estadoExpedienteMapper = estadoExpedienteMapper;
        this.expedienteRepository = expedienteRepository;
    }

    @Override
    @Transactional
    @Auditable(entidad = "EstadoExpediente", accion = AccionAuditoria.CREAR)
    public EstadoExpedienteResponseDTO crear(EstadoExpedienteRequestDTO dto) {
        if (estadoExpedienteRepository.existsByNombre(dto.nombre())) {
            throw new RecursoDuplicadoException("Ya existe un estado con el nombre: " + dto.nombre());
        }
        EstadoExpediente guardado = estadoExpedienteRepository.save(estadoExpedienteMapper.toEntity(dto));
        return estadoExpedienteMapper.toResponseDTO(guardado);
    }

    @Override
    @Transactional
    @Auditable(entidad = "EstadoExpediente", accion = AccionAuditoria.MODIFICAR)
    public EstadoExpedienteResponseDTO actualizar(UUID id, EstadoExpedienteRequestDTO dto) {
        EstadoExpediente estado = estadoExpedienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("EstadoExpediente", id));

        if (!estado.getNombre().equals(dto.nombre()) && estadoExpedienteRepository.existsByNombre(dto.nombre())) {
            throw new RecursoDuplicadoException("Ya existe un estado con el nombre: " + dto.nombre());
        }

        estado.setNombre(dto.nombre());
        return estadoExpedienteMapper.toResponseDTO(estado);
    }

    @Override
    @Transactional(readOnly = true)
    public EstadoExpedienteResponseDTO buscarPorId(UUID id) {
        return estadoExpedienteMapper.toResponseDTO(
                estadoExpedienteRepository.findById(id)
                        .orElseThrow(() -> new RecursoNoEncontradoException("EstadoExpediente", id)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EstadoExpedienteResponseDTO> listarTodos() {
        return estadoExpedienteRepository.findAll().stream()
                .map(estadoExpedienteMapper::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional
    @Auditable(entidad = "EstadoExpediente", accion = AccionAuditoria.ELIMINAR)
    public void eliminar(UUID id) {
        if (!estadoExpedienteRepository.existsById(id)) {
            throw new RecursoNoEncontradoException("EstadoExpediente", id);
        }
        if (expedienteRepository.existsByEstadoId(id)) {
            throw new RecursoEnUsoException("No se puede eliminar el estado: hay expedientes que lo usan");
        }
        estadoExpedienteRepository.deleteById(id);
    }
}
