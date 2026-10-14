package com.municipalidadsanjose.archivo.ocr;

import java.math.BigDecimal;

public record OcrResultado(
        String text,
        BigDecimal confidence,
        int pages,
        int lines,
        String model,
        java.util.List<OcrPagina> layout,
        String pdfBase64,
        Artifact artifact,
        String requestId
) {
    public record Artifact(String id, String capability, long sizeBytes, String sha256) {}

    public OcrResultado(String text, BigDecimal confidence, int pages, int lines, String model,
                        java.util.List<OcrPagina> layout, String pdfBase64) {
        this(text, confidence, pages, lines, model, layout, pdfBase64, null, null);
    }
}
