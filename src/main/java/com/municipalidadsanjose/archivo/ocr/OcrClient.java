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

    public OcrClient(ObjectMapper objectMapper,
                     @Value("${app.ocr.url:http://localhost:8091}") String baseUrl,
                     @Value("${app.ocr.timeout-seconds:300}") long timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/ocr");
        this.pdfEndpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/pdf");
        this.imageBatchEndpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/images/pdf");
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(10, timeoutSeconds)));
        this.httpClient = RestClient.builder().requestFactory(factory).build();
    }

    public OcrResultado reconocer(Resource archivo, String tipoMime, String nombreArchivo) {
        try {
            byte[] contenido;
            try (var entrada = archivo.getInputStream()) {
                contenido = entrada.readAllBytes();
            }
            String nombreCodificado = Base64.getEncoder()
                    .encodeToString(nombreArchivo.getBytes(StandardCharsets.UTF_8));
            String response = httpClient.post().uri(endpoint)
                    .contentType(MediaType.parseMediaType(tipoMime))
                    .header("X-Filename-Base64", nombreCodificado)
                    .body(contenido).retrieve().body(String.class);
            return objectMapper.readValue(response, OcrResultado.class);
        } catch (RestClientResponseException e) {
            throw serviceFailure(e);
        } catch (ResourceAccessException e) {
            throw connectionFailure(e);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el archivo original para el OCR.", e);
        } catch (RestClientException | tools.jackson.core.JacksonException e) {
            throw new IllegalStateException("El servicio OCR devolvió una respuesta inválida.", e);
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
        // Keep upstream bodies and internal paths out of persisted user-facing errors.
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
