package com.municipalidadsanjose.archivo.service.impl;

import com.municipalidadsanjose.archivo.audit.Auditable;
import com.municipalidadsanjose.archivo.dto.expediente.ExpedienteRequestDTO;
import com.municipalidadsanjose.archivo.dto.expediente.ExpedienteResponseDTO;
import com.municipalidadsanjose.archivo.entity.*;
import com.municipalidadsanjose.archivo.enums.AccionAuditoria;
import com.municipalidadsanjose.archivo.exception.RecursoDuplicadoException;
import com.municipalidadsanjose.archivo.exception.RecursoNoEncontradoException;
import com.municipalidadsanjose.archivo.mapper.ExpedienteMapper;
import com.municipalidadsanjose.archivo.repository.*;
import com.municipalidadsanjose.archivo.service.ExpedienteService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class ExpedienteServiceImpl implements ExpedienteService {

    private final ExpedienteRepository expedienteRepository;
    private final AreaResponsableRepository areaResponsableRepository;
    private final TipoDocumentalRepository tipoDocumentalRepository;
    private final EstadoExpedienteRepository estadoExpedienteRepository;
    private final CajaRepository cajaRepository;
    private final UsuarioRepository usuarioRepository;
    private final TagRepository tagRepository;
    private final ExpedienteMapper expedienteMapper;

    public ExpedienteServiceImpl(ExpedienteRepository expedienteRepository,
                                  AreaResponsableRepository areaResponsableRepository,
                                  TipoDocumentalRepository tipoDocumentalRepository,
                                  EstadoExpedienteRepository estadoExpedienteRepository,
                                  CajaRepository cajaRepository,
                                  UsuarioRepository usuarioRepository,
                                  TagRepository tagRepository,
                                  ExpedienteMapper expedienteMapper) {
        this.expedienteRepository = expedienteRepository;
        this.areaResponsableRepository = areaResponsableRepository;
        this.tipoDocumentalRepository = tipoDocumentalRepository;
        this.estadoExpedienteRepository = estadoExpedienteRepository;
        this.cajaRepository = cajaRepository;
        this.usuarioRepository = usuarioRepository;
        this.tagRepository = tagRepository;
        this.expedienteMapper = expedienteMapper;
    }

    private Set<Tag> resolverTags(Set<UUID> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<Tag> tags = new HashSet<>();
        for (UUID tagId : tagIds) {
            tags.add(tagRepository.findById(tagId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Tag", tagId)));
        }
        return tags;
    }

    @Override
    @Transactional
    @Auditable(entidad = "Expediente", accion = AccionAuditoria.CREAR)
    public ExpedienteResponseDTO crear(ExpedienteRequestDTO dto) {
        if (expedienteRepository.existsByCodigoUnico(dto.codigoUnico())) {
            throw new RecursoDuplicadoException("Ya existe un expediente con el código único: " + dto.codigoUnico());
        }

        AreaResponsable areaDestino = areaResponsableRepository.findById(dto.areaDestinoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("AreaResponsable", dto.areaDestinoId()));
        TipoDocumental tipo = tipoDocumentalRepository.findById(dto.tipoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("TipoDocumental", dto.tipoId()));
        EstadoExpediente estado = estadoExpedienteRepository.findById(dto.estadoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("EstadoExpediente", dto.estadoId()));
        Caja caja = cajaRepository.findById(dto.cajaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Caja", dto.cajaId()));
        Usuario creadoPor = usuarioRepository.findById(dto.creadoPorId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", dto.creadoPorId()));

        Expediente expediente = expedienteMapper.toEntity(dto, areaDestino, tipo, estado, caja, creadoPor);
        expediente.getTags().addAll(resolverTags(dto.tagIds()));

        // flush explícito: sin esto, fechaRegistro/fechaActualizacion (generadas por
        // Hibernate con @CreationTimestamp/@UpdateTimestamp) quedan null en la respuesta,
        // porque el INSERT recién se ejecuta al hacer commit, no al llamar a save().
        Expediente guardado = expedienteRepository.saveAndFlush(expediente);
        return expedienteMapper.toResponseDTO(guardado);
    }

    @Override
    @Transactional
    @Auditable(entidad = "Expediente", accion = AccionAuditoria.MODIFICAR)
    public ExpedienteResponseDTO actualizar(UUID id, ExpedienteRequestDTO dto) {
        Expediente expediente = expedienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente", id));

        boolean cambiaCodigo = !expediente.getCodigoUnico().equals(dto.codigoUnico());
        if (cambiaCodigo && expedienteRepository.existsByCodigoUnico(dto.codigoUnico())) {
            throw new RecursoDuplicadoException("Ya existe un expediente con el código único: " + dto.codigoUnico());
        }

        AreaResponsable areaDestino = areaResponsableRepository.findById(dto.areaDestinoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("AreaResponsable", dto.areaDestinoId()));
        TipoDocumental tipo = tipoDocumentalRepository.findById(dto.tipoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("TipoDocumental", dto.tipoId()));
        EstadoExpediente estado = estadoExpedienteRepository.findById(dto.estadoId())
                .orElseThrow(() -> new RecursoNoEncontradoException("EstadoExpediente", dto.estadoId()));
        Caja caja = cajaRepository.findById(dto.cajaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Caja", dto.cajaId()));
        Usuario creadoPor = usuarioRepository.findById(dto.creadoPorId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", dto.creadoPorId()));

        expediente.setCodigoUnico(dto.codigoUnico());
        expediente.setNumeroDocumento(dto.numeroDocumento());
        expediente.setRemitente(dto.remitente());
        expediente.setAreaDestino(areaDestino);
        expediente.setTipo(tipo);
        expediente.setEstado(estado);
        expediente.setFechaDocumento(dto.fechaDocumento());
        expediente.setAsunto(dto.asunto());
        expediente.setGlosa(dto.glosa());
        expediente.setCaja(caja);
        expediente.setNumeroFolios(dto.numeroFolios());
        expediente.setCreadoPor(creadoPor);

        // Mutamos la colección existente en vez de reemplazar la referencia:
        // es más seguro para Hibernate detectar altas/bajas en el @ManyToMany.
        expediente.getTags().clear();
        expediente.getTags().addAll(resolverTags(dto.tagIds()));

        // idem que en crear(): sin flush, fechaActualizacion en la respuesta
        // muestra el valor viejo en vez del que Hibernate acaba de generar.
        expedienteRepository.flush();
        return expedienteMapper.toResponseDTO(expediente);
    }

    @Override
    @Transactional(readOnly = true)
    public ExpedienteResponseDTO buscarPorId(UUID id) {
        return expedienteMapper.toResponseDTO(
                expedienteRepository.findById(id)
                        .orElseThrow(() -> new RecursoNoEncontradoException("Expediente", id)));
    }

    @Override
    @Transactional(readOnly = true)
    public ExpedienteResponseDTO buscarPorCodigoUnico(String codigoUnico) {
        return expedienteMapper.toResponseDTO(
                expedienteRepository.findByCodigoUnico(codigoUnico)
                        .orElseThrow(() -> new RecursoNoEncontradoException(
                                "Expediente no encontrado con código único: " + codigoUnico)));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ExpedienteResponseDTO> listarTodos(Pageable pageable) {
        return expedienteRepository.findAll(pageable).map(expedienteMapper::toResponseDTO);
    }
}
