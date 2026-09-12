package com.municipalidadsanjose.archivo.service.impl;

import com.municipalidadsanjose.archivo.audit.Auditable;
import com.municipalidadsanjose.archivo.dto.archivocentral.ArchivoCentralRequestDTO;
import com.municipalidadsanjose.archivo.dto.archivocentral.ArchivoCentralResponseDTO;
import com.municipalidadsanjose.archivo.entity.ArchivoCentral;
import com.municipalidadsanjose.archivo.enums.AccionAuditoria;
import com.municipalidadsanjose.archivo.exception.RecursoDuplicadoException;
import com.municipalidadsanjose.archivo.exception.RecursoEnUsoException;
import com.municipalidadsanjose.archivo.exception.RecursoNoEncontradoException;
import com.municipalidadsanjose.archivo.mapper.ArchivoCentralMapper;
import com.municipalidadsanjose.archivo.repository.ArchivoCentralRepository;
import com.municipalidadsanjose.archivo.repository.EstanteRepository;
import com.municipalidadsanjose.archivo.service.ArchivoCentralService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ArchivoCentralServiceImpl implements ArchivoCentralService {

    private final ArchivoCentralRepository archivoCentralRepository;
    private final ArchivoCentralMapper archivoCentralMapper;
    private final EstanteRepository estanteRepository;

    public ArchivoCentralServiceImpl(ArchivoCentralRepository archivoCentralRepository,
                                      ArchivoCentralMapper archivoCentralMapper,
                                      EstanteRepository estanteRepository) {
        this.archivoCentralRepository = archivoCentralRepository;
        this.archivoCentralMapper = archivoCentralMapper;
        this.estanteRepository = estanteRepository;
    }

    @Override
    @Transactional
    @Auditable(entidad = "ArchivoCentral", accion = AccionAuditoria.CREAR)
    public ArchivoCentralResponseDTO crear(ArchivoCentralRequestDTO dto) {
        if (archivoCentralRepository.existsByNombre(dto.nombre())) {
            throw new RecursoDuplicadoException("Ya existe un archivo central con el nombre: " + dto.nombre());
        }
        ArchivoCentral guardado = archivoCentralRepository.save(archivoCentralMapper.toEntity(dto));
        return archivoCentralMapper.toResponseDTO(guardado);
    }

    @Override
    @Transactional
    @Auditable(entidad = "ArchivoCentral", accion = AccionAuditoria.MODIFICAR)
    public ArchivoCentralResponseDTO actualizar(UUID id, ArchivoCentralRequestDTO dto) {
        ArchivoCentral archivoCentral = archivoCentralRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("ArchivoCentral", id));

        if (!archivoCentral.getNombre().equals(dto.nombre()) && archivoCentralRepository.existsByNombre(dto.nombre())) {
            throw new RecursoDuplicadoException("Ya existe un archivo central con el nombre: " + dto.nombre());
        }

        archivoCentral.setNombre(dto.nombre());
        return archivoCentralMapper.toResponseDTO(archivoCentral);
    }

    @Override
    @Transactional(readOnly = true)
    public ArchivoCentralResponseDTO buscarPorId(UUID id) {
        return archivoCentralMapper.toResponseDTO(
                archivoCentralRepository.findById(id)
                        .orElseThrow(() -> new RecursoNoEncontradoException("ArchivoCentral", id)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArchivoCentralResponseDTO> listarTodos() {
        return archivoCentralRepository.findAll().stream()
                .map(archivoCentralMapper::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional
    @Auditable(entidad = "ArchivoCentral", accion = AccionAuditoria.ELIMINAR)
    public void eliminar(UUID id) {
        if (!archivoCentralRepository.existsById(id)) {
            throw new RecursoNoEncontradoException("ArchivoCentral", id);
        }
        if (estanteRepository.existsByArchivoCentralId(id)) {
            throw new RecursoEnUsoException("No se puede eliminar el archivo central: tiene estantes registrados");
        }
        archivoCentralRepository.deleteById(id);
    }
}
