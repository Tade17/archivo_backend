package com.municipalidadsanjose.archivo.dto.documentodigital;

public record DocumentoOriginalDTO(int orden, String nombreArchivo, String tipoMime,
                                  long tamanoBytes, String hashSha256) {
    public record Stored(DocumentoOriginalDTO metadata, String rutaAlmacenamiento) {}
}
