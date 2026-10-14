package com.municipalidadsanjose.archivo.dto.documentodigital;

public record DocumentoOriginalDTO(int orden, String nombreArchivo, String tipoMime,
                                  long tamanoBytes, String hashSha256) {
    // Storage paths remain internal and are never returned by original-list endpoints.
    public record Stored(DocumentoOriginalDTO metadata, String rutaAlmacenamiento) {}
}
