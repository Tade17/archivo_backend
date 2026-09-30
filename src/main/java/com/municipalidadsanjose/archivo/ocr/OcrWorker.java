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
        try {
            DocumentoDigital documento = documentos.findById(documentoId)
                    .orElseThrow(() -> new IllegalStateException("El documento ya no existe"));
            OcrResultado resultado = client.reconocer(
                    storage.cargarComoRecurso(documento.getRutaAlmacenamiento()),
                    documento.getTipoMime(),
                    documento.getNombreArchivo());

            if (resultado.layout() == null || resultado.pdfBase64() == null) {
                throw new IllegalStateException("Actualiza el servicio OCR: debe generar el PDF y el texto posicionado.");
            }
            String layoutJson = mapper.writeValueAsString(resultado.layout());
            nuevaRuta = storage.guardarPdf(documento.getExpediente().getId().toString(),
                    java.util.Base64.getDecoder().decode(resultado.pdfBase64()));

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
                    // La nueva versión ya está publicada; una limpieza fallida no invalida el PDF.
                }
            }
            nuevaRuta = null;
        } catch (Exception e) {
            if (nuevaRuta != null) storage.eliminar(nuevaRuta);
            documentos.marcarErrorOcr(documentoId, mensajeSeguro(e), LocalDateTime.now());
        }
    }

    private String mensajeSeguro(Exception error) {
        String mensaje = error.getMessage();
        if (mensaje == null || mensaje.isBlank()) mensaje = "El OCR no pudo procesar el documento.";
        return mensaje.length() <= 1000 ? mensaje : mensaje.substring(0, 1000);
    }
}
