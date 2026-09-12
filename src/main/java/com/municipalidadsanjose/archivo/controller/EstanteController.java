package com.municipalidadsanjose.archivo.controller;

import com.municipalidadsanjose.archivo.dto.estante.EstanteRequestDTO;
import com.municipalidadsanjose.archivo.dto.estante.EstanteResponseDTO;
import com.municipalidadsanjose.archivo.service.EstanteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/estantes")
public class EstanteController {

    private final EstanteService estanteService;

    public EstanteController(EstanteService estanteService) {
        this.estanteService = estanteService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<EstanteResponseDTO> crear(@Valid @RequestBody EstanteRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(estanteService.crear(dto));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<EstanteResponseDTO> actualizar(@PathVariable UUID id,
                                                          @Valid @RequestBody EstanteRequestDTO dto) {
        return ResponseEntity.ok(estanteService.actualizar(id, dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EstanteResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(estanteService.buscarPorId(id));
    }

    @GetMapping
    public ResponseEntity<List<EstanteResponseDTO>> listarTodos(
            @RequestParam(required = false) UUID archivoCentralId) {
        if (archivoCentralId != null) {
            return ResponseEntity.ok(estanteService.listarPorArchivoCentral(archivoCentralId));
        }
        return ResponseEntity.ok(estanteService.listarTodos());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        estanteService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
