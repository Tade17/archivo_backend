package com.municipalidadsanjose.archivo.service.impl;

import com.municipalidadsanjose.archivo.audit.Auditable;
import com.municipalidadsanjose.archivo.dto.estante.EstanteRequestDTO;
import com.municipalidadsanjose.archivo.dto.estante.EstanteResponseDTO;
import com.municipalidadsanjose.archivo.entity.ArchivoCentral;
import com.municipalidadsanjose.archivo.entity.Estante;
import com.municipalidadsanjose.archivo.enums.AccionAuditoria;
import com.municipalidadsanjose.archivo.exception.RecursoDuplicadoException;
import com.municipalidadsanjose.archivo.exception.RecursoEnUsoException;
import com.municipalidadsanjose.archivo.exception.RecursoNoEncontradoException;
import com.municipalidadsanjose.archivo.mapper.EstanteMapper;
import com.municipalidadsanjose.archivo.repository.ArchivoCentralRepository;
import com.municipalidadsanjose.archivo.repository.EstanteRepository;
import com.municipalidadsanjose.archivo.repository.NivelRepository;
import com.municipalidadsanjose.archivo.service.EstanteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class EstanteServiceImpl implements EstanteService {

    private final EstanteRepository estanteRepository;
    private final ArchivoCentralRepository archivoCentralRepository;
    private final NivelRepository nivelRepository;
    private final EstanteMapper estanteMapper;

    public EstanteServiceImpl(EstanteRepository estanteRepository,
                               ArchivoCentralRepository archivoCentralRepository,
                               NivelRepository nivelRepository,
                               EstanteMapper estanteMapper) {
        this.estanteRepository = estanteRepository;
        this.archivoCentralRepository = archivoCentralRepository;
        this.nivelRepository = nivelRepository;
        this.estanteMapper = estanteMapper;
    }

    @Override
    @Transactional
    @Auditable(entidad = "Estante", accion = AccionAuditoria.CREAR)
    public EstanteResponseDTO crear(EstanteRequestDTO dto) {
        ArchivoCentral archivoCentral = archivoCentralRepository.findById(dto.archivoCentralId())
                .orElseThrow(() -> new RecursoNoEncontradoException("ArchivoCentral", dto.archivoCentralId()));

        if (estanteRepository.existsByArchivoCentralIdAndCodigo(dto.archivoCentralId(), dto.codigo())) {
            throw new RecursoDuplicadoException("Ya existe un estante con el código '" + dto.codigo() + "' en ese archivo central");
        }

        Estante guardado = estanteRepository.save(estanteMapper.toEntity(dto, archivoCentral));
        return estanteMapper.toResponseDTO(guardado);
    }

    @Override
    @Transactional
    @Auditable(entidad = "Estante", accion = AccionAuditoria.MODIFICAR)
    public EstanteResponseDTO actualizar(UUID id, EstanteRequestDTO dto) {
        Estante estante = estanteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Estante", id));

        ArchivoCentral archivoCentral = archivoCentralRepository.findById(dto.archivoCentralId())
                .orElseThrow(() -> new RecursoNoEncontradoException("ArchivoCentral", dto.archivoCentralId()));

        boolean cambia = !estante.getArchivoCentral().getId().equals(dto.archivoCentralId())
                || !estante.getCodigo().equals(dto.codigo());
        if (cambia && estanteRepository.existsByArchivoCentralIdAndCodigo(dto.archivoCentralId(), dto.codigo())) {
            throw new RecursoDuplicadoException("Ya existe un estante con el código '" + dto.codigo() + "' en ese archivo central");
        }

        estante.setArchivoCentral(archivoCentral);
        estante.setCodigo(dto.codigo());
        return estanteMapper.toResponseDTO(estante);
    }

    @Override
    @Transactional(readOnly = true)
    public EstanteResponseDTO buscarPorId(UUID id) {
        return estanteMapper.toResponseDTO(
                estanteRepository.findById(id)
                        .orElseThrow(() -> new RecursoNoEncontradoException("Estante", id)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EstanteResponseDTO> listarTodos() {
        return estanteRepository.findAll().stream()
                .map(estanteMapper::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EstanteResponseDTO> listarPorArchivoCentral(UUID archivoCentralId) {
        return estanteRepository.findByArchivoCentralId(archivoCentralId).stream()
                .map(estanteMapper::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional
    @Auditable(entidad = "Estante", accion = AccionAuditoria.ELIMINAR)
    public void eliminar(UUID id) {
        if (!estanteRepository.existsById(id)) {
            throw new RecursoNoEncontradoException("Estante", id);
        }
        if (nivelRepository.existsByEstanteId(id)) {
            throw new RecursoEnUsoException("No se puede eliminar el estante: tiene niveles registrados");
        }
        estanteRepository.deleteById(id);
    }
}
