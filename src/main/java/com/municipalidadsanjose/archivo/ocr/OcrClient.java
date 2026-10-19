package com.municipalidadsanjose.archivo.ocr;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Component
public class OcrClient {

    private final RestClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final URI pdfEndpoint;
    private final URI imageBatchEndpoint;
    private final URI artifactEndpoint;
    private final URI jobsEndpoint;
    private final long maxArtifactBytes;
    private static final int MAX_METADATA_BYTES = 16 * 1024 * 1024;

    public OcrClient(ObjectMapper objectMapper, String baseUrl, long timeoutSeconds) {
        this(objectMapper, baseUrl, timeoutSeconds, 128L * 1024 * 1024);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OcrClient(ObjectMapper objectMapper,
                     @Value("${app.ocr.url:http://localhost:8091}") String baseUrl,
                     @Value("${app.ocr.timeout-seconds:300}") long timeoutSeconds,
                     @Value("${app.ocr.max-artifact-bytes:134217728}") long maxArtifactBytes) {
        this.objectMapper = objectMapper;
        String root = baseUrl.replaceAll("/+$", "");
        this.endpoint = URI.create(root + "/v2/ocr");
        this.pdfEndpoint = URI.create(root + "/pdf");
        this.imageBatchEndpoint = URI.create(root + "/images/pdf");
        this.artifactEndpoint = URI.create(root + "/v2/artifacts/");
        this.jobsEndpoint = URI.create(root + "/v2/jobs/");
        this.maxArtifactBytes = Math.min(maxArtifactBytes, 128L * 1024 * 1024);
        if (this.maxArtifactBytes < 5) throw new IllegalArgumentException("Invalid artifact size limit");
        var factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(java.net.HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                // No reenviar la credencial del artefacto en una redirección.
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(10, timeoutSeconds)));
        this.httpClient = RestClient.builder().requestFactory(factory).build();
    }

