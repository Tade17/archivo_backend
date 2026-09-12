package com.municipalidadsanjose.archivo.service.impl;

import com.municipalidadsanjose.archivo.dto.auditoria.AuditoriaResponseDTO;
import com.municipalidadsanjose.archivo.exception.RecursoNoEncontradoException;
import com.municipalidadsanjose.archivo.mapper.AuditoriaMapper;
import com.municipalidadsanjose.archivo.repository.AuditoriaRepository;
import com.municipalidadsanjose.archivo.service.AuditoriaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AuditoriaServiceImpl implements AuditoriaService {

    private final AuditoriaRepository auditoriaRepository;
    private final AuditoriaMapper auditoriaMapper;

    public AuditoriaServiceImpl(AuditoriaRepository auditoriaRepository,
                                 AuditoriaMapper auditoriaMapper) {
        this.auditoriaRepository = auditoriaRepository;
        this.auditoriaMapper = auditoriaMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public AuditoriaResponseDTO buscarPorId(UUID id) {
        return auditoriaMapper.toResponseDTO(
                auditoriaRepository.findById(id)
                        .orElseThrow(() -> new RecursoNoEncontradoException("Auditoria", id)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditoriaResponseDTO> listarTodos() {
        return auditoriaRepository.findAll().stream()
                .map(auditoriaMapper::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditoriaResponseDTO> buscarPorEntidad(String entidadAfectada, UUID entidadId) {
        return auditoriaRepository.findByEntidadAfectadaAndEntidadId(entidadAfectada, entidadId).stream()
                .map(auditoriaMapper::toResponseDTO)
                .toList();
    }
}
