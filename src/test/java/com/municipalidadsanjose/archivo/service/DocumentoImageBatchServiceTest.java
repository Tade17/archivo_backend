package com.municipalidadsanjose.archivo.service;

import com.municipalidadsanjose.archivo.dto.documentodigital.*;
import com.municipalidadsanjose.archivo.entity.DocumentoDigital;
import com.municipalidadsanjose.archivo.exception.SolicitudInvalidaException;
import com.municipalidadsanjose.archivo.ocr.OcrClient;
import com.municipalidadsanjose.archivo.repository.DocumentoDigitalRepository;
import com.municipalidadsanjose.archivo.storage.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentoImageBatchServiceTest {
    private final DocumentoDigitalService documents = mock(DocumentoDigitalService.class);
    private final DocumentoDigitalRepository repository = mock(DocumentoDigitalRepository.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final OcrClient ocr = mock(OcrClient.class);
    private final UUID expediente = UUID.randomUUID(), tecnico = UUID.randomUUID();
    private final List<DocumentoDigital> saved = new ArrayList<>();
    private DocumentoImageBatchService service;
    private final byte[] png = {(byte)137,80,78,71,13,10,26,10,1};
    private final byte[] pdf = "%PDF-test".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    @BeforeEach
    void setup() {
        service = new DocumentoImageBatchService(documents, repository, storage, ocr, new ObjectMapper(), 20 * 1024 * 1024);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        when(storage.guardar(anyString(), any())).thenAnswer(invocation -> {
            MultipartFile file = invocation.getArgument(1);
            return new ArchivoAlmacenado("exp/" + UUID.randomUUID(), "a".repeat(64), file.getSize());
        });
        when(storage.cargarComoRecurso(anyString())).thenReturn(new org.springframework.core.io.ByteArrayResource(png));
        when(ocr.unirImagenes(anyList())).thenReturn(pdf);
        when(documents.crear(any())).thenAnswer(invocation -> {
            DocumentoDigitalRequestDTO dto = invocation.getArgument(0);
            var document = new DocumentoDigital();
            document.setId(UUID.randomUUID()); document.setRutaAlmacenamiento(dto.rutaAlmacenamiento());
            saved.add(document);
            when(repository.findById(document.getId())).thenReturn(Optional.of(document));
            var result = mock(DocumentoDigitalResponseDTO.class);
            when(result.id()).thenReturn(document.getId());
            when(result.nombreArchivo()).thenReturn(dto.nombreArchivo());
            return result;
        });
    }

    @AfterEach
    void clear() {
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private MultipartFile image(String name) { return new MockMultipartFile("archivos", name, "image/png", png); }
    private MultipartFile pdf(String name) { return new MockMultipartFile("archivos", name, "application/pdf", pdf); }
    private List<DocumentoDigitalResponseDTO> upload(MultipartFile... files) {
        return service.crear(expediente, tecnico, null, 220, null, List.of(files));
    }

    @Test
    void interleavedImagesBecomeOneDocumentInFirstImagePosition() {
        var result = upload(pdf("first.pdf"), image("z.png"), pdf("middle.pdf"), image("a.png"));
        assertEquals(List.of("first.pdf", "imagenes.pdf", "middle.pdf"), result.stream().map(DocumentoDigitalResponseDTO::nombreArchivo).toList());
    }

    @Test
    void emptyBatchRejectedBeforeWrites() {
        assertThrows(SolicitudInvalidaException.class, () -> upload());
        verifyNoInteractions(storage, documents, ocr);
    }

    @Test
    void countLimitPrecedesWrites() {
        var files = Collections.nCopies(101, image("a.png"));
        assertThrows(SolicitudInvalidaException.class, () -> service.crear(expediente, tecnico, null, null, null, files));
        verifyNoInteractions(storage, documents, ocr);
    }

    @Test
    void spoofedImageHeaderRejectedBeforeWrites() {
        assertThrows(SolicitudInvalidaException.class, () -> upload(new MockMultipartFile("archivos", "a.png", "image/png", new byte[]{1, 2})));
        verifyNoInteractions(storage, documents, ocr);
    }

    @Test
    void originalsHaveOrderedAssociationsAndSafeMetadata() throws Exception {
        var result = upload(image("z.png"), image("a.png"));
        var metadata = service.originales(result.get(0).id());
        assertEquals(List.of("z.png", "a.png"), metadata.stream().map(DocumentoOriginalDTO::nombreArchivo).toList());
        assertEquals(List.of(1, 2), metadata.stream().map(DocumentoOriginalDTO::orden).toList());
        assertEquals("a".repeat(64), metadata.get(0).hashSha256());
        assertArrayEquals(png, service.original(result.get(0).id(), 1).resource().getContentAsByteArray());
        assertThrows(com.municipalidadsanjose.archivo.exception.RecursoNoEncontradoException.class,
                () -> service.original(result.get(0).id(), 0));
        assertThrows(com.municipalidadsanjose.archivo.exception.RecursoNoEncontradoException.class,
                () -> service.original(result.get(0).id(), 3));
        assertFalse(new ObjectMapper().writeValueAsString(metadata).contains("rutaAlmacenamiento"));
    }

    @Test
    void unavailableConverterCleansOriginalsAndDoesNotCreateDocument() {
        when(ocr.unirImagenes(anyList())).thenThrow(new IllegalStateException("No se pudo conectar con el servicio OCR."));
        var error = assertThrows(SolicitudInvalidaException.class, () -> upload(image("a.png"), image("b.png")));
        assertTrue(error.getMessage().contains("No se pudieron unir"));
        verify(storage, times(2)).eliminar(anyString());
        verifyNoInteractions(documents);
    }

    @Test
    void rollbackRemovesCombinedAndOriginalFiles() {
        upload(pdf("first.pdf"), image("a.png"), image("b.png"));
        verify(storage, never()).eliminar(anyString());
        TransactionSynchronizationManager.getSynchronizations().forEach(
                sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(storage, times(4)).eliminar(anyString());
    }

    @Test
    void laterDocumentFailureCleansEntireBatch() {
        doThrow(new IllegalStateException("database rejected document")).when(documents)
                .crear(argThat(dto -> dto != null && dto.nombreArchivo().equals("last.pdf")));
        assertThrows(IllegalStateException.class, () -> upload(image("a.png"), pdf("last.pdf")));
        verify(storage, times(3)).eliminar(anyString());
    }

    @Test
    void committedBatchKeepsAllOriginals() {
        upload(image("a.png"));
        TransactionSynchronizationManager.getSynchronizations().forEach(
                sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        verify(storage, never()).eliminar(anyString());
    }

    @Test
    void canonicalMimeComesFromSignatureAndFilenameIsSafe() {
        var response = upload(new MockMultipartFile("archivos", "../a\r\n.png", "application/pdf", png));
        assertEquals("imagenes.pdf", response.get(0).nombreArchivo());
        var original = service.originales(response.get(0).id()).get(0);
        assertEquals("image/png", original.tipoMime());
        assertEquals("a.png", original.nombreArchivo());
    }

    @Test
    void totalImageBytesAreRejectedBeforeWrites() {
        service = new DocumentoImageBatchService(documents, repository, storage, ocr, new ObjectMapper(), 12);
        assertThrows(SolicitudInvalidaException.class, () -> upload(image("a.png"), image("b.png")));
        verifyNoInteractions(storage, documents, ocr);
    }

    @Test
    void oversizedCombinedPdfCleansOriginals() {
        service = new DocumentoImageBatchService(documents, repository, storage, ocr, new ObjectMapper(), 12);
        when(ocr.unirImagenes(anyList())).thenReturn(new byte[13]);
        assertThrows(SolicitudInvalidaException.class, () -> upload(image("a.png")));
        verify(storage).eliminar(anyString());
        verifyNoInteractions(documents);
    }

    @Test
    void originalBytesAndHashArePreservedOnFilesystem(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var actualStorage = new FileSystemStorageService(directory.toString(), 20 * 1024 * 1024, "application/pdf,image/png");
        service = new DocumentoImageBatchService(documents, repository, actualStorage, ocr, new ObjectMapper(), 20 * 1024 * 1024);
        var result = upload(image("a.png"));
        var original = service.original(result.get(0).id(), 1);
        assertArrayEquals(png, original.resource().getContentAsByteArray());
        assertEquals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(png)),
                original.metadata().hashSha256());
        assertEquals(2, countFiles(directory));
        TransactionSynchronizationManager.getSynchronizations().forEach(
                sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertEquals(0, countFiles(directory));
    }

    @Test
    void partialStorageWriteIsRemoved(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var actualStorage = new FileSystemStorageService(directory.toString(), 12, "image/png");
        MultipartFile truncated = new MockMultipartFile("archivos", "broken.png", "image/png", png) {
            @Override public java.io.InputStream getInputStream() {
                return new java.io.InputStream() {
                    int read;
                    @Override public int read() throws java.io.IOException {
                        if (read++ > 2) throw new java.io.IOException("broken upload stream");
                        return 1;
                    }
                };
            }
        };
        assertThrows(IllegalStateException.class, () -> actualStorage.guardar(expediente.toString(), truncated));
        assertEquals(0, countFiles(directory));
    }

    @Test
    void originalsDeletionRunsOnlyAfterCommit() throws Exception {
        var result = upload(image("a.png"), image("b.png"));
        saved.get(0).setRutaPdf("exp/searchable.pdf");
        saved.get(0).setOcrJobId("a".repeat(32));
        saved.get(0).setOcrJobCapability("b".repeat(64));
        when(repository.findLockedById(result.get(0).id())).thenReturn(Optional.of(saved.get(0)));
        var ocr = mock(com.municipalidadsanjose.archivo.ocr.OcrService.class);
        var implementation = new com.municipalidadsanjose.archivo.service.impl.DocumentoDigitalServiceImpl(repository,
                mock(com.municipalidadsanjose.archivo.repository.ExpedienteRepository.class),
                mock(com.municipalidadsanjose.archivo.repository.UsuarioRepository.class),
                mock(com.municipalidadsanjose.archivo.mapper.DocumentoDigitalMapper.class), storage,
                ocr, new ObjectMapper());
        implementation.eliminar(result.get(0).id());
        verify(repository).findLockedById(result.get(0).id());
        verify(repository).delete(saved.get(0));
        verifyNoInteractions(ocr);
        verify(storage, never()).eliminar(anyString());
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(storage, times(4)).eliminar(anyString());
        verify(ocr).cancelarRemoto("a".repeat(32), "b".repeat(64));
    }

    @Test
    void corruptManifestFailsClosed() {
        var result = upload(image("a.png"));
        saved.get(0).setOriginalesManifest("not JSON");
        assertThrows(IllegalStateException.class, () -> service.originales(result.get(0).id()));
    }

    private long countFiles(java.nio.file.Path directory) throws Exception {
        try (var files = java.nio.file.Files.walk(directory)) {
            return files.filter(java.nio.file.Files::isRegularFile).count();
        }
    }
}