    public OcrResultado reconocer(Resource archivo, String tipoMime, String nombreArchivo) {
        String requestId = java.util.UUID.randomUUID().toString().replace("-", "");
        try {
            byte[] contenido;
            try (var entrada = archivo.getInputStream()) {
                contenido = entrada.readNBytes(25 * 1024 * 1024 + 1);
            }
            if (contenido.length == 0 || contenido.length > 25 * 1024 * 1024) {
                throw failure("OCR_INPUT_LIMIT", requestId, "El original supera el límite de transferencia o está vacío.", null);
            }
            String nombreCodificado = Base64.getEncoder().encodeToString(nombreArchivo.getBytes(StandardCharsets.UTF_8));
            byte[] response = httpClient.post().uri(endpoint).contentType(MediaType.parseMediaType(tipoMime))
                    .header("X-Filename-Base64", nombreCodificado).header("X-Request-ID", requestId)
                    .body(contenido).exchange((request, reply) -> {
                        checkStatus(reply);
                        long declared = reply.getHeaders().getContentLength();
                        if (declared > MAX_METADATA_BYTES || reply.getHeaders().getContentType() == null
                                || !MediaType.APPLICATION_JSON.isCompatibleWith(reply.getHeaders().getContentType())) {
                            throw failure("OCR_METADATA_INVALID", requestId, "El servicio OCR devolvió metadatos inválidos.", null);
                        }
                        byte[] metadata = reply.getBody().readNBytes(MAX_METADATA_BYTES + 1);
                        if (metadata.length > MAX_METADATA_BYTES || declared >= 0 && declared != metadata.length) {
                            throw failure("OCR_METADATA_LIMIT", requestId, "El servicio OCR devolvió metadatos fuera de los límites.", null);
                        }
                        return metadata;
                    });
            OcrResultado result = objectMapper.readValue(response, OcrResultado.class);
            if (result == null) throw failure("OCR_METADATA_INVALID", requestId, "El servicio OCR devolvió una respuesta inválida.", null);
            validateArtifact(result.artifact());
            if (result.pdfBase64() != null || result.layout() == null) {
                throw failure("OCR_METADATA_INVALID", requestId, "El servicio OCR devolvió una respuesta inválida.", null);
            }
            return new OcrResultado(result.text(), result.confidence(), result.pages(), result.lines(), result.model(),
                    result.layout(), null, result.artifact(), requestId);
        } catch (OcrFailure error) {
            throw failure(error.code(), requestId, error.getMessage(), error);
        } catch (RestClientResponseException error) {
            throw failure("OCR_HTTP_ERROR", requestId, serviceFailure(error).getMessage(), error);
        } catch (ResourceAccessException error) {
            throw failure("OCR_CONNECTION_ERROR", requestId, connectionFailure(error).getMessage(), error);
        } catch (IOException error) {
            throw failure("OCR_INPUT_READ_FAILED", requestId, "No se pudo leer el archivo original para el OCR.", error);
        } catch (RestClientException | tools.jackson.core.JacksonException error) {
            throw failure("OCR_METADATA_INVALID", requestId, "El servicio OCR devolvió una respuesta inválida.", error);
        }
    }
    public byte[] generarPdf(Resource archivo, String tipoMime, String nombre,
                              java.util.List<OcrPagina> layout) {
        try {
            byte[] original;
            try (var entrada = archivo.getInputStream()) { original = entrada.readAllBytes(); }
            var datos = java.util.Map.of("fileBase64", Base64.getEncoder().encodeToString(original),
                    "contentType", tipoMime, "filename", nombre, "layout", layout);
            return httpClient.post().uri(pdfEndpoint).contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(datos)).retrieve().body(byte[].class);
        } catch (RestClientResponseException e) {
            throw serviceFailure(e);
        } catch (ResourceAccessException e) {
            throw connectionFailure(e);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el archivo original para generar el PDF.", e);
        } catch (RestClientException | tools.jackson.core.JacksonException e) {
            throw new IllegalStateException("El servicio OCR devolvió una respuesta inválida.", e);
        }
    }

    public byte[] unirImagenes(java.util.List<Resource> images) {
        final int maxBytes = 20 * 1024 * 1024;
        try {
            if (images.isEmpty() || images.size() > 100) {
                throw new IllegalStateException("Selecciona entre 1 y 100 imágenes.");
            }
            var payload = new java.util.ArrayList<java.util.Map<String, String>>();
            int total = 0;
            for (Resource image : images) {
                byte[] data;
                try (var input = image.getInputStream()) {
                    data = input.readNBytes(maxBytes - total + 1);
                }
                total += data.length;
                if (data.length == 0 || total > maxBytes) {
                    throw new IllegalStateException("Las imágenes superan el tamaño permitido o están vacías.");
                }
                payload.add(java.util.Map.of("fileBase64", Base64.getEncoder().encodeToString(data)));
            }
            byte[] result = httpClient.post().uri(imageBatchEndpoint).contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(java.util.Map.of("images", payload)))
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new RestClientResponseException("Image conversion failed", response.getStatusCode(),
                                    "", response.getHeaders(), new byte[0], StandardCharsets.UTF_8);
                        }
                        return response.getBody().readNBytes(maxBytes + 1);
                    });
            if (result == null || result.length < 5 || result.length > maxBytes
                    || !new String(result, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
                throw new IllegalStateException("El servicio no devolvió un PDF combinado válido dentro del límite de tamaño.");
            }
            return result;
        } catch (RestClientResponseException e) {
            throw serviceFailure(e);
        } catch (ResourceAccessException e) {
            throw connectionFailure(e);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudieron leer las imágenes originales.", e);
        } catch (RestClientException | tools.jackson.core.JacksonException e) {
            throw new IllegalStateException("El servicio OCR devolvió una respuesta inválida al unir las imágenes.", e);
        }
    }

    public String descargarPdf(OcrResultado.Artifact artifact,
                               com.municipalidadsanjose.archivo.storage.FileStorageService storage, String folder) {
        validateArtifact(artifact);
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return httpClient.get().uri(artifactEndpoint.resolve(artifact.id()))
                        .header("X-Artifact-Capability", artifact.capability())
                        .exchange((request, response) -> {
                            checkStatus(response);
                            var type = response.getHeaders().getContentType();
                            if (type == null || !MediaType.APPLICATION_PDF.isCompatibleWith(type)
                                    || response.getHeaders().getContentLength() != artifact.sizeBytes()) {
                                throw failure("OCR_ARTIFACT_INVALID", null, "El servicio OCR devolvió un PDF con cabeceras inválidas.", null);
                            }
                            return storage.guardarPdfStream(folder, response.getBody(), artifact.sizeBytes(),
                                    artifact.sha256(), maxArtifactBytes);
                        });
            } catch (RuntimeException error) {
                // Solo se reintenta la descarga del PDF, nunca el OCR.
                if (attempt == 0 && hasIoCause(error)) continue;
                if (error instanceof OcrFailure specific) throw specific;
                throw failure("OCR_ARTIFACT_DOWNLOAD_FAILED", null, "No se pudo descargar íntegramente el PDF OCR. Reintenta el documento.", error);
            }
        }
        throw failure("OCR_ARTIFACT_DOWNLOAD_FAILED", null, "No se pudo descargar el PDF OCR.", null);
    }

    public String engineFingerprint() {
        var result = jobJson(httpClient.get().uri(jobsEndpoint.resolve("config")), null, Config.class);
        if (result == null || result.engineFingerprint() == null || !result.engineFingerprint().matches("[a-f0-9]{64}")) {
            throw failure("OCR_JOB_METADATA_INVALID", null, "El servicio OCR devolvió una configuración inválida.", null);
        }
        return result.engineFingerprint();
    }

    public record Config(String engineFingerprint, long maxInputBytes, int maxPages) { }

    public OcrJobStatus consultarJob(String id, String capability) {
        validateJobIdentity(id, capability);
        return validateStatus(jobJson(httpClient.get().uri(jobsEndpoint.resolve(id))
                .header("X-Job-Capability", capability), id, OcrJobStatus.class), id);
    }

    public OcrJobStatus enviarJob(String id, String capability, String fingerprint, String sha256, Resource source) {
        validateJobIdentity(id, capability);
        if (fingerprint == null || !fingerprint.matches("[a-f0-9]{64}") || sha256 == null || !sha256.matches("[a-f0-9]{64}")) {
            throw failure("OCR_JOB_IDENTITY_INVALID", id, "La identidad del trabajo OCR no es válida.", null);
        }
        try {
            byte[] data;
            try (var input = source.getInputStream()) { data = input.readNBytes(25 * 1024 * 1024 + 1); }
            if (data.length == 0 || data.length > 25 * 1024 * 1024) {
                throw failure("OCR_INPUT_LIMIT", id, "El archivo supera el límite de transferencia.", null);
            }
            String actual = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data));
            if (!actual.equals(sha256)) throw failure("OCR_INPUT_HASH", id, "El archivo original no coincide con su hash guardado.", null);
            return validateStatus(jobJson(httpClient.post().uri(jobsEndpoint.resolve(id))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM).header("X-Job-Capability", capability)
                    .header("X-Source-SHA256", sha256).header("X-OCR-Fingerprint", fingerprint).body(data), id,
                    OcrJobStatus.class), id);
        } catch (IOException error) {
            throw failure("OCR_INPUT_READ_FAILED", id, "No se pudo leer el original para el OCR.", error);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    public OcrResultado resultadoJob(String id, String capability) {
        validateJobIdentity(id, capability);
        var result = jobJson(httpClient.get().uri(jobsEndpoint.resolve(id + "/result"))
                .header("X-Job-Capability", capability), id, OcrResultado.class);
        if (result == null || result.pdfBase64() != null || result.layout() == null || result.pages() < 1 || result.pages() > 100) {
            throw failure("OCR_JOB_METADATA_INVALID", id, "El servicio OCR devolvió un resultado inválido.", null);
        }
        validateArtifact(result.artifact());
        if (!id.equals(result.artifact().id()) || !capability.equals(result.artifact().capability())) {
            throw failure("OCR_JOB_IDENTITY_INVALID", id, "El artefacto no corresponde al trabajo OCR.", null);
        }
        return result;
    }

    public void cancelarJob(String id, String capability) { deleteJob(id, capability, false); }
    public void confirmarJob(String id, String capability) { deleteJob(id, capability, true); }

    private void deleteJob(String id, String capability, boolean completed) {
        validateJobIdentity(id, capability);
        jobJson(httpClient.delete().uri(jobsEndpoint.resolve(id + (completed ? "/result" : "")))
                .header("X-Job-Capability", capability), id, null);
    }

    private void validateJobIdentity(String id, String capability) {
        if (id == null || !id.matches("[a-f0-9]{32}") || capability == null || !capability.matches("[a-f0-9]{64}")) {
            throw failure("OCR_JOB_IDENTITY_INVALID", null, "La identidad del trabajo OCR no es válida.", null);
        }
    }

    private OcrJobStatus validateStatus(OcrJobStatus status, String id) {
        if (status == null || !id.equals(status.id()) || status.pages() < 1 || status.pages() > 100
                || status.completedPages() < 0 || status.completedPages() > status.pages()
                || status.state() == null || status.phase() == null
                || !java.util.Set.of("QUEUED", "RUNNING", "COMPLETED", "ERROR", "CANCELLED").contains(status.state())
                || !java.util.Set.of("QUEUED", "RECOGNIZING", "EXPORTING", "COMPLETED", "ERROR", "CANCELLED").contains(status.phase())
                || "COMPLETED".equals(status.state()) && status.completedPages() != status.pages()
                || status.errorCode() != null && !status.errorCode().matches("OCR_[A-Z_]{1,60}")) {
            throw failure("OCR_JOB_METADATA_INVALID", id, "El servicio OCR devolvió progreso inválido.", null);
        }
        return status;
    }

    private <T> T jobJson(RestClient.RequestHeadersSpec<?> request, String id, Class<T> type) {
        try {
            byte[] data = request.exchange((outgoing, reply) -> {
                if (type == null && reply.getStatusCode().value() == 404) return new byte[0];
                checkStatus(reply);
                if (type == null) return new byte[0];
                long size = reply.getHeaders().getContentLength();
                var media = reply.getHeaders().getContentType();
                if (size > MAX_METADATA_BYTES || media == null || !MediaType.APPLICATION_JSON.isCompatibleWith(media)) {
                    throw failure("OCR_JOB_METADATA_INVALID", id, "El servicio OCR devolvió metadatos inválidos.", null);
                }
                byte[] body = reply.getBody().readNBytes(MAX_METADATA_BYTES + 1);
                if (body.length > MAX_METADATA_BYTES || size >= 0 && size != body.length) {
                    throw failure("OCR_JOB_METADATA_INVALID", id, "El servicio OCR devolvió metadatos fuera del límite.", null);
                }
                return body;
            });
            return type == null ? null : objectMapper.readValue(data, type);
        } catch (OcrFailure error) {
            throw error;
        } catch (RestClientResponseException error) {
            throw failure("OCR_JOB_HTTP_" + error.getStatusCode().value(), id, "El servicio OCR no pudo atender el trabajo.", null);
        } catch (ResourceAccessException error) {
            throw failure("OCR_JOB_CONNECTION", id, "No se pudo comunicar con el servicio OCR.", null);
        } catch (RestClientException | tools.jackson.core.JacksonException error) {
            throw failure("OCR_JOB_METADATA_INVALID", id, "El servicio OCR devolvió una respuesta inválida.", null);
        }
    }

    private void validateArtifact(OcrResultado.Artifact artifact) {
        if (artifact == null || artifact.id() == null || !artifact.id().matches("[a-f0-9]{32}")
                || artifact.capability() == null || !artifact.capability().matches("[a-f0-9]{64}")
                || artifact.sha256() == null || !artifact.sha256().matches("[a-f0-9]{64}")
                || artifact.sizeBytes() < 5 || artifact.sizeBytes() > maxArtifactBytes) {
            throw failure("OCR_ARTIFACT_INVALID", null, "El servicio OCR devolvió una referencia de PDF inválida.", null);
        }
    }

    private static void checkStatus(org.springframework.http.client.ClientHttpResponse response) throws IOException {
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RestClientResponseException("OCR HTTP failure", response.getStatusCode(), "",
                    response.getHeaders(), new byte[0], StandardCharsets.UTF_8);
        }
    }

    private static boolean hasIoCause(Throwable error) {
        var visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        for (Throwable cause = error; cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof IOException) return true;
        }
        return false;
    }

    private static OcrFailure failure(String code, String requestId, String message, Throwable cause) {
        return new OcrFailure(code, requestId, message, cause);
    }

    public static final class OcrFailure extends IllegalStateException {
        private final String code;
        private final String requestId;
        private OcrFailure(String code, String requestId, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
            this.requestId = requestId;
        }
        public String code() { return code; }
        public String requestId() { return requestId; }
        public boolean transientFailure() {
            return code.equals("OCR_JOB_CONNECTION") || code.equals("OCR_ARTIFACT_DOWNLOAD_FAILED")
                    || code.matches("OCR_JOB_HTTP_(408|425|429|5[0-9]{2})");
        }
    }

    private IllegalStateException serviceFailure(RestClientResponseException error) {
        int status = error.getStatusCode().value();
        String message;
        if (status == 413) {
            message = "El archivo supera el tamaño permitido por el servicio OCR.";
        } else if (status == 400 || status == 422) {
            message = "El servicio OCR no pudo procesar el documento. Comprueba que el archivo sea válido y respete los límites de páginas y píxeles.";
        } else if (status == 408 || status == 504) {
            message = "El servicio OCR superó el tiempo máximo de procesamiento. Reintenta el documento.";
        } else if (status >= 500) {
            message = "El servicio OCR respondió con un error interno. Reintenta el documento o revisa el servicio.";
        } else {
            message = "El servicio OCR rechazó la solicitud (HTTP " + status + ").";
        }
        return new IllegalStateException(message, error);
    }

    private IllegalStateException connectionFailure(ResourceAccessException error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException) {
                return new IllegalStateException(
                        "El servicio OCR superó el tiempo máximo de espera. Reintenta el documento.", error);
            }
        }
        return new IllegalStateException(
                "No se pudo conectar con el servicio OCR. Comprueba que el contenedor esté activo.", error);
    }
}
