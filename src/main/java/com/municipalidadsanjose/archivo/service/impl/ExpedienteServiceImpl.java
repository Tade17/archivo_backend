package com.municipalidadsanjose.archivo.service.impl;

import com.municipalidadsanjose.archivo.audit.Auditable;
import com.municipalidadsanjose.archivo.dto.expediente.ExpedienteRequestDTO;
import com.municipalidadsanjose.archivo.dto.expediente.ExpedienteResponseDTO;
import com.municipalidadsanjose.archivo.entity.*;
import com.municipalidadsanjose.archivo.enums.AccionAuditoria;
import com.municipalidadsanjose.archivo.enums.EstadoDigitalizacion;
import com.municipalidadsanjose.archivo.exception.RecursoDuplicadoException;
import com.municipalidadsanjose.archivo.exception.RecursoNoEncontradoException;
import com.municipalidadsanjose.archivo.exception.SolicitudInvalidaException;
import com.municipalidadsanjose.archivo.mapper.ExpedienteMapper;
import com.municipalidadsanjose.archivo.repository.*;
import com.municipalidadsanjose.archivo.service.ExpedienteService;
import com.municipalidadsanjose.archivo.storage.FileStorageService;
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
    private final ContenedorRepository contenedorRepository;
    private final UsuarioRepository usuarioRepository;
    private final TagRepository tagRepository;
    private final ExpedienteMapper expedienteMapper;
    private final DocumentoDigitalRepository documentoDigitalRepository;
    private final PrestamoRepository prestamoRepository;
    private final FileStorageService fileStorageService;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    public ExpedienteServiceImpl(ExpedienteRepository expedienteRepository,
                                  AreaResponsableRepository areaResponsableRepository,
                                  TipoDocumentalRepository tipoDocumentalRepository,
                                  EstadoExpedienteRepository estadoExpedienteRepository,
                                  ContenedorRepository contenedorRepository,
                                  UsuarioRepository usuarioRepository,
                                  TagRepository tagRepository,
                                  ExpedienteMapper expedienteMapper,
                                  DocumentoDigitalRepository documentoDigitalRepository,
                                  PrestamoRepository prestamoRepository,
                                  FileStorageService fileStorageService,
                                  tools.jackson.databind.ObjectMapper objectMapper) {
        this.expedienteRepository = expedienteRepository;
        this.areaResponsableRepository = areaResponsableRepository;
        this.tipoDocumentalRepository = tipoDocumentalRepository;
        this.estadoExpedienteRepository = estadoExpedienteRepository;
        this.contenedorRepository = contenedorRepository;
        this.usuarioRepository = usuarioRepository;
        this.tagRepository = tagRepository;
        this.expedienteMapper = expedienteMapper;
        this.documentoDigitalRepository = documentoDigitalRepository;
        this.prestamoRepository = prestamoRepository;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
    }

    // Un contenedor COMPLETADO ya tiene todos sus documentos: para agregar uno
    // faltante primero hay que reabrirlo (queda en auditoría quién lo hizo).
    private Contenedor contenedorAbierto(UUID contenedorId) {
        Contenedor contenedor = contenedorRepository.findById(contenedorId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Contenedor", contenedorId));
        if (contenedor.getEstado() == EstadoDigitalizacion.COMPLETADO) {
            throw new SolicitudInvalidaException(
                    "El contenedor está marcado como completado. Reábrelo para agregar documentos.");
        }
        return contenedor;
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
        Contenedor contenedor = contenedorAbierto(dto.contenedorId());
        Usuario creadoPor = usuarioRepository.findById(dto.creadoPorId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", dto.creadoPorId()));

        Expediente expediente = expedienteMapper.toEntity(dto, areaDestino, tipo, estado, contenedor, creadoPor);
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
        Contenedor contenedor = expediente.getContenedor().getId().equals(dto.contenedorId())
                ? expediente.getContenedor()
                : contenedorAbierto(dto.contenedorId());
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
        expediente.setDestinatario(dto.destinatario());
        expediente.setContenedor(contenedor);
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
    @Transactional
    @Auditable(entidad = "Expediente", accion = AccionAuditoria.ELIMINAR)
    public void eliminar(UUID id) {
        Expediente expediente = expedienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente", id));
        if (prestamoRepository.existsByExpedienteId(id)) {
            throw new SolicitudInvalidaException("No se puede eliminar: el expediente tiene préstamos registrados.");
        }
        java.util.List<DocumentoDigital> documentos = documentoDigitalRepository.findByExpedienteId(id);
        documentoDigitalRepository.deleteAll(documentos);
        expedienteRepository.delete(expediente);
        expedienteRepository.flush();
        // Los archivos físicos se borran al final: si la transacción fallara
        // antes, no queda una fila apuntando a un archivo que ya no existe.
        for (DocumentoDigital d : documentos) {
            fileStorageService.eliminar(d.getRutaAlmacenamiento());
            if (d.getRutaPdf() != null) fileStorageService.eliminar(d.getRutaPdf());
            // Imágenes originales conservadas cuando se unieron en un solo PDF.
            com.municipalidadsanjose.archivo.service.DocumentoImageBatchService
                    .readManifest(d, objectMapper)
                    .forEach(original -> fileStorageService.eliminar(original.rutaAlmacenamiento()));
        }
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
