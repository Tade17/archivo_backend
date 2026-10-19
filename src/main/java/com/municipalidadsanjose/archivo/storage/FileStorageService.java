package com.municipalidadsanjose.archivo.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    ArchivoAlmacenado guardar(String subcarpeta, MultipartFile archivo);

    String guardarPdf(String subcarpeta, byte[] contenido);

    String guardarPdfStream(String subcarpeta, java.io.InputStream contenido,
                            long expectedBytes, String sha256, long maxBytes);

    Resource cargarComoRecurso(String rutaRelativa);

    void eliminar(String rutaRelativa);
}
