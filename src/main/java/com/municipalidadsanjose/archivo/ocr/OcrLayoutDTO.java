package com.municipalidadsanjose.archivo.ocr;

import java.util.List;

public record OcrLayoutDTO(List<OcrPagina> layout, long version) {}
