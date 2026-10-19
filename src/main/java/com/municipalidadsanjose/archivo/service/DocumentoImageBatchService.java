package com.municipalidadsanjose.archivo.service;

import com.municipalidadsanjose.archivo.dto.documentodigital.*;
import com.municipalidadsanjose.archivo.entity.DocumentoDigital;
import com.municipalidadsanjose.archivo.exception.RecursoNoEncontradoException;
import com.municipalidadsanjose.archivo.exception.SolicitudInvalidaException;
import com.municipalidadsanjose.archivo.ocr.OcrClient;
import com.municipalidadsanjose.archivo.repository.DocumentoDigitalRepository;
import com.municipalidadsanjose.archivo.storage.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.*;
import java.util.*;

@Service
public class DocumentoImageBatchService {
    private final DocumentoDigitalService documents;
    private final DocumentoDigitalRepository repository;
    private final FileStorageService storage;
    private final OcrClient ocr;
    private final ObjectMapper mapper;
    private final long maxBytes;

    public DocumentoImageBatchService(DocumentoDigitalService documents, DocumentoDigitalRepository repository,
            FileStorageService storage, OcrClient ocr, ObjectMapper mapper,
            @Value("${app.storage.max-file-size-bytes:20971520}") long maxBytes) {
        this.documents = documents;
        this.repository = repository;
        this.storage = storage;
        this.ocr = ocr;
        this.mapper = mapper;
        this.maxBytes = Math.min(maxBytes, 20L * 1024 * 1024);
    }

