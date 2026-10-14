package com.municipalidadsanjose.archivo.ocr;

import com.municipalidadsanjose.archivo.entity.*;
import com.municipalidadsanjose.archivo.repository.DocumentoDigitalRepository;
import com.municipalidadsanjose.archivo.storage.FileStorageService;
import com.municipalidadsanjose.archivo.enums.EstadoOcr;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.*;
import tools.jackson.databind.ObjectMapper;
import org.springframework.core.io.ByteArrayResource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OcrWorkerTest {
    private final DocumentoDigitalRepository documents = mock(DocumentoDigitalRepository.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final OcrClient client = mock(OcrClient.class);
    private final UUID id = UUID.randomUUID();
    private OcrWorker worker;
    private DocumentoDigital document;
    private OcrResultado result;

    @BeforeEach void setup() {
        worker = new OcrWorker(documents, storage, client, new ObjectMapper(), true, new BigDecimal("0.75"));
        document = new DocumentoDigital(); document.setId(id); document.setRutaPdf("old.pdf");
        document.setRutaAlmacenamiento("original.pdf"); document.setTipoMime("application/pdf"); document.setNombreArchivo("test.pdf");
        var expediente = new Expediente(); expediente.setId(UUID.randomUUID()); document.setExpediente(expediente);
        result = new OcrResultado("recognized", new BigDecimal("0.9"), 1, 1, "test", List.of(), null,
                new OcrResultado.Artifact("a".repeat(32), "b".repeat(64), 15, "c".repeat(64)), "d".repeat(32));
        when(documents.marcarProcesando(eq(id), any())).thenReturn(1);
        when(documents.findById(id)).thenReturn(Optional.of(document));
        when(storage.cargarComoRecurso("original.pdf")).thenReturn(new ByteArrayResource(new byte[]{1}));
        when(client.reconocer(any(), anyString(), anyString())).thenReturn(result);
        when(client.descargarPdf(any(), any(), anyString())).thenReturn("new.pdf");
        when(documents.guardarResultadoOcr(eq(id), anyString(), any(), anyInt(), any(), anyString(), anyString(), any())).thenReturn(1);
    }

    @Test void publishesBinaryArtifactThenRemovesOldPdf() {
        worker.procesar(id);
        verify(client).descargarPdf(result.artifact(), storage, document.getExpediente().getId().toString());
        var order = inOrder(documents, storage);
        order.verify(documents).guardarResultadoOcr(eq(id), eq("recognized"), any(), eq(1), eq(EstadoOcr.COMPLETADO), anyString(), eq("new.pdf"), any());
        order.verify(storage).eliminar("old.pdf");
        verify(storage, never()).guardarPdf(anyString(), any(byte[].class));
    }

    @Test void failedDownloadPreservesOldPdfAndStoresOnlySafeDiagnostic() {
        when(client.descargarPdf(any(), any(), anyString())).thenThrow(new IllegalStateException("/sensitive/path capabilityABC"));
        worker.procesar(id);
        verify(storage, never()).eliminar("old.pdf");
        var capture = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(documents).marcarErrorOcr(eq(id), capture.capture(), any());
        assertFalse(capture.getValue().contains("sensitive"));
        assertFalse(capture.getValue().contains("capabilityABC"));
        assertTrue(capture.getValue().contains("Referencia"));
    }

    @Test void failedPublicationRemovesOnlyNewArtifact() {
        when(documents.guardarResultadoOcr(eq(id), anyString(), any(), anyInt(), any(), anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("synthetic database failure"));
        worker.procesar(id);
        verify(storage).eliminar("new.pdf");
        verify(storage, never()).eliminar("old.pdf");
    }

    @Test void rejectedPublicationRemovesOnlyNewArtifact() {
        when(documents.guardarResultadoOcr(eq(id), anyString(), any(), anyInt(), any(), anyString(), anyString(), any())).thenReturn(0);
        worker.procesar(id);
        verify(storage).eliminar("new.pdf");
        verify(storage, never()).eliminar("old.pdf");
    }
}
