package com.municipalidadsanjose.archivo.controller;

import com.municipalidadsanjose.archivo.dto.documentodigital.DocumentoOriginalDTO;
import com.municipalidadsanjose.archivo.ocr.*;
import com.municipalidadsanjose.archivo.security.UsuarioPrincipal;
import com.municipalidadsanjose.archivo.service.*;
import com.municipalidadsanjose.archivo.storage.FileStorageService;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentoDigitalControllerTest {
    private AnnotationConfigApplicationContext context;
    private DocumentoDigitalController controller;
    private DocumentoImageBatchService batch;
    private DocumentoDigitalService documents;
    private final UUID id = UUID.randomUUID();

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean DocumentoImageBatchService batch() { return mock(DocumentoImageBatchService.class); }
        @Bean DocumentoDigitalService documents() { return mock(DocumentoDigitalService.class); }
        @Bean DocumentoDigitalController controller(DocumentoImageBatchService batch, DocumentoDigitalService documents) {
            return new DocumentoDigitalController(documents, mock(FileStorageService.class), mock(OcrService.class), mock(OcrPdfService.class), batch);
        }
    }

    @BeforeEach
    void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(DocumentoDigitalController.class);
        batch = context.getBean(DocumentoImageBatchService.class);
        documents = context.getBean(DocumentoDigitalService.class);
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); context.close(); }

    @Test
    void originalEndpointsRequireAuthentication() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.descargarArchivo(id));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.originales(id));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.descargarOriginal(id, 1));
        verifyNoInteractions(batch, documents);
    }

    @Test
    void anonymousCannotDownloadOriginals() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("test", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertThrows(AccessDeniedException.class, () -> controller.descargarArchivo(id));
        assertThrows(AccessDeniedException.class, () -> controller.descargarOriginal(id, 1));
        verifyNoInteractions(batch);
    }

    @Test
    void authenticatedDownloadUsesBoundDocumentAndAudits() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("reader", "", List.of()));
        var metadata = new DocumentoOriginalDTO(2, "second.png", "image/png", 3, "a".repeat(64));
        var resource = new ByteArrayResource(new byte[]{1,2,3});
        when(batch.original(id, 2)).thenReturn(new DocumentoImageBatchService.OriginalDownload(metadata, resource));
        var response = controller.descargarOriginal(id, 2);
        assertSame(resource, response.getBody());
        assertEquals("private, no-store", response.getHeaders().getCacheControl());
        assertEquals("image/png", response.getHeaders().getContentType().toString());
        verify(batch).original(id, 2);
        verify(documents).registrarDescarga(id);
    }

    @Test
    void uploadUsesAuthenticatedTechnicianNotSubmittedIdentifier() {
        var principal = mock(UsuarioPrincipal.class);
        var technician = UUID.randomUUID();
        when(principal.getId()).thenReturn(technician);
        when(batch.crear(any(), any(), any(), any(), any(), anyList())).thenReturn(List.of());
        controller.crear(id, UUID.randomUUID(), null, null, null, List.of(), principal);
        verify(batch).crear(id, technician, null, null, null, List.of());
    }

    @Test
    void multipartRoutePreservesReceivedOrderAndAuthenticatedTechnician() throws Exception {
        var principal = mock(UsuarioPrincipal.class);
        var technician = UUID.randomUUID();
        when(principal.getId()).thenReturn(technician);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, "", List.of()));
        when(batch.crear(any(), any(), any(), any(), any(), anyList())).thenReturn(List.of());
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/documentos-digitales")
                        .file(new org.springframework.mock.web.MockMultipartFile("archivos", "z.png", "image/png", new byte[]{1}))
                        .file(new org.springframework.mock.web.MockMultipartFile("archivos", "middle.pdf", "application/pdf", new byte[]{2}))
                        .file(new org.springframework.mock.web.MockMultipartFile("archivos", "a.png", "image/png", new byte[]{3}))
                        .param("expedienteId", id.toString()).param("tecnicoResponsableId", UUID.randomUUID().toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());
        verify(batch).crear(eq(id), eq(technician), isNull(), isNull(), isNull(),
                argThat(files -> files.stream().map(org.springframework.web.multipart.MultipartFile::getOriginalFilename)
                        .toList().equals(List.of("z.png", "middle.pdf", "a.png"))));
    }

    @Test
    void originalFileDownloadUsesRealEntityPathWhilePublicDtoPathIsNull(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        byte[] original="%PDF-original bytes".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        java.nio.file.Files.createDirectories(directory.resolve("exp"));
        java.nio.file.Files.write(directory.resolve("exp/original.pdf"),original);
        var storage=new com.municipalidadsanjose.archivo.storage.FileSystemStorageService(directory.toString(),20,"application/pdf");
        var repository=mock(com.municipalidadsanjose.archivo.repository.DocumentoDigitalRepository.class);
        var document=new com.municipalidadsanjose.archivo.entity.DocumentoDigital(); document.setId(id);
        document.setRutaAlmacenamiento("exp/original.pdf"); document.setNombreArchivo("original.pdf"); document.setTipoMime("application/pdf");
        var expediente=new com.municipalidadsanjose.archivo.entity.Expediente(); expediente.setId(UUID.randomUUID());
        var technician=new com.municipalidadsanjose.archivo.entity.Usuario(); technician.setNombre("Test");
        document.setExpediente(expediente); document.setTecnicoResponsable(technician);
        when(repository.findById(id)).thenReturn(Optional.of(document));
        var service=spy(new com.municipalidadsanjose.archivo.service.impl.DocumentoDigitalServiceImpl(repository,
                mock(com.municipalidadsanjose.archivo.repository.ExpedienteRepository.class),
                mock(com.municipalidadsanjose.archivo.repository.UsuarioRepository.class),
                new com.municipalidadsanjose.archivo.mapper.DocumentoDigitalMapper(),storage,mock(OcrService.class),new tools.jackson.databind.ObjectMapper()));
        assertNull(service.buscarPorId(id).rutaAlmacenamiento());
        var actual=new DocumentoDigitalController(service,storage,mock(OcrService.class),mock(OcrPdfService.class),batch);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("reader","",List.of()));
        var response=actual.descargarArchivo(id);
        assertEquals("application/pdf",response.getHeaders().getContentType().toString());
        try(var stream=response.getBody().getInputStream()) { assertArrayEquals(original,stream.readAllBytes()); }
        verify(service).registrarDescarga(id);
        verify(service).cargarArchivoOriginal(id);
        assertEquals("private, no-store",response.getHeaders().getCacheControl());
        assertNull(service.buscarPorId(id).rutaAlmacenamiento());
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(actual).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/documentos-digitales/"+id+"/archivo"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(original));
    }
}
