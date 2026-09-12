package com.municipalidadsanjose.archivo.storage;

// Resultado de guardar un archivo: la ruta es siempre relativa al directorio
// base de almacenamiento, nunca absoluta (así el storage se puede mover o
// migrar a otra raíz sin tener que reescribir rutas guardadas en la base).
public record ArchivoAlmacenado(String rutaRelativa, String hashSha256, long tamanoBytes) {
}
