package com.municipalidadsanjose.archivo.service.impl;

import com.municipalidadsanjose.archivo.audit.Auditable;
import com.municipalidadsanjose.archivo.dto.tipodocumental.TipoDocumentalRequestDTO;
import com.municipalidadsanjose.archivo.dto.tipodocumental.TipoDocumentalResponseDTO;
import com.municipalidadsanjose.archivo.entity.TipoDocumental;
import com.municipalidadsanjose.archivo.enums.AccionAuditoria;
import com.municipalidadsanjose.archivo.exception.RecursoDuplicadoException;
import com.municipalidadsanjose.archivo.exception.RecursoEnUsoException;
import com.municipalidadsanjose.archivo.exception.RecursoNoEncontradoException;
import com.municipalidadsanjose.archivo.mapper.TipoDocumentalMapper;
import com.municipalidadsanjose.archivo.repository.ExpedienteRepository;
import com.municipalidadsanjose.archivo.repository.TipoDocumentalRepository;
import com.municipalidadsanjose.archivo.service.TipoDocumentalService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TipoDocumentalServiceImpl implements TipoDocumentalService {

    private final TipoDocumentalRepository tipoDocumentalRepository;
    private final TipoDocumentalMapper tipoDocumentalMapper;
    private final ExpedienteRepository expedienteRepository;

    public TipoDocumentalServiceImpl(TipoDocumentalRepository tipoDocumentalRepository,
                                      TipoDocumentalMapper tipoDocumentalMapper,
                                      ExpedienteRepository expedienteRepository) {
        this.tipoDocumentalRepository = tipoDocumentalRepository;
        this.tipoDocumentalMapper = tipoDocumentalMapper;
        this.expedienteRepository = expedienteRepository;
    }

    @Override
    @Transactional
    @Auditable(entidad = "TipoDocumental", accion = AccionAuditoria.CREAR)
    public TipoDocumentalResponseDTO crear(TipoDocumentalRequestDTO dto) {
        if (tipoDocumentalRepository.existsByNombre(dto.nombre())) {
            throw new RecursoDuplicadoException("Ya existe un tipo documental con el nombre: " + dto.nombre());
        }
        TipoDocumental guardado = tipoDocumentalRepository.save(tipoDocumentalMapper.toEntity(dto));
        return tipoDocumentalMapper.toResponseDTO(guardado);
    }

    @Override
    @Transactional
    @Auditable(entidad = "TipoDocumental", accion = AccionAuditoria.MODIFICAR)
    public TipoDocumentalResponseDTO actualizar(UUID id, TipoDocumentalRequestDTO dto) {
        TipoDocumental tipo = tipoDocumentalRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("TipoDocumental", id));

        if (!tipo.getNombre().equals(dto.nombre()) && tipoDocumentalRepository.existsByNombre(dto.nombre())) {
            throw new RecursoDuplicadoException("Ya existe un tipo documental con el nombre: " + dto.nombre());
        }

        tipo.setNombre(dto.nombre());
        return tipoDocumentalMapper.toResponseDTO(tipo);
    }

    @Override
    @Transactional(readOnly = true)
    public TipoDocumentalResponseDTO buscarPorId(UUID id) {
        return tipoDocumentalMapper.toResponseDTO(
                tipoDocumentalRepository.findById(id)
                        .orElseThrow(() -> new RecursoNoEncontradoException("TipoDocumental", id)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TipoDocumentalResponseDTO> listarTodos() {
        return tipoDocumentalRepository.findAll().stream()
                .map(tipoDocumentalMapper::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional
    @Auditable(entidad = "TipoDocumental", accion = AccionAuditoria.ELIMINAR)
    public void eliminar(UUID id) {
        if (!tipoDocumentalRepository.existsById(id)) {
            throw new RecursoNoEncontradoException("TipoDocumental", id);
        }
        if (expedienteRepository.existsByTipoId(id)) {
            throw new RecursoEnUsoException("No se puede eliminar el tipo documental: hay expedientes que lo usan");
        }
        tipoDocumentalRepository.deleteById(id);
    }
}
