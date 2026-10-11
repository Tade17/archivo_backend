package com.municipalidadsanjose.archivo.controller;

import com.municipalidadsanjose.archivo.dto.nivel.NivelRequestDTO;
import com.municipalidadsanjose.archivo.dto.nivel.NivelResponseDTO;
import com.municipalidadsanjose.archivo.service.NivelService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/niveles")
public class NivelController {

    private final NivelService nivelService;

    public NivelController(NivelService nivelService) {
        this.nivelService = nivelService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<NivelResponseDTO> crear(@Valid @RequestBody NivelRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(nivelService.crear(dto));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<NivelResponseDTO> actualizar(@PathVariable UUID id,
                                                        @Valid @RequestBody NivelRequestDTO dto) {
        return ResponseEntity.ok(nivelService.actualizar(id, dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NivelResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(nivelService.buscarPorId(id));
    }

    @GetMapping
    public ResponseEntity<List<NivelResponseDTO>> listarTodos(
            @RequestParam(required = false) UUID estanteId) {
        if (estanteId != null) {
            return ResponseEntity.ok(nivelService.listarPorEstante(estanteId));
        }
        return ResponseEntity.ok(nivelService.listarTodos());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        nivelService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
