package com.municipalidadsanjose.archivo.ocr;

import java.math.BigDecimal;

public record OcrResultado(
        String text,
        BigDecimal confidence,
        int pages,
        int lines,
        String model
) {
}
