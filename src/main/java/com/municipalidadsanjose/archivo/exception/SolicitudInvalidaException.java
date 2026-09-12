package com.municipalidadsanjose.archivo.exception;

// Para reglas de negocio que Bean Validation no puede expresar
// (dependen de contexto, ej. "obligatorio solo al crear").
public class SolicitudInvalidaException extends RuntimeException {

    public SolicitudInvalidaException(String message) {
        super(message);
    }
}
