package com.municipalidadsanjose.archivo.ocr;

import com.municipalidadsanjose.archivo.entity.DocumentoDigital;
import com.municipalidadsanjose.archivo.enums.EstadoOcr;
import com.municipalidadsanjose.archivo.repository.DocumentoDigitalRepository;
import com.municipalidadsanjose.archivo.storage.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
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

    public OcrWorker(DocumentoDigitalRepository documentos,
                     FileStorageService storage,
                     OcrClient client,
                     tools.jackson.databind.ObjectMapper mapper,
                     @Value("${app.ocr.enabled:true}") boolean enabled,
                     @Value("${app.ocr.review-threshold:0.75}") BigDecimal umbralRevision) {
        this.documentos = documentos;
        this.storage = storage;
        this.client = client;
        this.mapper = mapper;
        this.enabled = enabled;
        this.umbralRevision = umbralRevision;
    }

    @Async("ocrExecutor")
    public void procesar(UUID documentoId) {
        if (!enabled || documentos.marcarProcesando(documentoId, LocalDateTime.now()) == 0) return;
        String nuevaRuta = null;
        String requestId = UUID.randomUUID().toString().replace("-", "");
        long started = System.nanoTime();
        long inputBytes = -1;
        long artifactBytes = -1;
        try {
            DocumentoDigital documento = documentos.findById(documentoId)
                    .orElseThrow(() -> new IllegalStateException("El documento ya no existe"));
            var original = storage.cargarComoRecurso(documento.getRutaAlmacenamiento());
            inputBytes = original.contentLength();
            OcrResultado resultado = client.reconocer(
                    original,
                    documento.getTipoMime(),
                    documento.getNombreArchivo());

            if (resultado.layout() == null || resultado.artifact() == null) {
                throw new IllegalStateException("Actualiza el servicio OCR: debe proporcionar un PDF binario protegido.");
            }
            if (resultado.requestId() != null && resultado.requestId().matches("[a-f0-9]{32}")) requestId = resultado.requestId();
            artifactBytes = resultado.artifact().sizeBytes();
            String layoutJson = mapper.writeValueAsString(resultado.layout());
            nuevaRuta = client.descargarPdf(resultado.artifact(), storage, documento.getExpediente().getId().toString());

            String texto = resultado.text() == null ? "" : resultado.text().trim();
            BigDecimal confianza = resultado.confidence() == null ? BigDecimal.ZERO : resultado.confidence();
            EstadoOcr estado = texto.isBlank() || confianza.compareTo(umbralRevision) < 0
                    ? EstadoOcr.REQUIERE_REVISION
                    : EstadoOcr.COMPLETADO;
            int actualizados = documentos.guardarResultadoOcr(
                    documentoId,
                    texto,
                    confianza,
                    Math.max(1, resultado.pages()),
                    estado,
                    layoutJson,
                    nuevaRuta,
                    LocalDateTime.now());
            if (actualizados == 0) {
                storage.eliminar(nuevaRuta);
            } else if (documento.getRutaPdf() != null) {
                try { storage.eliminar(documento.getRutaPdf()); } catch (RuntimeException ignored) {
                    log.warn("event=ocr_cleanup_failed requestId={} category={}", requestId, ignored.getClass().getSimpleName());
                }
            }
            nuevaRuta = null;
            log.info("event={} requestId={} inputBytes={} artifactBytes={} pages={} durationMs={}",
                    actualizados > 0 ? "ocr_published" : "ocr_discarded", requestId, inputBytes,
                    artifactBytes, resultado.pages(), (System.nanoTime() - started) / 1_000_000);
        } catch (Exception e) {
            if (e instanceof OcrClient.OcrFailure failure && failure.requestId() != null) requestId = failure.requestId();
            if (nuevaRuta != null) {
                try { storage.eliminar(nuevaRuta); }
                catch (RuntimeException cleanup) { log.warn("event=ocr_cleanup_failed requestId={} category={}", requestId, cleanup.getClass().getSimpleName()); }
            }
            String code = e instanceof OcrClient.OcrFailure failure ? failure.code() : "OCR_PROCESSING_FAILED";
            log.warn("event=ocr_failed requestId={} code={} category={} inputBytes={} artifactBytes={} durationMs={}",
                    requestId, code, exceptionCategory(e), inputBytes, artifactBytes, (System.nanoTime() - started) / 1_000_000);
            documentos.marcarErrorOcr(documentoId, "No se pudo completar el OCR [" + code + "]. Referencia: " + requestId + ".", LocalDateTime.now());
        }
    }

    private String exceptionCategory(Throwable error) {
        var visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        Throwable last = error;
        while (last.getCause() != null && visited.add(last.getCause())) last = last.getCause();
        return last.getClass().getSimpleName();
    }
}
