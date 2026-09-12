package com.municipalidadsanjose.archivo.service.impl;

import com.municipalidadsanjose.archivo.audit.Auditable;
import com.municipalidadsanjose.archivo.dto.nivel.NivelRequestDTO;
import com.municipalidadsanjose.archivo.dto.nivel.NivelResponseDTO;
import com.municipalidadsanjose.archivo.entity.Estante;
import com.municipalidadsanjose.archivo.entity.Nivel;
import com.municipalidadsanjose.archivo.enums.AccionAuditoria;
import com.municipalidadsanjose.archivo.exception.RecursoDuplicadoException;
import com.municipalidadsanjose.archivo.exception.RecursoEnUsoException;
import com.municipalidadsanjose.archivo.exception.RecursoNoEncontradoException;
import com.municipalidadsanjose.archivo.mapper.NivelMapper;
import com.municipalidadsanjose.archivo.repository.CajaRepository;
import com.municipalidadsanjose.archivo.repository.EstanteRepository;
import com.municipalidadsanjose.archivo.repository.NivelRepository;
import com.municipalidadsanjose.archivo.service.NivelService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class NivelServiceImpl implements NivelService {

    private final NivelRepository nivelRepository;
    private final EstanteRepository estanteRepository;
    private final CajaRepository cajaRepository;
    private final NivelMapper nivelMapper;

    public NivelServiceImpl(NivelRepository nivelRepository,
                             EstanteRepository estanteRepository,
                             CajaRepository cajaRepository,
                             NivelMapper nivelMapper) {
        this.nivelRepository = nivelRepository;
        this.estanteRepository = estanteRepository;
        this.cajaRepository = cajaRepository;
        this.nivelMapper = nivelMapper;
    }

    @Override
    @Transactional
    @Auditable(entidad = "Nivel", accion = AccionAuditoria.CREAR)
    public NivelResponseDTO crear(NivelRequestDTO dto) {
        Estante estante = estanteRepository.findById(dto.estanteId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Estante", dto.estanteId()));

        if (nivelRepository.existsByEstanteIdAndCodigo(dto.estanteId(), dto.codigo())) {
            throw new RecursoDuplicadoException("Ya existe un nivel con el código '" + dto.codigo() + "' en ese estante");
        }

        Nivel guardado = nivelRepository.save(nivelMapper.toEntity(dto, estante));
        return nivelMapper.toResponseDTO(guardado);
    }

    @Override
    @Transactional
    @Auditable(entidad = "Nivel", accion = AccionAuditoria.MODIFICAR)
    public NivelResponseDTO actualizar(UUID id, NivelRequestDTO dto) {
        Nivel nivel = nivelRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Nivel", id));

        Estante estante = estanteRepository.findById(dto.estanteId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Estante", dto.estanteId()));

        boolean cambia = !nivel.getEstante().getId().equals(dto.estanteId())
                || !nivel.getCodigo().equals(dto.codigo());
        if (cambia && nivelRepository.existsByEstanteIdAndCodigo(dto.estanteId(), dto.codigo())) {
            throw new RecursoDuplicadoException("Ya existe un nivel con el código '" + dto.codigo() + "' en ese estante");
        }

        nivel.setEstante(estante);
        nivel.setCodigo(dto.codigo());
        return nivelMapper.toResponseDTO(nivel);
    }

    @Override
    @Transactional(readOnly = true)
    public NivelResponseDTO buscarPorId(UUID id) {
        return nivelMapper.toResponseDTO(
                nivelRepository.findById(id)
                        .orElseThrow(() -> new RecursoNoEncontradoException("Nivel", id)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<NivelResponseDTO> listarTodos() {
        return nivelRepository.findAll().stream()
                .map(nivelMapper::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NivelResponseDTO> listarPorEstante(UUID estanteId) {
        return nivelRepository.findByEstanteId(estanteId).stream()
                .map(nivelMapper::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional
    @Auditable(entidad = "Nivel", accion = AccionAuditoria.ELIMINAR)
    public void eliminar(UUID id) {
        if (!nivelRepository.existsById(id)) {
            throw new RecursoNoEncontradoException("Nivel", id);
        }
        if (cajaRepository.existsByNivelId(id)) {
            throw new RecursoEnUsoException("No se puede eliminar el nivel: tiene cajas registradas");
        }
        nivelRepository.deleteById(id);
    }
}
