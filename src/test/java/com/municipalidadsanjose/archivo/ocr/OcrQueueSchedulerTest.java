package com.municipalidadsanjose.archivo.ocr;

import com.municipalidadsanjose.archivo.entity.DocumentoDigital;
import com.municipalidadsanjose.archivo.enums.EstadoOcr;
import com.municipalidadsanjose.archivo.repository.DocumentoDigitalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OcrQueueSchedulerTest {
    @Test void startupNeverResetsAProcessingLiveLease() {
        var repository = mock(DocumentoDigitalRepository.class);
        var service = new OcrService(repository, mock(ApplicationEventPublisher.class), mock(OcrClient.class), true);
        service.recuperarPendientes();
        verify(repository, never()).recuperarProcesamientosInterrumpidos(any());
        verify(repository, never()).findIdsByOcrEstadoIn(any());
    }

    @Test void manualRetryDoesNotDuplicateAPendingJob() {
        var repository = mock(DocumentoDigitalRepository.class);
        var id = UUID.randomUUID();
        var document = new DocumentoDigital(); document.setOcrEstado(EstadoOcr.PENDIENTE);
        when(repository.findLockedById(id)).thenReturn(Optional.of(document));
        var service = new OcrService(repository, mock(ApplicationEventPublisher.class), mock(OcrClient.class), true);
        assertThrows(RuntimeException.class, () -> service.reintentar(id));
        verify(repository, never()).marcarPendiente(any(), any());
    }

    @Test void continuouslyDrainsMoreThanAnAsyncQueueCapacity() {
        var repository=mock(DocumentoDigitalRepository.class); var worker=mock(OcrWorker.class);
        var pending=new java.util.ArrayDeque<UUID>();
        for(int i=0;i<150;i++) pending.add(UUID.randomUUID());
        when(repository.reclamarSiguiente(anyString(),anyInt())).thenAnswer(call ->
                pending.isEmpty()?java.util.List.of():java.util.List.of(pending.remove()));
        var scheduler=new OcrQueueScheduler(repository,worker,true,120);
        for(int i=0;i<151;i++) scheduler.drain();
        verify(worker,times(150)).procesarReclamado(any(),anyString());
        assertTrue(pending.isEmpty());
    }

    @Test void responseDtoExposesProgressButNoJobCapabilityOrLeaseOwner() {
        var names=java.util.Arrays.stream(com.municipalidadsanjose.archivo.dto.documentodigital.DocumentoDigitalResponseDTO.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();
        assertTrue(names.containsAll(java.util.List.of("ocrPaginasProcesadas","ocrPaginasTotales","ocrFase","ocrProximoReintento")));
        assertFalse(names.stream().anyMatch(n -> n.toLowerCase().contains("capability") || n.toLowerCase().contains("lease") || n.equals("ocrJobId")));
    }

    @Test void heartbeatRunsWhileRemotePollIsBlocked() throws Exception {
        var repository=mock(DocumentoDigitalRepository.class); var worker=mock(OcrWorker.class);
        UUID id=UUID.randomUUID(); when(repository.reclamarSiguiente(anyString(),anyInt())).thenReturn(java.util.List.of(id));
        var entered=new java.util.concurrent.CountDownLatch(1); var release=new java.util.concurrent.CountDownLatch(1);
        doAnswer(call->{entered.countDown(); assertTrue(release.await(5,java.util.concurrent.TimeUnit.SECONDS)); return null;})
                .when(worker).procesarReclamado(eq(id),anyString());
        var scheduler=new OcrQueueScheduler(repository,worker,true,120);
        var thread=new Thread(scheduler::drain); thread.start();
        try { assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS)); scheduler.heartbeat();
            verify(repository).renovarLease(eq(id),anyString(),eq(120)); }
        finally { release.countDown(); thread.join(5000); }
        assertFalse(thread.isAlive());
    }

    @Test void responseMappingNeverIncludesNewPrivateStateOrStoragePath() throws Exception {
        var document=new DocumentoDigital();
        var expediente=new com.municipalidadsanjose.archivo.entity.Expediente(); expediente.setId(UUID.randomUUID());
        var user=new com.municipalidadsanjose.archivo.entity.Usuario(); user.setNombre("Test");
        document.setExpediente(expediente); document.setTecnicoResponsable(user); document.setRutaAlmacenamiento("private/path");
        document.setOcrJobCapability("b".repeat(64)); document.setOcrLeaseOwner("private-owner");
        document.setOcrPaginasProcesadas(2); document.setOcrPaginasTotales(3); document.setOcrFase("RECOGNIZING");
        var response=new com.municipalidadsanjose.archivo.mapper.DocumentoDigitalMapper().toResponseDTO(document);
        var json=new tools.jackson.databind.ObjectMapper().writeValueAsString(response);
        assertNull(response.rutaAlmacenamiento()); assertFalse(json.contains("private/path"));
        assertFalse(json.contains("b".repeat(64))); assertFalse(json.contains("private-owner"));
        assertEquals(2,response.ocrPaginasProcesadas());
    }
}
