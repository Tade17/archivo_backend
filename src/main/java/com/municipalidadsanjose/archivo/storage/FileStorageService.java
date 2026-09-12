package com.municipalidadsanjose.archivo.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

// Abstrae el "dónde" se guardan los binarios de los documentos digitalizados.
// Hoy hay una sola implementación (filesystem local, ver FileSystemStorageService);
// el resto de la app solo depende de esta interfaz, así que cambiar a un backend
// tipo S3/MinIO el día de mañana no toca nada fuera del paquete storage.
public interface FileStorageService {

    // Valida (tamaño, tipo MIME) y persiste el archivo bajo la subcarpeta dada.
    // Lanza SolicitudInvalidaException si no pasa la validación.
    ArchivoAlmacenado guardar(String subcarpeta, MultipartFile archivo);

    // rutaRelativa es la que devolvió guardar(): relativa al directorio base.
    Resource cargarComoRecurso(String rutaRelativa);

    void eliminar(String rutaRelativa);
}
