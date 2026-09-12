package com.municipalidadsanjose.archivo.controller;

import com.municipalidadsanjose.archivo.dto.archivocentral.ArchivoCentralRequestDTO;
import com.municipalidadsanjose.archivo.dto.archivocentral.ArchivoCentralResponseDTO;
import com.municipalidadsanjose.archivo.service.ArchivoCentralService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/archivos-centrales")
public class ArchivoCentralController {

    private final ArchivoCentralService archivoCentralService;

    public ArchivoCentralController(ArchivoCentralService archivoCentralService) {
        this.archivoCentralService = archivoCentralService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ArchivoCentralResponseDTO> crear(@Valid @RequestBody ArchivoCentralRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(archivoCentralService.crear(dto));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ArchivoCentralResponseDTO> actualizar(@PathVariable UUID id,
                                                                 @Valid @RequestBody ArchivoCentralRequestDTO dto) {
        return ResponseEntity.ok(archivoCentralService.actualizar(id, dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ArchivoCentralResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(archivoCentralService.buscarPorId(id));
    }

    @GetMapping
    public ResponseEntity<List<ArchivoCentralResponseDTO>> listarTodos() {
        return ResponseEntity.ok(archivoCentralService.listarTodos());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        archivoCentralService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
