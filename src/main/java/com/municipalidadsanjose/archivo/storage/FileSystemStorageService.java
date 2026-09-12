package com.municipalidadsanjose.archivo.storage;

import com.municipalidadsanjose.archivo.exception.RecursoNoEncontradoException;
import com.municipalidadsanjose.archivo.exception.SolicitudInvalidaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FileSystemStorageService implements FileStorageService {

    private final Path baseDir;
    private final long maxTamanoBytes;
    private final Set<String> tiposMimePermitidos;

    public FileSystemStorageService(
            @Value("${app.storage.base-dir}") String baseDir,
            @Value("${app.storage.max-file-size-bytes}") long maxTamanoBytes,
            @Value("${app.storage.tipos-mime-permitidos}") String tiposMimePermitidos) {
        this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
        this.maxTamanoBytes = maxTamanoBytes;
        this.tiposMimePermitidos = Arrays.stream(tiposMimePermitidos.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear el directorio de almacenamiento: " + this.baseDir, e);
        }
    }

    @Override
    public ArchivoAlmacenado guardar(String subcarpeta, MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new SolicitudInvalidaException("El archivo está vacío o no fue enviado");
        }
        if (archivo.getSize() > maxTamanoBytes) {
            throw new SolicitudInvalidaException(
                    "El archivo '%s' supera el tamaño máximo permitido (%d MB)"
                            .formatted(archivo.getOriginalFilename(), maxTamanoBytes / (1024 * 1024)));
        }
        // Nota: el tipo MIME viene del header Content-Type que declara el cliente,
        // no se inspecciona el contenido real del archivo (eso requeriría una
        // librería de detección tipo Apache Tika). Suficiente para el objetivo
        // actual de bloquear extensiones no deseadas por error o descuido.
        String tipoMime = archivo.getContentType();
        if (tipoMime == null || !tiposMimePermitidos.contains(tipoMime)) {
            throw new SolicitudInvalidaException(
                    "Tipo de archivo no permitido: " + tipoMime + ". Permitidos: " + tiposMimePermitidos);
        }

        // La subcarpeta es siempre el UUID del expediente (nunca texto libre del
        // cliente), y aun así se valida que el resultado no se escape del
        // directorio base, como defensa en profundidad contra path traversal.
        Path directorio = baseDir.resolve(subcarpeta).normalize();
        if (!directorio.startsWith(baseDir)) {
            throw new SolicitudInvalidaException("Subcarpeta de almacenamiento inválida");
        }

        try {
            Files.createDirectories(directorio);
            String nombreFisico = UUID.randomUUID() + extensionDe(archivo.getOriginalFilename());
            Path destino = directorio.resolve(nombreFisico);

            String hash;
            try (DigestInputStream digestStream = new DigestInputStream(
                    archivo.getInputStream(), MessageDigest.getInstance("SHA-256"))) {
                Files.copy(digestStream, destino, StandardCopyOption.REPLACE_EXISTING);
                hash = HexFormat.of().formatHex(digestStream.getMessageDigest().digest());
            }

            String rutaRelativa = baseDir.relativize(destino).toString().replace('\\', '/');
            return new ArchivoAlmacenado(rutaRelativa, hash, archivo.getSize());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("No se pudo guardar el archivo '" + archivo.getOriginalFilename() + "'", e);
        }
    }

    @Override
    public Resource cargarComoRecurso(String rutaRelativa) {
        Path ruta = baseDir.resolve(rutaRelativa).normalize();
        if (!ruta.startsWith(baseDir) || !Files.isRegularFile(ruta)) {
            throw new RecursoNoEncontradoException("Archivo en almacenamiento: " + rutaRelativa);
        }
        try {
            Resource recurso = new UrlResource(ruta.toUri());
            if (!recurso.isReadable()) {
                throw new RecursoNoEncontradoException("Archivo en almacenamiento: " + rutaRelativa);
            }
            return recurso;
        } catch (MalformedURLException e) {
            throw new RecursoNoEncontradoException("Archivo en almacenamiento: " + rutaRelativa);
        }
    }

    @Override
    public void eliminar(String rutaRelativa) {
        Path ruta = baseDir.resolve(rutaRelativa).normalize();
        if (!ruta.startsWith(baseDir)) {
            return;
        }
        try {
            Files.deleteIfExists(ruta);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo eliminar el archivo: " + rutaRelativa, e);
        }
    }

    private String extensionDe(String nombreOriginal) {
        if (nombreOriginal == null) {
            return "";
        }
        String nombreSaneado = Paths.get(nombreOriginal).getFileName().toString();
        int puntoIdx = nombreSaneado.lastIndexOf('.');
        return puntoIdx >= 0 ? nombreSaneado.substring(puntoIdx) : "";
    }
}
