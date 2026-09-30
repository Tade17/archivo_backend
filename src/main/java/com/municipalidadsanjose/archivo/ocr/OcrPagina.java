package com.municipalidadsanjose.archivo.ocr;

import java.util.List;

public record OcrPagina(double width, double height, List<OcrBloque> blocks) {
    public record OcrBloque(String id, String text, double x, double y,
                            double width, double height, double confidence) {}
}
