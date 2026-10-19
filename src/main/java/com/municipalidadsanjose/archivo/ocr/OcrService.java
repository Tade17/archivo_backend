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
    private final OcrClient client;

    public OcrService(DocumentoDigitalRepository documentos,
                      ApplicationEventPublisher events, OcrClient client,
                      @Value("${app.ocr.enabled:true}") boolean enabled) {
        this.documentos = documentos;
        this.events = events;
        this.enabled = enabled;
        this.client = client;
    }

    public void solicitar(UUID documentoId) {
        if (enabled) events.publishEvent(new OcrSolicitadoEvent(documentoId));
    }

    @Transactional
    public void reintentar(UUID documentoId) {
        DocumentoDigital documento = documentos.findLockedById(documentoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("DocumentoDigital", documentoId));
        if (documento.getOcrEstado() == EstadoOcr.PROCESANDO || documento.getOcrEstado() == EstadoOcr.PENDIENTE) {
            throw new SolicitudInvalidaException("El documento ya se está procesando.");
        }
        if (documento.isOcrRevisado()) {
            throw new SolicitudInvalidaException("El texto contiene correcciones guardadas. Reprocesarlo las reemplazaría; conserva y edita el PDF actual.");
        }
        String oldJob = documento.getOcrJobId(), oldCapability = documento.getOcrJobCapability();
        documentos.reiniciarJob(documentoId);
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { cancelarRemoto(oldJob, oldCapability); }
                });
        }
        solicitar(documentoId);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recuperarPendientes() {
        if (!enabled) return;
        documentos.programarPdfFaltantes();
    }

    public void cancelarRemoto(String job, String capability) {
        if (job == null || capability == null) return;
        try { client.cancelarJob(job, capability); }
        catch (RuntimeException error) {
            org.slf4j.LoggerFactory.getLogger(OcrService.class).warn("event=ocr_cancel_failed category={}", error.getClass().getSimpleName());
        }
    }
}
