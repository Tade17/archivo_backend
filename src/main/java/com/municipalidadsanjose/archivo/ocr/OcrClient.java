package com.municipalidadsanjose.archivo.ocr;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Component
public class OcrClient {

    private final RestClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final URI pdfEndpoint;

    public OcrClient(ObjectMapper objectMapper,
                     @Value("${app.ocr.url:http://localhost:8091}") String baseUrl,
                     @Value("${app.ocr.timeout-seconds:300}") long timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/ocr");
        this.pdfEndpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/pdf");
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
        } catch (IOException | RestClientException e) {
            throw new IllegalStateException(
                    "No se pudo conectar con el servicio OCR. Comprueba que el contenedor esté activo.", e);
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
        } catch (IOException | RestClientException e) {
            throw new IllegalStateException("No se pudo conectar con el generador de PDF.", e);
        }
    }
}