    @Transactional
    public List<DocumentoDigitalResponseDTO> crear(UUID expedienteId, UUID tecnicoId, String scanner,
            Integer dpi, String formato, List<MultipartFile> files) {
        if (files == null || files.isEmpty() || files.size() > 100) {
            throw new SolicitudInvalidaException("Selecciona entre 1 y 100 archivos.");
        }
        List<MultipartFile> validated = new ArrayList<>();
        long totalImages = 0;
        for (MultipartFile file : files) {
            String mime = detectType(file);
            if (!mime.equals("application/pdf")) totalImages += file.getSize();
            if (totalImages > maxBytes) throw new SolicitudInvalidaException("Las imágenes juntas superan el límite de 20 MB.");
            validated.add(new ValidatedFile(file, safeName(file.getOriginalFilename()), mime));
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("La carga de documentos requiere una transacción activa.");
        }
        List<String> pendingPaths = new ArrayList<>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) cleanup(storage, pendingPaths);
            }
        });
        List<DocumentoDigitalResponseDTO> results = new ArrayList<>();
        List<MultipartFile> images = validated.stream().filter(f -> !f.getContentType().equals("application/pdf")).toList();
        boolean grouped = false;
        try {
            for (MultipartFile file : validated) {
                if (file.getContentType().equals("application/pdf")) {
                    var stored = save(expedienteId, file, pendingPaths);
                    results.add(create(expedienteId, tecnicoId, scanner, dpi, formato, file, stored));
                } else if (!grouped) {
                    grouped = true;
                    List<DocumentoOriginalDTO.Stored> originals = new ArrayList<>();
                    List<Resource> resources = new ArrayList<>();
                    for (MultipartFile image : images) {
                        var stored = save(expedienteId, image, pendingPaths);
                        originals.add(new DocumentoOriginalDTO.Stored(new DocumentoOriginalDTO(originals.size() + 1,
                                image.getOriginalFilename(), image.getContentType(), stored.tamanoBytes(), stored.hashSha256()),
                                stored.rutaRelativa()));
                        resources.add(storage.cargarComoRecurso(stored.rutaRelativa()));
                    }
                    byte[] pdf;
                    try { pdf = ocr.unirImagenes(resources); }
                    catch (IllegalStateException error) {
                        var failure = new SolicitudInvalidaException("No se pudieron unir las imágenes. " + error.getMessage());
                        failure.initCause(error);
                        throw failure;
                    }
                    if (pdf == null || pdf.length > maxBytes) {
                        throw new SolicitudInvalidaException("El PDF combinado supera el tamaño permitido.");
                    }
                    var combined = new MemoryFile(pdf);
                    var storedPdf = save(expedienteId, combined, pendingPaths);
                    var response = create(expedienteId, tecnicoId, scanner, dpi, "PDF", combined, storedPdf);
                    DocumentoDigital document = find(response.id());
                    document.setOriginalesManifest(mapper.writeValueAsString(originals));
                    repository.saveAndFlush(document);
                    results.add(response);
                }
            }
            return results;
        } catch (RuntimeException error) {
            try { cleanup(storage, pendingPaths); }
            catch (RuntimeException cleanupError) { error.addSuppressed(cleanupError); }
            throw error;
        }
    }

    private ArchivoAlmacenado save(UUID expediente, MultipartFile file, List<String> pending) {
        var stored = storage.guardar(expediente.toString(), file);
        pending.add(stored.rutaRelativa());
        return stored;
    }

    private DocumentoDigitalResponseDTO create(UUID expediente, UUID tecnico, String scanner,
            Integer dpi, String formato, MultipartFile file, ArchivoAlmacenado stored) {
        return documents.crear(new DocumentoDigitalRequestDTO(expediente, file.getOriginalFilename(),
                stored.rutaRelativa(), file.getContentType(), stored.hashSha256(), tecnico, scanner, dpi, formato, null));
    }

    @Transactional(readOnly = true)
    public List<DocumentoOriginalDTO> originales(UUID id) {
        return readManifest(find(id), mapper).stream().map(DocumentoOriginalDTO.Stored::metadata).toList();
    }

    @Transactional(readOnly = true)
    public OriginalDownload original(UUID id, int orden) {
        if (orden < 1 || orden > 100) throw new RecursoNoEncontradoException("Imagen original", id);
        var manifest = readManifest(find(id), mapper);
        if (orden > manifest.size()) throw new RecursoNoEncontradoException("Imagen original", id);
        var original = manifest.get(orden - 1);
        return new OriginalDownload(original.metadata(), storage.cargarComoRecurso(original.rutaAlmacenamiento()));
    }

    public record OriginalDownload(DocumentoOriginalDTO metadata, Resource resource) {}

    private DocumentoDigital find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("DocumentoDigital", id));
    }

    public static List<DocumentoOriginalDTO.Stored> readManifest(DocumentoDigital document, ObjectMapper mapper) {
        String json = document.getOriginalesManifest();
        if (json == null) return List.of();
        if (json.length() > 100_000) throw new IllegalStateException("La asociación de imágenes originales es inválida.");
        try {
            var manifest = Arrays.asList(mapper.readValue(json, DocumentoOriginalDTO.Stored[].class));
            if (manifest.isEmpty() || manifest.size() > 100) throw new IllegalArgumentException();
            for (int index = 0; index < manifest.size(); index++) {
                var original = manifest.get(index);
                if (original == null || original.metadata() == null || original.metadata().orden() != index + 1
                        || original.rutaAlmacenamiento() == null || original.metadata().tamanoBytes() < 1) {
                    throw new IllegalArgumentException();
                }
            }
            return manifest;
        } catch (RuntimeException error) {
            throw new IllegalStateException("La asociación de imágenes originales es inválida.", error);
        }
    }

    public static void cleanup(FileStorageService storage, List<String> paths) {
        RuntimeException failure = null;
        for (String path : List.copyOf(paths)) {
            try { storage.eliminar(path); paths.remove(path); }
            catch (RuntimeException error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }

    private String detectType(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > maxBytes) {
            throw new SolicitudInvalidaException("El archivo está vacío o supera el tamaño permitido.");
        }
        try (var input = file.getInputStream()) {
            byte[] header = input.readNBytes(8);
            if (starts(header, new byte[]{(byte)137,80,78,71,13,10,26,10})) return "image/png";
            if (starts(header, new byte[]{(byte)255,(byte)216,(byte)255})) return "image/jpeg";
            if (starts(header, new byte[]{73,73,42,0}) || starts(header, new byte[]{77,77,0,42})) return "image/tiff";
            if (starts(header, new byte[]{37,80,68,70,45})) return "application/pdf";
            throw new SolicitudInvalidaException("El contenido no es un PDF, PNG, JPEG o TIFF válido.");
        } catch (IOException error) {
            throw new SolicitudInvalidaException("No se pudo leer el archivo subido.");
        }
    }

    private static boolean starts(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.equals(Arrays.copyOf(data, prefix.length), prefix);
    }

    private static String safeName(String name) {
        String safe = name == null ? "documento" : name.replace('\\', '/');
        safe = safe.substring(safe.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
        if (safe.isBlank()) safe = "documento";
        return safe.substring(0, Math.min(safe.length(), 255));
    }

    private record ValidatedFile(MultipartFile source, String name, String mime) implements MultipartFile {
        public String getName() { return source.getName(); }
        public String getOriginalFilename() { return name; }
        public String getContentType() { return mime; }
        public boolean isEmpty() { return source.isEmpty(); }
        public long getSize() { return source.getSize(); }
        public byte[] getBytes() throws IOException { return source.getBytes(); }
        public InputStream getInputStream() throws IOException { return source.getInputStream(); }
        public void transferTo(File dest) throws IOException { source.transferTo(dest); }
    }

    private record MemoryFile(byte[] bytes) implements MultipartFile {
        public String getName() { return "archivos"; }
        public String getOriginalFilename() { return "imagenes.pdf"; }
        public String getContentType() { return "application/pdf"; }
        public boolean isEmpty() { return bytes.length == 0; }
        public long getSize() { return bytes.length; }
        public byte[] getBytes() { return bytes; }
        public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        public void transferTo(File dest) throws IOException { java.nio.file.Files.write(dest.toPath(), bytes); }
    }
}
