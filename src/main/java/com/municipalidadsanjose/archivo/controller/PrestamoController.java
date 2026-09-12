package com.municipalidadsanjose.archivo.controller;

import com.municipalidadsanjose.archivo.dto.common.PaginaResponseDTO;
import com.municipalidadsanjose.archivo.dto.prestamo.PrestamoRequestDTO;
import com.municipalidadsanjose.archivo.dto.prestamo.PrestamoResponseDTO;
import com.municipalidadsanjose.archivo.service.PrestamoService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/prestamos")
public class PrestamoController {

    private final PrestamoService prestamoService;

    public PrestamoController(PrestamoService prestamoService) {
        this.prestamoService = prestamoService;
    }

    @PostMapping
    public ResponseEntity<PrestamoResponseDTO> crear(@Valid @RequestBody PrestamoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(prestamoService.crear(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PrestamoResponseDTO> actualizar(@PathVariable UUID id,
                                                           @Valid @RequestBody PrestamoRequestDTO dto) {
        return ResponseEntity.ok(prestamoService.actualizar(id, dto));
    }

    @PatchMapping("/{id}/devolver")
    public ResponseEntity<PrestamoResponseDTO> devolver(@PathVariable UUID id) {
        return ResponseEntity.ok(prestamoService.devolver(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PrestamoResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(prestamoService.buscarPorId(id));
    }

    @GetMapping
    public ResponseEntity<PaginaResponseDTO<PrestamoResponseDTO>> listarTodos(
            @RequestParam(required = false) UUID expedienteId,
            @RequestParam(required = false) UUID solicitanteId,
            @PageableDefault(size = 20, sort = "fechaSolicitud") Pageable pageable) {
        if (expedienteId != null) {
            return ResponseEntity.ok(PaginaResponseDTO.de(prestamoService.listarPorExpediente(expedienteId, pageable)));
        }
        if (solicitanteId != null) {
            return ResponseEntity.ok(PaginaResponseDTO.de(prestamoService.listarPorSolicitante(solicitanteId, pageable)));
        }
        return ResponseEntity.ok(PaginaResponseDTO.de(prestamoService.listarTodos(pageable)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        prestamoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
