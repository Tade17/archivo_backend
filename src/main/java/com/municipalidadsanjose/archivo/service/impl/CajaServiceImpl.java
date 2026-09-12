package com.municipalidadsanjose.archivo.service.impl;

import com.municipalidadsanjose.archivo.audit.Auditable;
import com.municipalidadsanjose.archivo.dto.caja.CajaRequestDTO;
import com.municipalidadsanjose.archivo.dto.caja.CajaResponseDTO;
import com.municipalidadsanjose.archivo.entity.Caja;
import com.municipalidadsanjose.archivo.entity.Nivel;
import com.municipalidadsanjose.archivo.enums.AccionAuditoria;
import com.municipalidadsanjose.archivo.exception.RecursoDuplicadoException;
import com.municipalidadsanjose.archivo.exception.RecursoEnUsoException;
import com.municipalidadsanjose.archivo.exception.RecursoNoEncontradoException;
import com.municipalidadsanjose.archivo.mapper.CajaMapper;
import com.municipalidadsanjose.archivo.repository.CajaRepository;
import com.municipalidadsanjose.archivo.repository.ExpedienteRepository;
import com.municipalidadsanjose.archivo.repository.NivelRepository;
import com.municipalidadsanjose.archivo.service.CajaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CajaServiceImpl implements CajaService {

    private final CajaRepository cajaRepository;
    private final NivelRepository nivelRepository;
    private final ExpedienteRepository expedienteRepository;
    private final CajaMapper cajaMapper;

    public CajaServiceImpl(CajaRepository cajaRepository,
                            NivelRepository nivelRepository,
                            ExpedienteRepository expedienteRepository,
                            CajaMapper cajaMapper) {
        this.cajaRepository = cajaRepository;
        this.nivelRepository = nivelRepository;
        this.expedienteRepository = expedienteRepository;
        this.cajaMapper = cajaMapper;
    }

    @Override
    @Transactional
    @Auditable(entidad = "Caja", accion = AccionAuditoria.CREAR)
    public CajaResponseDTO crear(CajaRequestDTO dto) {
        Nivel nivel = nivelRepository.findById(dto.nivelId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Nivel", dto.nivelId()));

        if (cajaRepository.existsByNivelIdAndCodigo(dto.nivelId(), dto.codigo())) {
            throw new RecursoDuplicadoException("Ya existe una caja con el código '" + dto.codigo() + "' en ese nivel");
        }

        Caja guardada = cajaRepository.save(cajaMapper.toEntity(dto, nivel));
        return cajaMapper.toResponseDTO(guardada);
    }

    @Override
    @Transactional
    @Auditable(entidad = "Caja", accion = AccionAuditoria.MODIFICAR)
    public CajaResponseDTO actualizar(UUID id, CajaRequestDTO dto) {
        Caja caja = cajaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Caja", id));

        Nivel nivel = nivelRepository.findById(dto.nivelId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Nivel", dto.nivelId()));

        boolean cambia = !caja.getNivel().getId().equals(dto.nivelId())
                || !caja.getCodigo().equals(dto.codigo());
        if (cambia && cajaRepository.existsByNivelIdAndCodigo(dto.nivelId(), dto.codigo())) {
            throw new RecursoDuplicadoException("Ya existe una caja con el código '" + dto.codigo() + "' en ese nivel");
        }

        caja.setNivel(nivel);
        caja.setCodigo(dto.codigo());
        return cajaMapper.toResponseDTO(caja);
    }

    @Override
    @Transactional(readOnly = true)
    public CajaResponseDTO buscarPorId(UUID id) {
        return cajaMapper.toResponseDTO(
                cajaRepository.findById(id)
                        .orElseThrow(() -> new RecursoNoEncontradoException("Caja", id)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CajaResponseDTO> listarTodos() {
        return cajaRepository.findAll().stream()
                .map(cajaMapper::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CajaResponseDTO> listarPorNivel(UUID nivelId) {
        return cajaRepository.findByNivelId(nivelId).stream()
                .map(cajaMapper::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional
    @Auditable(entidad = "Caja", accion = AccionAuditoria.ELIMINAR)
    public void eliminar(UUID id) {
        if (!cajaRepository.existsById(id)) {
            throw new RecursoNoEncontradoException("Caja", id);
        }
        if (expedienteRepository.existsByCajaId(id)) {
            throw new RecursoEnUsoException("No se puede eliminar la caja: tiene expedientes archivados");
        }
        cajaRepository.deleteById(id);
    }
}
