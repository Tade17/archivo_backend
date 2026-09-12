package com.municipalidadsanjose.archivo.exception;

import java.time.LocalDateTime;

public record ErrorResponse(int status, String mensaje, LocalDateTime timestamp) {

    public ErrorResponse(int status, String mensaje) {
        this(status, mensaje, LocalDateTime.now());
    }
}
