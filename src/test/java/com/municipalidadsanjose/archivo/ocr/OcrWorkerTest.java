package com.municipalidadsanjose.archivo.ocr;

import com.municipalidadsanjose.archivo.entity.*;
import com.municipalidadsanjose.archivo.repository.DocumentoDigitalRepository;
import com.municipalidadsanjose.archivo.storage.FileStorageService;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.util.*;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OcrWorkerTest {
    private final DocumentoDigitalRepository documents = mock(DocumentoDigitalRepository.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final OcrClient client = mock(OcrClient.class);
    private final UUID id = UUID.randomUUID();
    private final String owner = "e".repeat(32), job = "a".repeat(32), cap = "b".repeat(64);
    private OcrWorker worker;
    private DocumentoDigital document;
    private OcrResultado result;

    @BeforeEach void setup() {
        worker = new OcrWorker(documents, storage, client, new ObjectMapper(), true, new BigDecimal("0.75"));
        document = new DocumentoDigital(); document.setId(id); document.setRutaPdf("old.pdf");
        document.setOcrLeaseOwner(owner); document.setOcrJobId(job); document.setOcrJobCapability(cap);
        document.setOcrJobSubmitted(true); document.setOcrRetries(0);
        document.setRutaAlmacenamiento("original.pdf");
        var expediente = new Expediente(); expediente.setId(UUID.randomUUID()); document.setExpediente(expediente);
        result = new OcrResultado("recognized", new BigDecimal("0.9"), 1, 1, "test", List.of(), null,
                new OcrResultado.Artifact(job, cap, 15, "c".repeat(64)), "d".repeat(32));
        when(documents.findForOcr(id)).thenReturn(Optional.of(document));
        when(documents.confirmarJobEnvio(id, owner, job)).thenReturn(1);
        when(client.consultarJob(job, cap)).thenReturn(new OcrJobStatus(job,"COMPLETED","COMPLETED",1,1,null));
        when(client.resultadoJob(job,cap)).thenReturn(result);
        when(client.descargarPdf(any(), any(), anyString())).thenReturn("new.pdf");
        when(documents.publicarJob(eq(id),eq(owner),eq(job),anyString(),any(),anyInt(),anyString(),anyString(),anyString())).thenReturn(1);
    }

    @Test void commitsFencedPublicationBeforeDeletingOldPdfAndAcknowledgement() {
        worker.procesarReclamado(id,owner);
        var order = inOrder(documents, storage, client);
        order.verify(documents).publicarJob(eq(id),eq(owner),eq(job),eq("recognized"),any(),eq(1),eq("COMPLETADO"),anyString(),eq("new.pdf"));
        order.verify(storage).eliminar("old.pdf");
        order.verify(client).confirmarJob(job,cap);
        verify(client,never()).reconocer(any(),anyString(),anyString());
    }

    @Test void failedDownloadPreservesOldPdfAndSafeDiagnostic() {
        when(client.descargarPdf(any(),any(),anyString())).thenThrow(new IllegalStateException("/secret/path capability"));
        worker.procesarReclamado(id,owner);
        verify(storage,never()).eliminar("old.pdf");
        var capture=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(documents).guardarFalloJob(eq(id),eq(owner),eq(job),eq("ERROR"),capture.capture(),eq("ERROR"),eq(0));
        assertFalse(capture.getValue().contains("secret")); assertFalse(capture.getValue().contains("capability"));
        verify(client,never()).confirmarJob(anyString(),anyString());
    }

    @Test void failedPublicationRemovesOnlyNewArtifact() {
        when(documents.publicarJob(any(),anyString(),anyString(),anyString(),any(),anyInt(),anyString(),anyString(),anyString()))
                .thenThrow(new org.springframework.dao.TransientDataAccessResourceException("synthetic database failure"));
        worker.procesarReclamado(id,owner);
        verify(storage).eliminar("new.pdf"); verify(storage,never()).eliminar("old.pdf");
        verify(documents).guardarFalloJob(eq(id),eq(owner),eq(job),eq("PROCESANDO"),anyString(),eq("RETRY_WAIT"),anyInt());
        verify(client,never()).confirmarJob(anyString(),anyString());
    }

    @Test void stalePublicationCannotOverwriteNewerJobOrAcknowledgeResult() {
        when(documents.publicarJob(any(),anyString(),anyString(),anyString(),any(),anyInt(),anyString(),anyString(),anyString())).thenReturn(0);
        worker.procesarReclamado(id,owner);
        verify(storage).eliminar("new.pdf"); verify(storage,never()).eliminar("old.pdf");
        verify(client,never()).confirmarJob(anyString(),anyString()); verify(client).cancelarJob(job,cap);
    }

    @Test void acknowledgementFailureDoesNotUndoPublishedSuccess() {
        doThrow(new IllegalStateException("private capability")).when(client).confirmarJob(job,cap);
        worker.procesarReclamado(id,owner);
        verify(storage,never()).eliminar("new.pdf");
        verify(documents,never()).guardarFalloJob(any(),anyString(),any(),anyString(),anyString(),anyString(),anyInt());
    }

    @Test void persistsActualProgressWithoutPublishingOrResubmitting() {
        when(client.consultarJob(job,cap)).thenReturn(new OcrJobStatus(job,"RUNNING","RECOGNIZING",10,4,null));
        worker.procesarReclamado(id,owner);
        verify(documents).guardarProgreso(eq(id),eq(owner),eq(job),eq(4),eq(10),eq("RECOGNIZING"),eq(2));
        verify(client,never()).enviarJob(anyString(),anyString(),anyString(),anyString(),any());
        verify(client,never()).descargarPdf(any(),any(),anyString());
    }

    @Test void deletedDocumentHasNoPublicationOrRemoteAction() {
        when(documents.findForOcr(id)).thenReturn(Optional.empty());
        worker.procesarReclamado(id,owner);
        verifyNoInteractions(client,storage);
    }

    @Test void terminalRemoteErrorIsNotAutomaticallyRetried() {
        when(client.consultarJob(job,cap)).thenReturn(new OcrJobStatus(job,"ERROR","ERROR",1,0,"OCR_INPUT_INVALID"));
        worker.procesarReclamado(id,owner);
        verify(documents).guardarFalloJob(eq(id),eq(owner),eq(job),eq("ERROR"),anyString(),eq("ERROR"),eq(0));
    }

    @Test void differentLeaseOwnerCannotPublish() {
        document.setOcrLeaseOwner("different"); worker.procesarReclamado(id,owner);
        verifyNoInteractions(client,storage);
    }

    @Test void retriesTransientFailuresOnlyWithinBudget() {
        var failure=mock(OcrClient.OcrFailure.class);
        when(failure.code()).thenReturn("OCR_JOB_HTTP_503"); when(failure.transientFailure()).thenReturn(true);
        when(client.consultarJob(job,cap)).thenThrow(failure);
        worker.procesarReclamado(id,owner);
        verify(documents).guardarFalloJob(eq(id),eq(owner),eq(job),eq("PROCESANDO"),anyString(),eq("RETRY_WAIT"),anyInt());
        reset(documents); when(documents.findForOcr(id)).thenReturn(Optional.of(document));
        document.setOcrRetries(5); worker.procesarReclamado(id,owner);
        verify(documents).guardarFalloJob(eq(id),eq(owner),eq(job),eq("ERROR"),anyString(),eq("ERROR"),eq(0));
    }

    @Test void expiredSubmittedJobNeverReexecutesInference() {
        var failure=mock(OcrClient.OcrFailure.class); when(failure.code()).thenReturn("OCR_JOB_HTTP_404");
        when(client.consultarJob(job,cap)).thenThrow(failure);
        worker.procesarReclamado(id,owner);
        verify(client,never()).enviarJob(anyString(),anyString(),anyString(),anyString(),any());
        verify(documents).guardarFalloJob(eq(id),eq(owner),eq(job),eq("ERROR"),contains("OCR_JOB_EXPIRED"),eq("ERROR"),eq(0));
    }

    @Test void identityCommitsBeforeFirstIdempotentSubmission() {
        document.setOcrJobId(null); document.setOcrJobCapability(null); document.setOcrJobSubmitted(null);
        document.setHashSha256("c".repeat(64));
        when(client.engineFingerprint()).thenReturn("d".repeat(64));
        when(documents.guardarJobIdentity(eq(id),eq(owner),anyString(),anyString(),anyString())).thenReturn(1);
        when(documents.confirmarJobEnvio(eq(id),eq(owner),anyString())).thenReturn(1);
        var missing=mock(OcrClient.OcrFailure.class); when(missing.code()).thenReturn("OCR_JOB_HTTP_404");
        when(client.consultarJob(anyString(),anyString())).thenThrow(missing);
        when(storage.cargarComoRecurso("original.pdf")).thenReturn(new org.springframework.core.io.ByteArrayResource(new byte[]{1}));
        when(client.enviarJob(anyString(),anyString(),anyString(),anyString(),any())).thenAnswer(call ->
                new OcrJobStatus(call.getArgument(0),"QUEUED","QUEUED",3,0,null));
        worker.procesarReclamado(id,owner);
        var order=inOrder(documents,client);
        order.verify(documents).guardarJobIdentity(eq(id),eq(owner),matches("[a-f0-9]{32}"),matches("[a-f0-9]{64}"),eq("d".repeat(64)));
        order.verify(client).enviarJob(eq(document.getOcrJobId()),eq(document.getOcrJobCapability()),eq("d".repeat(64)),eq("c".repeat(64)),any());
        assertNotEquals(document.getOcrJobId().repeat(2),document.getOcrJobCapability());
    }
}
