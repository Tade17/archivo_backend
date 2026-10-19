package com.municipalidadsanjose.archivo.ocr;

import com.municipalidadsanjose.archivo.entity.DocumentoDigital;
import com.municipalidadsanjose.archivo.enums.EstadoOcr;
import com.municipalidadsanjose.archivo.repository.DocumentoDigitalRepository;
import com.municipalidadsanjose.archivo.storage.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class OcrWorker {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OcrWorker.class);
    private final DocumentoDigitalRepository documentos;
    private final FileStorageService storage;
    private final OcrClient client;
    private final boolean enabled;
    private final BigDecimal umbralRevision;
    private final tools.jackson.databind.ObjectMapper mapper;
    private static final int MAX_RETRIES = 5;

    public OcrWorker(DocumentoDigitalRepository documentos, FileStorageService storage, OcrClient client,
                     tools.jackson.databind.ObjectMapper mapper,
                     @Value("${app.ocr.enabled:true}") boolean enabled,
                     @Value("${app.ocr.review-threshold:0.75}") BigDecimal umbralRevision) {
        this.documentos = documentos; this.storage = storage; this.client = client;
        this.mapper = mapper; this.enabled = enabled; this.umbralRevision = umbralRevision;
    }

    public void procesarReclamado(UUID id, String owner) {
        if (!enabled) return;
        String newPath = null;
        DocumentoDigital document = null;
        String job = null;
        try {
            document = documentos.findForOcr(id).orElse(null);
            if (document == null || !owner.equals(document.getOcrLeaseOwner())) return;
            job = document.getOcrJobId();
            if (job == null) {
                String fingerprint = client.engineFingerprint();
                job = randomHex();
                String capability = randomHex() + randomHex();
                if (documentos.guardarJobIdentity(id, owner, job, capability, fingerprint) == 0) return;
                document.setOcrJobId(job); document.setOcrJobCapability(capability);
                document.setOcrEngineFingerprint(fingerprint);
            }
            OcrJobStatus status;
            try {
                status = client.consultarJob(job, document.getOcrJobCapability());
            } catch (OcrClient.OcrFailure error) {
                if (!error.code().equals("OCR_JOB_HTTP_404")) throw error;
                if (Boolean.TRUE.equals(document.getOcrJobSubmitted())) throw new JobRejected("OCR_JOB_EXPIRED");
                status = client.enviarJob(job, document.getOcrJobCapability(), document.getOcrEngineFingerprint(),
                        document.getHashSha256(), storage.cargarComoRecurso(document.getRutaAlmacenamiento()));
            }
            if (document.getOcrPaginasProcesadas() != null && status.completedPages() < document.getOcrPaginasProcesadas()
                    || document.getOcrPaginasTotales() != null && status.pages() != document.getOcrPaginasTotales()) {
                throw new JobRejected("OCR_JOB_METADATA_INVALID");
            }
            if (documentos.confirmarJobEnvio(id, owner, job) == 0) return;
            if (status.state().equals("ERROR") || status.state().equals("CANCELLED")) {
                throw new JobRejected(status.errorCode() == null ? "OCR_JOB_CANCELLED" : status.errorCode());
            }
            if (!status.state().equals("COMPLETED")) {
                documentos.guardarProgreso(id, owner, job, status.completedPages(), status.pages(), status.phase(),
                        2);
                return;
            }
            var result = client.resultadoJob(job, document.getOcrJobCapability());
            if (result.pages() != status.pages()) throw new JobRejected("OCR_JOB_METADATA_INVALID");
            String layout = mapper.writeValueAsString(result.layout());
            newPath = client.descargarPdf(result.artifact(), storage, document.getExpediente().getId().toString());
            String text = result.text() == null ? "" : result.text().trim();
            BigDecimal confidence = result.confidence() == null ? BigDecimal.ZERO : result.confidence();
            if (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
                throw new JobRejected("OCR_JOB_METADATA_INVALID");
            }
            EstadoOcr state = text.isBlank() || confidence.compareTo(umbralRevision) < 0
                    ? EstadoOcr.REQUIERE_REVISION : EstadoOcr.COMPLETADO;
            // publicarJob confirma su transacción antes de retornar.
            int updated = documentos.publicarJob(id, owner, job, text, confidence, result.pages(), state.name(), layout, newPath);
            if (updated == 0) {
                cleanup(newPath); newPath = null;
                cancel(job, document.getOcrJobCapability());
                return;
            }
            newPath = null;
            cleanup(document.getRutaPdf());
            try { client.confirmarJob(job, document.getOcrJobCapability()); }
            catch (RuntimeException error) {
                log.warn("event=ocr_ack_failed category={}", error.getClass().getSimpleName());
            }
            log.info("event=ocr_published pages={} artifactBytes={}", result.pages(), result.artifact().sizeBytes());
        } catch (Exception error) {
            cleanup(newPath);
            int retries = document == null || document.getOcrRetries() == null ? 0 : document.getOcrRetries();
            boolean transientError = error instanceof OcrClient.OcrFailure failure && failure.transientFailure()
                    || error instanceof org.springframework.dao.DataAccessException;
            boolean retry = transientError && retries < MAX_RETRIES;
            String code = error instanceof OcrClient.OcrFailure failure ? failure.code()
                    : error instanceof JobRejected rejected ? rejected.code : "OCR_PROCESSING_FAILED";
            String reference = randomHex();
            documentos.guardarFalloJob(id, owner, job, retry ? "PROCESANDO" : "ERROR",
                    "No se pudo completar el OCR [" + code + "]. Referencia: " + reference + ".",
                    retry ? "RETRY_WAIT" : "ERROR", retry ? (int)Math.min(60, 2L << retries) : 0);
            log.warn("event=ocr_job_failed code={} category={} retry={}", code, error.getClass().getSimpleName(), retry);
        }
    }

    private void cleanup(String path) {
        if (path == null) return;
        try { storage.eliminar(path); }
        catch (RuntimeException error) { log.warn("event=ocr_cleanup_failed category={}", error.getClass().getSimpleName()); }
    }

    private void cancel(String job, String capability) {
        try { client.cancelarJob(job, capability); }
        catch (RuntimeException error) { log.warn("event=ocr_cancel_failed category={}", error.getClass().getSimpleName()); }
    }

    private static String randomHex() { return UUID.randomUUID().toString().replace("-", ""); }
    private static class JobRejected extends IllegalStateException {
        private final String code;
        JobRejected(String code) { super(code); this.code = code; }
    }
}
