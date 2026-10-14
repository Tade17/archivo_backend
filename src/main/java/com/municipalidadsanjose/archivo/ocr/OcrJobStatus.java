package com.municipalidadsanjose.archivo.ocr;

public record OcrJobStatus(String id, String state, String phase, int pages,
                           int completedPages, String errorCode) {
}
