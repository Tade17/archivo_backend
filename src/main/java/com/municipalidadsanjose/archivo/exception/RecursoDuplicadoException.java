package com.municipalidadsanjose.archivo.exception;

// Para cuando ya existe un recurso con el mismo valor único
// (ej. correo de Usuario, nombre de Rol).
public class RecursoDuplicadoException extends RuntimeException {

    public RecursoDuplicadoException(String message) {
        super(message);
    }
}
