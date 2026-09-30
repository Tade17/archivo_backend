package com.municipalidadsanjose.archivo.ocr;

import com.municipalidadsanjose.archivo.audit.Auditable;
import com.municipalidadsanjose.archivo.dto.documentodigital.DocumentoDigitalResponseDTO;
import com.municipalidadsanjose.archivo.entity.DocumentoDigital;
import com.municipalidadsanjose.archivo.enums.AccionAuditoria;
import com.municipalidadsanjose.archivo.enums.EstadoOcr;
import com.municipalidadsanjose.archivo.exception.RecursoNoEncontradoException;
import com.municipalidadsanjose.archivo.exception.SolicitudInvalidaException;
import com.municipalidadsanjose.archivo.mapper.DocumentoDigitalMapper;
import com.municipalidadsanjose.archivo.repository.DocumentoDigitalRepository;
import com.municipalidadsanjose.archivo.storage.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OcrPdfService {
    private final DocumentoDigitalRepository documentos;
    private final FileStorageService storage;
    private final OcrClient client;
    private final ObjectMapper mapper;
    private final DocumentoDigitalMapper dtoMapper;

    public OcrPdfService(DocumentoDigitalRepository documentos, FileStorageService storage,
                         OcrClient client, ObjectMapper mapper, DocumentoDigitalMapper dtoMapper) {
        this.documentos = documentos; this.storage = storage; this.client = client;
        this.mapper = mapper; this.dtoMapper = dtoMapper;
    }

    @Transactional(readOnly = true)
    public OcrLayoutDTO layout(UUID id) {
        var d = buscar(id);
        return new OcrLayoutDTO(leer(d), d.getOcrVersion());
    }

    @Transactional(readOnly = true)
    public String rutaPdf(UUID id) {
        var d = buscar(id);
        if (d.getRutaPdf() == null) throw new SolicitudInvalidaException("El PDF digitalizado todavía no está disponible.");
        return d.getRutaPdf();
    }

    @Transactional
    @Auditable(entidad = "DocumentoDigital", accion = AccionAuditoria.MODIFICAR)
    public DocumentoDigitalResponseDTO corregir(UUID id, OcrLayoutDTO solicitud) {
        var d = documentos.findLockedById(id).orElseThrow(() -> new RecursoNoEncontradoException("DocumentoDigital", id));
        if (d.getOcrEstado() == EstadoOcr.PENDIENTE || d.getOcrEstado() == EstadoOcr.PROCESANDO)
            throw new SolicitudInvalidaException("Espera a que termine la digitalización.");
        if (d.getOcrVersion() != solicitud.version())
            throw new SolicitudInvalidaException("Otro usuario modificó el texto. Recarga el documento antes de guardar.");
        validarCorreccion(leer(d), solicitud.layout());
        String json = mapper.writeValueAsString(solicitud.layout());
        byte[] pdf = client.generarPdf(storage.cargarComoRecurso(d.getRutaAlmacenamiento()),
                d.getTipoMime(), d.getNombreArchivo(), solicitud.layout());
        String nuevaRuta = storage.guardarPdf(d.getExpediente().getId().toString(), pdf);
        String anterior = d.getRutaPdf();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) storage.eliminar(nuevaRuta);
                else if (anterior != null) {
                    try { storage.eliminar(anterior); } catch (RuntimeException ignored) {
                        // La versión guardada sigue válida aunque falle la limpieza del derivado anterior.
                    }
                }
            }
        });
        String texto = solicitud.layout().stream().map(p -> p.blocks().stream()
                .map(OcrPagina.OcrBloque::text).collect(Collectors.joining("\n"))).collect(Collectors.joining("\n\n"));
        d.setRutaPdf(nuevaRuta); d.setOcrLayout(json); d.setOcrTexto(texto);
        d.setOcrRevisado(true); d.setOcrVersion(d.getOcrVersion() + 1);
        d.setOcrEstado(texto.isBlank() ? EstadoOcr.REQUIERE_REVISION : EstadoOcr.COMPLETADO);
        d.setOcrActualizadoEn(LocalDateTime.now()); d.setOcrError(null);
        documentos.flush();
        return dtoMapper.toResponseDTO(d);
    }

    static void validarCorreccion(List<OcrPagina> original, List<OcrPagina> nueva) {
        if (nueva == null || original.isEmpty() || nueva.size() != original.size()) invalidar();
        for (int i = 0; i < original.size(); i++) {
            var a = original.get(i); var b = nueva.get(i);
            if (b == null || a.width() != b.width() || a.height() != b.height()
                    || b.blocks() == null || a.blocks().size() != b.blocks().size()) invalidar();
            for (int j = 0; j < a.blocks().size(); j++) {
                var x = a.blocks().get(j); var y = b.blocks().get(j);
                if (y == null || !x.id().equals(y.id()) || x.x() != y.x() || x.y() != y.y()
                        || x.width() != y.width() || x.height() != y.height() || x.confidence() != y.confidence()
                        || y.text() == null || y.text().length() > 10000) invalidar();
            }
        }
    }
    private static void invalidar() { throw new SolicitudInvalidaException("Solo puedes corregir el texto de los fragmentos existentes."); }
    private DocumentoDigital buscar(UUID id) { return documentos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("DocumentoDigital", id)); }
    private List<OcrPagina> leer(DocumentoDigital d) {
        if (d.getOcrLayout() == null) return List.of();
        return List.of(mapper.readValue(d.getOcrLayout(), OcrPagina[].class));
    }
}
