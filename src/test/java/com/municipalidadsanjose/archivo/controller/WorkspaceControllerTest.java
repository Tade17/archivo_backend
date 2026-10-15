package com.municipalidadsanjose.archivo.controller;

import com.municipalidadsanjose.archivo.config.SecurityConfig;
import com.municipalidadsanjose.archivo.entity.*;
import com.municipalidadsanjose.archivo.mapper.DocumentoDigitalMapper;
import com.municipalidadsanjose.archivo.ocr.*;
import com.municipalidadsanjose.archivo.repository.*;
import com.municipalidadsanjose.archivo.security.*;
import com.municipalidadsanjose.archivo.service.*;
import com.municipalidadsanjose.archivo.service.impl.DocumentoDigitalServiceImpl;
import com.municipalidadsanjose.archivo.storage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkspaceControllerTest {
    @TempDir Path directory;
    private final UUID id = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final OcrPdfService pdf = mock(OcrPdfService.class);
    private FileSystemStorageService storage;
    private DocumentoDigitalServiceImpl documents;
    private DocumentoDigital document;
    private WorkspaceController controller;
    private UsuarioPrincipal user;

    @BeforeEach
    void setup() throws Exception {
        storage = spy(new FileSystemStorageService(directory.toString(), 20, "application/pdf"));
        var repository = mock(DocumentoDigitalRepository.class);
        document = new DocumentoDigital(); document.setId(id);
        document.setRutaAlmacenamiento("exp/original.pdf");
        document.setNombreArchivo("original.pdf"); document.setTipoMime("application/pdf");
        var expediente = new Expediente(); expediente.setId(UUID.randomUUID());
        var technician = new Usuario(); technician.setNombre("Test");
        document.setExpediente(expediente); document.setTecnicoResponsable(technician);
        when(repository.findById(id)).thenReturn(Optional.of(document));
        documents = spy(new DocumentoDigitalServiceImpl(repository, mock(ExpedienteRepository.class),
                mock(UsuarioRepository.class), new DocumentoDigitalMapper(), storage,
                mock(OcrService.class), new tools.jackson.databind.ObjectMapper()));
        controller = new WorkspaceController(db, mock(ExpedienteService.class), documents, storage, pdf);
        user = mock(UsuarioPrincipal.class); when(user.getId()).thenReturn(userId);
        Files.createDirectories(directory.resolve("exp"));
    }

    @Test
    void originalPreviewUsesInternalResourceWhenRealMapperSanitizesPath() throws Exception {
        byte[] original = "%PDF-original unchanged".getBytes(StandardCharsets.US_ASCII);
        Files.write(directory.resolve("exp/original.pdf"), original);
        assertNull(documents.buscarPorId(id).rutaAlmacenamiento());
        var response = controller.preview(id, user);
        try (var stream = response.getBody().getInputStream()) { assertArrayEquals(original, stream.readAllBytes()); }
        assertEquals("application/pdf", response.getHeaders().getContentType().toString());
        assertEquals("private, no-store", response.getHeaders().getCacheControl());
        assertEquals("inline; filename=\"original.pdf\"", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        verify(documents).cargarArchivoOriginal(id);
        verifyNoInteractions(pdf);
        verifyAudit();
        assertNull(documents.buscarPorId(id).rutaAlmacenamiento());
    }

    @Test
    void generatedPreviewKeepsBoundPdfPathAndHeaders() throws Exception {
        byte[] generated = "%PDF-generated result".getBytes(StandardCharsets.US_ASCII);
        Files.write(directory.resolve("exp/generated.pdf"), generated);
        document.setRutaPdf("exp/generated.pdf"); document.setTipoMime("image/png");
        when(pdf.rutaPdf(id)).thenReturn("exp/generated.pdf");
        assertNull(documents.buscarPorId(id).rutaAlmacenamiento());
        var response = controller.preview(id, user);
        try (var stream = response.getBody().getInputStream()) { assertArrayEquals(generated, stream.readAllBytes()); }
        assertEquals("application/pdf", response.getHeaders().getContentType().toString());
        assertEquals("private, no-store", response.getHeaders().getCacheControl());
        assertEquals("inline; filename=\"original.pdf\"", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        verify(pdf).rutaPdf(id);
        verify(documents, never()).cargarArchivoOriginal(id);
        verifyAudit();
    }

    private void verifyAudit() {
        verify(db).update("INSERT INTO auditoria(usuario_id,entidad_afectada,entidad_id,accion) VALUES (?,?,?,?)",
                userId, "DocumentoDigital", id, "CONSULTAR");
    }

    @Configuration
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class SecurityFixture {
        @Bean JwtAuthenticationFilter jwtFilter() throws Exception {
            var filter = mock(JwtAuthenticationFilter.class);
            doAnswer(invocation -> {
                ((jakarta.servlet.FilterChain) invocation.getArgument(2)).doFilter(invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }).when(filter).doFilter(any(), any(), any());
            return filter;
        }
        @Bean UserDetailsService userDetails() { return mock(UserDetailsService.class); }
        @Bean tools.jackson.databind.ObjectMapper objectMapper() { return new tools.jackson.databind.ObjectMapper(); }
    }

    @Test
    void realSecurityChainRejectsAnonymousPreviewBeforeResourceOrAudit() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(SecurityFixture.class); context.refresh();
            var mvc = MockMvcBuilders.standaloneSetup(controller)
                    .addFilters(context.getBean("springSecurityFilterChain", jakarta.servlet.Filter.class)).build();
            mvc.perform(get("/api/workspace/documentos/" + id + "/vista"))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(db, pdf);
            verify(documents, never()).buscarPorId(any());
            verify(documents, never()).cargarArchivoOriginal(any());
        }
    }
}
