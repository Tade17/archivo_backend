package com.municipalidadsanjose.archivo.ocr;

import com.municipalidadsanjose.archivo.entity.DocumentoDigital;
import com.municipalidadsanjose.archivo.enums.EstadoOcr;
import com.municipalidadsanjose.archivo.exception.RecursoNoEncontradoException;
import com.municipalidadsanjose.archivo.exception.SolicitudInvalidaException;
import com.municipalidadsanjose.archivo.repository.DocumentoDigitalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OcrService {

    private final DocumentoDigitalRepository documentos;
    private final ApplicationEventPublisher events;
    private final boolean enabled;

    public OcrService(DocumentoDigitalRepository documentos,
                      ApplicationEventPublisher events,
                      @Value("${app.ocr.enabled:true}") boolean enabled) {
        this.documentos = documentos;
        this.events = events;
        this.enabled = enabled;
    }

    public void solicitar(UUID documentoId) {
        if (enabled) events.publishEvent(new OcrSolicitadoEvent(documentoId));
    }

    @Transactional
    public void reintentar(UUID documentoId) {
        DocumentoDigital documento = documentos.findById(documentoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("DocumentoDigital", documentoId));
        if (documento.getOcrEstado() == EstadoOcr.PROCESANDO) {
            throw new SolicitudInvalidaException("El documento ya se está procesando.");
        }
        documentos.marcarPendiente(documentoId, LocalDateTime.now());
        solicitar(documentoId);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recuperarPendientes() {
        if (!enabled) return;
        documentos.recuperarProcesamientosInterrumpidos(LocalDateTime.now());
        List<UUID> pendientes = documentos.findIdsByOcrEstadoIn(List.of(EstadoOcr.PENDIENTE));
        pendientes.forEach(this::solicitar);
    }
}
