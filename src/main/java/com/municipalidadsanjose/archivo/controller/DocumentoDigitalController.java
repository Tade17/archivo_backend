package com.municipalidadsanjose.archivo.controller;

import com.municipalidadsanjose.archivo.dto.common.PaginaResponseDTO;
import com.municipalidadsanjose.archivo.dto.documentodigital.DocumentoDigitalActualizarDTO;
import com.municipalidadsanjose.archivo.dto.documentodigital.DocumentoDigitalRequestDTO;
import com.municipalidadsanjose.archivo.dto.documentodigital.DocumentoDigitalResponseDTO;
import com.municipalidadsanjose.archivo.service.DocumentoDigitalService;
import com.municipalidadsanjose.archivo.storage.ArchivoAlmacenado;
import com.municipalidadsanjose.archivo.storage.FileStorageService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documentos-digitales")
public class DocumentoDigitalController {

    private final DocumentoDigitalService documentoDigitalService;
    private final FileStorageService fileStorageService;

    public DocumentoDigitalController(DocumentoDigitalService documentoDigitalService,
                                       FileStorageService fileStorageService) {
        this.documentoDigitalService = documentoDigitalService;
        this.fileStorageService = fileStorageService;
    }

    // Acepta uno o varios archivos en un solo request (flujo de digitalización
    // por lote: se escanean varias páginas/documentos de un mismo expediente de
    // una sola vez). Cada archivo se guarda bajo storage/documentos/<expedienteId>/
    // y genera su propio DocumentoDigital, auditado individualmente.
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<DocumentoDigitalResponseDTO>> crear(
            @RequestParam UUID expedienteId,
            @RequestParam UUID tecnicoResponsableId,
            @RequestParam(required = false) String escanerUtilizado,
            @RequestParam(required = false) Integer resolucionDpi,
            @RequestParam(required = false) String formatoSalida,
            @RequestParam("archivos") List<MultipartFile> archivos) {

        List<DocumentoDigitalResponseDTO> creados = new ArrayList<>();
        for (MultipartFile archivo : archivos) {
            ArchivoAlmacenado guardado = fileStorageService.guardar(expedienteId.toString(), archivo);
            String nombreOriginal = archivo.getOriginalFilename() != null ? archivo.getOriginalFilename() : "documento";

            DocumentoDigitalRequestDTO dto = new DocumentoDigitalRequestDTO(
                    expedienteId,
                    nombreOriginal,
                    guardado.rutaRelativa(),
                    archivo.getContentType(),
                    guardado.hashSha256(),
                    tecnicoResponsableId,
                    escanerUtilizado,
                    resolucionDpi,
                    formatoSalida,
                    null);
            creados.add(documentoDigitalService.crear(dto));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(creados);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocumentoDigitalResponseDTO> actualizar(@PathVariable UUID id,
                                                                   @Valid @RequestBody DocumentoDigitalActualizarDTO dto) {
        return ResponseEntity.ok(documentoDigitalService.actualizar(id, dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentoDigitalResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(documentoDigitalService.buscarPorId(id));
    }

    @GetMapping
    public ResponseEntity<PaginaResponseDTO<DocumentoDigitalResponseDTO>> listarTodos(
            @RequestParam(required = false) UUID expedienteId,
            @PageableDefault(size = 20, sort = "fechaDigitalizacion") Pageable pageable) {
        if (expedienteId != null) {
            return ResponseEntity.ok(PaginaResponseDTO.de(documentoDigitalService.listarPorExpediente(expedienteId, pageable)));
        }
        return ResponseEntity.ok(PaginaResponseDTO.de(documentoDigitalService.listarTodos(pageable)));
    }

    // Deja constancia en auditoría (accion=DESCARGAR) de quién bajó el archivo,
    // y devuelve el binario real (no la metadata).
    @GetMapping("/{id}/archivo")
    public ResponseEntity<Resource> descargarArchivo(@PathVariable UUID id) {
        DocumentoDigitalResponseDTO metadata = documentoDigitalService.registrarDescarga(id);
        Resource recurso = fileStorageService.cargarComoRecurso(metadata.rutaAlmacenamiento());

        return ResponseEntity.ok()
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
