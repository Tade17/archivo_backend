package com.municipalidadsanjose.archivo.ocr;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OcrClientTest {
    private final ByteArrayResource file = new ByteArrayResource(new byte[]{1, 2, 3});
    private MockRestServiceServer server;

    private OcrClient client(boolean pdf, ResponseCreator response) {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var client = new OcrClient(new ObjectMapper(), "http://localhost:8091", 10);
        ReflectionTestUtils.setField(client, "httpClient", builder.build());
        server.expect(requestTo("http://localhost:8091/" + (pdf ? "pdf" : "ocr"))).andRespond(response);
        return client;
    }

    private IllegalStateException failure(boolean pdf, ResponseCreator response) {
        var client = client(pdf, response);
        var error = assertThrows(IllegalStateException.class, () -> {
            if (pdf) client.generarPdf(file, "application/pdf", "test.pdf", List.of());
            else client.reconocer(file, "application/pdf", "test.pdf");
        });
        server.verify();
        assertNotNull(error.getCause());
        return error;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void processingErrorIsNotReportedAsConnectionFailure(boolean pdf) {
        var error = failure(pdf, withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .body("{\"detail\":\"private traceback /secret/path\"}").contentType(MediaType.APPLICATION_JSON));
        assertTrue(error.getMessage().contains("no pudo procesar"));
        assertFalse(error.getMessage().contains("conectar"));
        assertFalse(error.getMessage().contains("secret"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void requestLimitHasSpecificSafeMessage(boolean pdf) {
        var error = failure(pdf, withStatus(HttpStatus.PAYLOAD_TOO_LARGE));
        assertTrue(error.getMessage().contains("tamaño permitido"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void serverFailureHasSpecificSafeMessage(boolean pdf) {
        var error = failure(pdf, withServerError().body("internal diagnostic"));
        assertTrue(error.getMessage().contains("error interno"));
        assertFalse(error.getMessage().contains("diagnostic"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void refusedConnectionRemainsConnectionFailure(boolean pdf) {
        var error = failure(pdf, withException(new ConnectException("Connection refused")));
        assertTrue(error.getMessage().contains("No se pudo conectar"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void readTimeoutHasSpecificMessage(boolean pdf) {
        var error = failure(pdf, withException(new SocketTimeoutException("Read timed out")));
        assertTrue(error.getMessage().contains("tiempo máximo"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void invalidDocumentResponseIsSafe(boolean pdf) {
        var error = failure(pdf, withBadRequest().body("private filename"));
        assertTrue(error.getMessage().contains("no pudo procesar"));
        assertFalse(error.getMessage().contains("private"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void upstreamTimeoutHasSpecificMessage(boolean pdf) {
        var error = failure(pdf, withStatus(HttpStatus.GATEWAY_TIMEOUT));
        assertTrue(error.getMessage().contains("tiempo máximo"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void successfulResponseIsPreserved(boolean pdf) {
        var client = client(pdf, withSuccess(pdf ? "%PDF-derived" :
                "{\"text\":\"recognized\",\"confidence\":0.9,\"pages\":1,\"lines\":1,\"model\":\"test\",\"layout\":[],\"pdfBase64\":\"\"}",
                pdf ? MediaType.APPLICATION_PDF : MediaType.APPLICATION_JSON));
        if (pdf) {
            assertArrayEquals("%PDF-derived".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    client.generarPdf(file, "application/pdf", "test.pdf", List.of()));
        } else {
            assertEquals("recognized", client.reconocer(file, "application/pdf", "test.pdf").text());
        }
        server.verify();
    }

    @Test
    void malformedJsonIsNotReportedAsConnectionFailure() {
        var error = failure(false, withSuccess("private invalid JSON", MediaType.APPLICATION_JSON));
        assertTrue(error.getMessage().contains("respuesta inválida"));
        assertFalse(error.getMessage().contains("private"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unreadableOriginalIsNotReportedAsConnectionFailure(boolean pdf) {
        var unreadable = new ByteArrayResource(new byte[0]) {
            @Override
            public InputStream getInputStream() throws IOException {
                throw new IOException("private local path");
            }
        };
        var client = new OcrClient(new ObjectMapper(), "http://localhost:8091", 10);
        var error = assertThrows(IllegalStateException.class, () -> {
            if (pdf) client.generarPdf(unreadable, "application/pdf", "test.pdf", List.of());
            else client.reconocer(unreadable, "application/pdf", "test.pdf");
        });
        assertTrue(error.getMessage().contains("leer el archivo original"));
        assertFalse(error.getMessage().contains("private"));
    }

    @Test
    void imageConversionSendsOriginalBytesInReceivedOrder() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var client = new OcrClient(new ObjectMapper(), "http://localhost:8091", 10);
        ReflectionTestUtils.setField(client, "httpClient", builder.build());
        server.expect(requestTo("http://localhost:8091/images/pdf"))
                .andExpect(content().json("{\"images\":[{\"fileBase64\":\"AQ==\"},{\"fileBase64\":\"Ag==\"}]}"))
                .andRespond(withSuccess("%PDF-test", MediaType.APPLICATION_PDF));
        byte[] result = client.unirImagenes(List.of(new ByteArrayResource(new byte[]{1}), new ByteArrayResource(new byte[]{2})));
        assertEquals("%PDF-test", new String(result, java.nio.charset.StandardCharsets.US_ASCII));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 422, 503})
    void imageConversionErrorsAreSafeAndSpecific(int status) {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var client = new OcrClient(new ObjectMapper(), "http://localhost:8091", 10);
        ReflectionTestUtils.setField(client, "httpClient", builder.build());
        server.expect(requestTo("http://localhost:8091/images/pdf"))
                .andRespond(withStatus(HttpStatus.valueOf(status)).body("private traceback"));
        var error = assertThrows(IllegalStateException.class, () -> client.unirImagenes(List.of(file)));
        assertFalse(error.getMessage().contains("private"));
        assertFalse(error.getMessage().contains("conectar"));
        server.verify();
    }

    @Test
    void imageConversionRejectsInvalidPdfResponse() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var client = new OcrClient(new ObjectMapper(), "http://localhost:8091", 10);
        ReflectionTestUtils.setField(client, "httpClient", builder.build());
        server.expect(requestTo("http://localhost:8091/images/pdf"))
                .andRespond(withSuccess("not a PDF", MediaType.APPLICATION_PDF));
        assertThrows(IllegalStateException.class, () -> client.unirImagenes(List.of(file)));
        server.verify();
    }

    @Test
    void imageConversionRejectsTotalBytesBeforeSendingRequest() {
        var client = new OcrClient(new ObjectMapper(), "http://localhost:8091", 10);
        var oversized = new ByteArrayResource(new byte[20 * 1024 * 1024 + 1]);
        var error = assertThrows(IllegalStateException.class, () -> client.unirImagenes(List.of(oversized)));
        assertTrue(error.getMessage().contains("tamaño permitido"));
    }
}
