package com.municipalidadsanjose.archivo.service.impl;

import com.municipalidadsanjose.archivo.audit.Auditable;
import com.municipalidadsanjose.archivo.dto.documentodigital.DocumentoDigitalActualizarDTO;
import com.municipalidadsanjose.archivo.dto.documentodigital.DocumentoDigitalRequestDTO;
import com.municipalidadsanjose.archivo.dto.documentodigital.DocumentoDigitalResponseDTO;
import com.municipalidadsanjose.archivo.entity.DocumentoDigital;
import com.municipalidadsanjose.archivo.entity.Expediente;
import com.municipalidadsanjose.archivo.entity.Usuario;
import com.municipalidadsanjose.archivo.enums.AccionAuditoria;
import com.municipalidadsanjose.archivo.exception.RecursoNoEncontradoException;
import com.municipalidadsanjose.archivo.mapper.DocumentoDigitalMapper;
import com.municipalidadsanjose.archivo.repository.DocumentoDigitalRepository;
import com.municipalidadsanjose.archivo.repository.ExpedienteRepository;
import com.municipalidadsanjose.archivo.repository.UsuarioRepository;
import com.municipalidadsanjose.archivo.service.DocumentoDigitalService;
import com.municipalidadsanjose.archivo.storage.FileStorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DocumentoDigitalServiceImpl implements DocumentoDigitalService {

    private final DocumentoDigitalRepository documentoDigitalRepository;
    private final ExpedienteRepository expedienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final DocumentoDigitalMapper documentoDigitalMapper;
    private final FileStorageService fileStorageService;

    public DocumentoDigitalServiceImpl(DocumentoDigitalRepository documentoDigitalRepository,
                                        ExpedienteRepository expedienteRepository,
                                        UsuarioRepository usuarioRepository,
                                        DocumentoDigitalMapper documentoDigitalMapper,
                                        FileStorageService fileStorageService) {
        this.documentoDigitalRepository = documentoDigitalRepository;
        this.expedienteRepository = expedienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.documentoDigitalMapper = documentoDigitalMapper;
        this.fileStorageService = fileStorageService;
    }

    @Override
    @Transactional
    @Auditable(entidad = "DocumentoDigital", accion = AccionAuditoria.CREAR)
    public DocumentoDigitalResponseDTO crear(DocumentoDigitalRequestDTO dto) {
        Expediente expediente = expedienteRepository.findById(dto.expedienteId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente", dto.expedienteId()));
        Usuario tecnicoResponsable = usuarioRepository.findById(dto.tecnicoResponsableId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", dto.tecnicoResponsableId()));

        // flush explícito: sin esto, fechaDigitalizacion/fechaActualizacion (generadas por
        // Hibernate con @CreationTimestamp/@UpdateTimestamp) quedan null en la respuesta,
        // porque el INSERT recién se ejecuta al hacer commit, no al llamar a save().
        DocumentoDigital guardado = documentoDigitalRepository.saveAndFlush(
                documentoDigitalMapper.toEntity(dto, expediente, tecnicoResponsable));
        return documentoDigitalMapper.toResponseDTO(guardado);
    }

    @Override
    @Transactional
    @Auditable(entidad = "DocumentoDigital", accion = AccionAuditoria.MODIFICAR)
    public DocumentoDigitalResponseDTO actualizar(UUID id, DocumentoDigitalActualizarDTO dto) {
        DocumentoDigital documento = documentoDigitalRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("DocumentoDigital", id));

        // El expedienteId no se reasigna en un update: un documento digitalizado
        // pertenece al expediente con el que se creó, no se "muda" de expediente.
        // Tampoco se reasignan rutaAlmacenamiento/tipoMime/hashSha256: quedan
        // fijados por el archivo real subido en crear(), no son editables a mano.
        Usuario tecnicoResponsable = usuarioRepository.findById(dto.tecnicoResponsableId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", dto.tecnicoResponsableId()));

        documento.setNombreArchivo(dto.nombreArchivo());
        documento.setTecnicoResponsable(tecnicoResponsable);
        documento.setEscanerUtilizado(dto.escanerUtilizado());
        documento.setResolucionDpi(dto.resolucionDpi());
        documento.setFormatoSalida(dto.formatoSalida());
        documento.setOcrTexto(dto.ocrTexto());

        // idem que en crear(): sin flush, fechaActualizacion en la respuesta
        // muestra el valor viejo en vez del que Hibernate acaba de generar.
        documentoDigitalRepository.flush();
        return documentoDigitalMapper.toResponseDTO(documento);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentoDigitalResponseDTO buscarPorId(UUID id) {
        return documentoDigitalMapper.toResponseDTO(
                documentoDigitalRepository.findById(id)
                        .orElseThrow(() -> new RecursoNoEncontradoException("DocumentoDigital", id)));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DocumentoDigitalResponseDTO> listarTodos(Pageable pageable) {
        return documentoDigitalRepository.findAll(pageable).map(documentoDigitalMapper::toResponseDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DocumentoDigitalResponseDTO> listarPorExpediente(UUID expedienteId, Pageable pageable) {
        return documentoDigitalRepository.findByExpedienteId(expedienteId, pageable)
                .map(documentoDigitalMapper::toResponseDTO);
    }

    // No es readOnly a propósito: aunque el método en sí solo lee, @Auditable
    // graba una fila de auditoría dentro de esta misma transacción, y con
    // readOnly=true Hibernate pone el flush en modo manual y ese INSERT nunca
    // se escribe (se pierde en silencio, sin ninguna excepción).
    @Override
    @Transactional
    @Auditable(entidad = "DocumentoDigital", accion = AccionAuditoria.DESCARGAR)
    public DocumentoDigitalResponseDTO registrarDescarga(UUID id) {
        return documentoDigitalMapper.toResponseDTO(
                documentoDigitalRepository.findById(id)
                        .orElseThrow(() -> new RecursoNoEncontradoException("DocumentoDigital", id)));
    }

    @Override
    @Transactional
    @Auditable(entidad = "DocumentoDigital", accion = AccionAuditoria.ELIMINAR)
    public void eliminar(UUID id) {
        DocumentoDigital documento = documentoDigitalRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("DocumentoDigital", id));
        // Borra primero la fila y recién después el archivo físico: si el archivo
        // no se pudiera borrar por algún motivo, preferimos un huérfano en disco
        // (se puede limpiar después) antes que una fila fantasma sin archivo.
        documentoDigitalRepository.delete(documento);
        fileStorageService.eliminar(documento.getRutaAlmacenamiento());
    }
}
