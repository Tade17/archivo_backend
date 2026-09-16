package com.municipalidadsanjose.archivo.controller;

import com.municipalidadsanjose.archivo.dto.common.PaginaResponseDTO;
import com.municipalidadsanjose.archivo.dto.documentodigital.DocumentoDigitalActualizarDTO;
import com.municipalidadsanjose.archivo.dto.documentodigital.DocumentoDigitalRequestDTO;
import com.municipalidadsanjose.archivo.dto.documentodigital.DocumentoDigitalResponseDTO;
import com.municipalidadsanjose.archivo.exception.SolicitudInvalidaException;
import com.municipalidadsanjose.archivo.security.UsuarioPrincipal;
import com.municipalidadsanjose.archivo.service.DocumentoDigitalService;
import com.municipalidadsanjose.archivo.storage.ArchivoAlmacenado;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
    @Transactional
    public ResponseEntity<List<DocumentoDigitalResponseDTO>> crear(
            @RequestParam UUID expedienteId,
            @RequestParam UUID tecnicoResponsableId,
            @RequestParam(required = false) String escanerUtilizado,
            @RequestParam(required = false) Integer resolucionDpi,
            @RequestParam(required = false) String formatoSalida,
            @RequestParam("archivos") List<MultipartFile> archivos,
            @AuthenticationPrincipal UsuarioPrincipal principal) {

        if (archivos.isEmpty()) {
            throw new SolicitudInvalidaException("Selecciona al menos un archivo.");
        }
        // El técnico responsable siempre es quien está autenticado, nunca lo que
        // mande el cliente: evita que alguien le adjudique la subida a otro usuario.
        tecnicoResponsableId = principal.getId();

        List<DocumentoDigitalResponseDTO> creados = new ArrayList<>();
        for (MultipartFile archivo : archivos) {
            ArchivoAlmacenado guardado = fileStorageService.guardar(expedienteId.toString(), archivo);
            // Si la transacción falla después de guardar el archivo en disco (p.ej. un
            // documento posterior del lote falla la validación), el archivo huérfano
            // se borra: el filesystem no tiene rollback, así que lo hacemos a mano.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        fileStorageService.eliminar(guardado.rutaRelativa());
                    }
                }
            });
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
            @ParameterObject @PageableDefault(size = 20, sort = "fechaDigitalizacion") Pageable pageable) {
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
