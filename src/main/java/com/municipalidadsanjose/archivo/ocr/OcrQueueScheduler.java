package com.municipalidadsanjose.archivo.ocr;

import com.municipalidadsanjose.archivo.repository.DocumentoDigitalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class OcrQueueScheduler {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OcrQueueScheduler.class);
    private final DocumentoDigitalRepository documents;
    private final OcrWorker worker;
    private final boolean enabled;
    private final int leaseSeconds;
    private final String owner = UUID.randomUUID().toString().replace("-", "");
    private final AtomicReference<UUID> active = new AtomicReference<>();

    public OcrQueueScheduler(DocumentoDigitalRepository documents, OcrWorker worker,
                             @Value("${app.ocr.enabled:true}") boolean enabled,
                             @Value("${app.ocr.lease-seconds:120}") int leaseSeconds) {
        this.documents = documents;
        this.worker = worker;
        this.enabled = enabled;
        this.leaseSeconds = Math.max(30, Math.min(leaseSeconds, 600));
    }

    @Scheduled(fixedDelayString = "#{T(java.lang.Math).max(500, ${app.ocr.poll-millis:2000})}")
    public synchronized void drain() {
        if (!enabled) return;
        try {
            var claimed = documents.reclamarSiguiente(owner, leaseSeconds);
            if (claimed.isEmpty()) return;
            UUID id = claimed.get(0);
            active.set(id);
            try { worker.procesarReclamado(id, owner); }
            finally { active.set(null); }
        } catch (RuntimeException error) {
            log.warn("event=ocr_queue_failed category={}", error.getClass().getSimpleName());
        }
    }

    @Scheduled(fixedDelay = 10000)
    public void heartbeat() {
        UUID id = active.get();
        if (id == null) return;
        try { documents.renovarLease(id, owner, leaseSeconds); }
        catch (RuntimeException error) {
            log.warn("event=ocr_heartbeat_failed category={}", error.getClass().getSimpleName());
        }
    }
}
