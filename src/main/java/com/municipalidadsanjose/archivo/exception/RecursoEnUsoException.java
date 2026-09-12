package com.municipalidadsanjose.archivo.exception;

// Para cuando no se puede eliminar/modificar un recurso porque otra
// entidad todavía lo referencia (ej. borrar un Rol con usuarios asignados).
public class RecursoEnUsoException extends RuntimeException {

    public RecursoEnUsoException(String message) {
        super(message);
    }
}
