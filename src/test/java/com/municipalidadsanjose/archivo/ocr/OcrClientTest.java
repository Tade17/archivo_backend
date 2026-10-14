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
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    private final ByteArrayResource file = new ByteArrayResource(new byte[]{1, 2, 3});
    private MockRestServiceServer server;

    private OcrClient client(boolean pdf, ResponseCreator response) {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var client = new OcrClient(new ObjectMapper(), "http://localhost:8091", 10);
        ReflectionTestUtils.setField(client, "httpClient", builder.build());
        server.expect(requestTo("http://localhost:8091/" + (pdf ? "pdf" : "v2/ocr"))).andRespond(response);
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
                "{\"text\":\"recognized\",\"confidence\":0.9,\"pages\":1,\"lines\":1,\"model\":\"test\",\"layout\":[],\"artifact\":{\"id\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"capability\":\"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\",\"sizeBytes\":8,\"sha256\":\"cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc\"}}",
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

    @Test
    void v2MetadataFitsConstrainedReaderWithoutPdfBase64() {
        var factory = tools.jackson.core.json.JsonFactory.builder().streamReadConstraints(
                tools.jackson.core.StreamReadConstraints.builder().maxStringLength(128).build()).build();
        var mapper = tools.jackson.databind.json.JsonMapper.builder(factory).build();
        assertThrows(tools.jackson.core.exc.StreamConstraintsException.class, () -> mapper.readValue(
                "{\"pdfBase64\":\"" + "x".repeat(512) + "\"}", OcrResultado.class));
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var client = new OcrClient(mapper, "http://localhost:8091", 10);
        ReflectionTestUtils.setField(client, "httpClient", builder.build());
        String metadata = "{\"text\":\"recognized\",\"confidence\":0.9,\"pages\":1,\"lines\":1,\"model\":\"test\",\"layout\":[],"
                + "\"artifact\":{\"id\":\"" + "a".repeat(32) + "\",\"capability\":\"" + "b".repeat(64)
                + "\",\"sizeBytes\":8,\"sha256\":\"" + "c".repeat(64) + "\"},\"requestId\":\"" + "d".repeat(32) + "\"}";
        server.expect(requestTo("http://localhost:8091/v2/ocr")).andRespond(withSuccess(metadata, MediaType.APPLICATION_JSON));
        var result = client.reconocer(file, "application/pdf", "test.pdf");
        assertNull(result.pdfBase64());
        assertNotNull(result.artifact());
        assertEquals(8, result.artifact().sizeBytes());
        assertTrue(result.requestId().matches("[a-f0-9]{32}"));
        server.verify();
    }

    private OcrClient binaryClient() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var client = new OcrClient(new ObjectMapper(), "http://localhost:8091", 10);
        ReflectionTestUtils.setField(client, "httpClient", builder.build());
        return client;
    }

    private OcrResultado.Artifact descriptor(byte[] pdf) throws Exception {
        return new OcrResultado.Artifact("a".repeat(32), "b".repeat(64), pdf.length,
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(pdf)));
    }

    private com.municipalidadsanjose.archivo.storage.FileSystemStorageService binaryStorage() {
        return new com.municipalidadsanjose.archivo.storage.FileSystemStorageService(directory.toString(), 20 * 1024 * 1024, "application/pdf");
    }

    @Test
    void retainedArtifactDownloadRetriesWithoutRecognition() throws Exception {
        var client = binaryClient();
        byte[] pdf = "%PDF-test".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var artifact = descriptor(pdf);
        server.expect(requestTo("http://localhost:8091/v2/artifacts/" + artifact.id()))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("X-Artifact-Capability", artifact.capability()))
                .andRespond(withException(new SocketTimeoutException("synthetic timeout")));
        server.expect(requestTo("http://localhost:8091/v2/artifacts/" + artifact.id()))
                .andRespond(withSuccess(pdf, MediaType.APPLICATION_PDF).header("Content-Length", String.valueOf(pdf.length)));
        var storage = binaryStorage();
        String path = client.descargarPdf(artifact, storage, "exp");
        assertArrayEquals(pdf, storage.cargarComoRecurso(path).getContentAsByteArray());
        server.verify();
    }

    @Test
    void artifactHeadersMustMatchMetadataBeforeStorage() throws Exception {
        var client = binaryClient();
        byte[] pdf = "%PDF-test".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var artifact = descriptor(pdf);
        server.expect(requestTo("http://localhost:8091/v2/artifacts/" + artifact.id()))
                .andRespond(withSuccess(pdf, MediaType.TEXT_PLAIN).header("Content-Length", String.valueOf(pdf.length)));
        var storage = org.mockito.Mockito.mock(com.municipalidadsanjose.archivo.storage.FileStorageService.class);
        assertThrows(OcrClient.OcrFailure.class, () -> client.descargarPdf(artifact, storage, "exp"));
        org.mockito.Mockito.verifyNoInteractions(storage);
        server.verify();
    }

    @Test
    void artifactLengthMismatchIsRejectedBeforeStorage() throws Exception {
        var client = binaryClient();
        byte[] pdf = "%PDF-test".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var artifact = descriptor(pdf);
        server.expect(requestTo("http://localhost:8091/v2/artifacts/" + artifact.id()))
                .andRespond(withSuccess(pdf, MediaType.APPLICATION_PDF).header("Content-Length", String.valueOf(pdf.length + 1)));
        var storage = org.mockito.Mockito.mock(com.municipalidadsanjose.archivo.storage.FileStorageService.class);
        assertThrows(OcrClient.OcrFailure.class, () -> client.descargarPdf(artifact, storage, "exp"));
        org.mockito.Mockito.verifyNoInteractions(storage);
        server.verify();
    }

    @Test
    void rejectsInvalidOrOversizedArtifactBeforeNetworkAccess() {
        var client = binaryClient();
        var storage = binaryStorage();
        for (var artifact : List.of(new OcrResultado.Artifact("../outside", "b".repeat(64), 9, "c".repeat(64)),
                new OcrResultado.Artifact("a".repeat(32), "b".repeat(64), 128L * 1024 * 1024 + 1, "c".repeat(64)))) {
            assertThrows(OcrClient.OcrFailure.class, () -> client.descargarPdf(artifact, storage, "exp"));
        }
        server.verify();
    }

    @Test
    void rejectsExcessMetadataContentLengthBeforeAllocation() {
        var client = binaryClient();
        server.expect(requestTo("http://localhost:8091/v2/ocr"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON).header("Content-Length", String.valueOf(16 * 1024 * 1024 + 1)));
        var error = assertThrows(OcrClient.OcrFailure.class, () -> client.reconocer(file, "application/pdf", "test.pdf"));
        assertEquals("OCR_METADATA_INVALID", error.code());
        assertNotNull(error.requestId());
        server.verify();
    }

    @Test
    void capabilityDownloadRejectsRedirectResponse() throws Exception {
        var client = binaryClient();
        byte[] pdf = "%PDF-test".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var artifact = descriptor(pdf);
        server.expect(requestTo("http://localhost:8091/v2/artifacts/" + artifact.id()))
                .andRespond(withStatus(HttpStatus.FOUND).header("Location", "http://untrusted.invalid/pdf"));
        assertThrows(OcrClient.OcrFailure.class, () -> client.descargarPdf(artifact, binaryStorage(), "exp"));
        server.verify();
    }
}
