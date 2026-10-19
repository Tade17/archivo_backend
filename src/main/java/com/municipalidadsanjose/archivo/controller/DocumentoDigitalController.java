package com.municipalidadsanjose.archivo.controller;

import com.municipalidadsanjose.archivo.dto.common.PaginaResponseDTO;
import com.municipalidadsanjose.archivo.dto.documentodigital.DocumentoDigitalActualizarDTO;
import com.municipalidadsanjose.archivo.dto.documentodigital.DocumentoDigitalResponseDTO;
import com.municipalidadsanjose.archivo.security.UsuarioPrincipal;
import com.municipalidadsanjose.archivo.ocr.OcrService;
import com.municipalidadsanjose.archivo.service.DocumentoDigitalService;
import com.municipalidadsanjose.archivo.service.DocumentoImageBatchService;
import com.municipalidadsanjose.archivo.dto.documentodigital.DocumentoOriginalDTO;
import com.municipalidadsanjose.archivo.storage.FileStorageService;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documentos-digitales")
public class DocumentoDigitalController {

    private final DocumentoDigitalService documentoDigitalService;
    private final DocumentoImageBatchService imageBatchService;
    private final FileStorageService fileStorageService;
    private final OcrService ocrService;
    private final com.municipalidadsanjose.archivo.ocr.OcrPdfService ocrPdf;

    public DocumentoDigitalController(DocumentoDigitalService documentoDigitalService,
                                       FileStorageService fileStorageService,
                                       OcrService ocrService,
                                       com.municipalidadsanjose.archivo.ocr.OcrPdfService ocrPdf, DocumentoImageBatchService imageBatchService) {
        this.documentoDigitalService = documentoDigitalService;
        this.fileStorageService = fileStorageService;
        this.ocrService = ocrService;
        this.ocrPdf = ocrPdf;
        this.imageBatchService = imageBatchService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<List<DocumentoDigitalResponseDTO>> crear(
            @RequestParam UUID expedienteId,
            @RequestParam UUID tecnicoResponsableId,
            @RequestParam(required = false) String escanerUtilizado,
            @RequestParam(required = false) Integer resolucionDpi,
            @RequestParam(required = false) String formatoSalida,
            @RequestParam("archivos") List<MultipartFile> archivos,
            @AuthenticationPrincipal UsuarioPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(imageBatchService.crear(
                expedienteId, principal.getId(), escanerUtilizado, resolucionDpi, formatoSalida, archivos));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/originales")
    public ResponseEntity<List<DocumentoOriginalDTO>> originales(@PathVariable UUID id) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(imageBatchService.originales(id));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/originales/{orden}")
    public ResponseEntity<Resource> descargarOriginal(@PathVariable UUID id, @PathVariable int orden) {
        var original = imageBatchService.original(id, orden);
        documentoDigitalService.registrarDescarga(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(original.metadata().tipoMime()))
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(original.metadata().nombreArchivo(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .body(original.resource());
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocumentoDigitalResponseDTO> actualizar(@PathVariable UUID id,
                                                                   @Valid @RequestBody DocumentoDigitalActualizarDTO dto,
                                                                   @AuthenticationPrincipal UsuarioPrincipal principal) {
        DocumentoDigitalActualizarDTO datosSeguros = new DocumentoDigitalActualizarDTO(
                dto.nombreArchivo(),
                principal.getId(),
                dto.escanerUtilizado(),
                dto.resolucionDpi(),
                dto.formatoSalida());
        return ResponseEntity.ok(documentoDigitalService.actualizar(id, datosSeguros));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentoDigitalResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(documentoDigitalService.buscarPorId(id));
    }

    @GetMapping
    public ResponseEntity<PaginaResponseDTO<DocumentoDigitalResponseDTO>> listarTodos(
            @RequestParam(required = false) UUID expedienteId,
            @ParameterObject @PageableDefault(size = 20, sort = "fechaDigitalizacion") Pageable pageable) {
        if (expedienteId != null) {
            return ResponseEntity.ok(PaginaResponseDTO.de(documentoDigitalService.listarPorExpediente(expedienteId, pageable)));
        }
        return ResponseEntity.ok(PaginaResponseDTO.de(documentoDigitalService.listarTodos(pageable)));
    }

    @PostMapping("/{id}/ocr/reintentar")
    public ResponseEntity<Void> reintentarOcr(@PathVariable UUID id) {
        ocrService.reintentar(id);
        return ResponseEntity.accepted().build();
    }

    // El texto OCR no se edita: el archivo histórico queda tal como lo generó el OCR (ver OCR.md).

    @GetMapping("/{id}/pdf")
    public ResponseEntity<Resource> descargarPdf(@PathVariable UUID id) {
        String ruta = ocrPdf.rutaPdf(id);
        var metadata = documentoDigitalService.registrarDescarga(id);
        String nombre = metadata.nombreArchivo().replaceFirst("\\.[^.]+$", "") + "-digitalizado.pdf";
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build().toString())
                .body(fileStorageService.cargarComoRecurso(ruta));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/archivo")
    public ResponseEntity<Resource> descargarArchivo(@PathVariable UUID id) {
        Resource recurso = documentoDigitalService.cargarArchivoOriginal(id);
        DocumentoDigitalResponseDTO metadata = documentoDigitalService.registrarDescarga(id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.parseMediaType(metadata.tipoMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(metadata.nombreArchivo()).build().toString())
                .body(recurso);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        documentoDigitalService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
