package com.municipalidadsanjose.archivo.ocr;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Component
public class OcrClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final Duration timeout;

    public OcrClient(ObjectMapper objectMapper,
                     @Value("${app.ocr.url:http://localhost:8091}") String baseUrl,
                     @Value("${app.ocr.timeout-seconds:300}") long timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/ocr");
        this.timeout = Duration.ofSeconds(Math.max(10, timeoutSeconds));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public OcrResultado reconocer(Resource archivo, String tipoMime, String nombreArchivo) {
        try {
            byte[] contenido;
            try (var entrada = archivo.getInputStream()) {
                contenido = entrada.readAllBytes();
            }
            String nombreCodificado = Base64.getEncoder()
                    .encodeToString(nombreArchivo.getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Content-Type", tipoMime)
                    .header("X-Filename-Base64", nombreCodificado)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(contenido))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "El servicio OCR respondió %d: %s".formatted(
                                response.statusCode(), limitar(response.body(), 500)));
            }
            return objectMapper.readValue(response.body(), OcrResultado.class);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("El procesamiento OCR fue interrumpido", e);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo conectar con el servicio OCR. Comprueba que el contenedor esté activo.", e);
        }
    }

    private String limitar(String valor, int maximo) {
        if (valor == null || valor.length() <= maximo) return valor;
        return valor.substring(0, maximo) + "…";
    }
}
