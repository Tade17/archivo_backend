package com.municipalidadsanjose.archivo.controller;

import com.municipalidadsanjose.archivo.dto.caja.CajaRequestDTO;
import com.municipalidadsanjose.archivo.dto.caja.CajaResponseDTO;
import com.municipalidadsanjose.archivo.service.CajaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cajas")
public class CajaController {

    private final CajaService cajaService;

    public CajaController(CajaService cajaService) {
        this.cajaService = cajaService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<CajaResponseDTO> crear(@Valid @RequestBody CajaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cajaService.crear(dto));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<CajaResponseDTO> actualizar(@PathVariable UUID id,
                                                       @Valid @RequestBody CajaRequestDTO dto) {
        return ResponseEntity.ok(cajaService.actualizar(id, dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CajaResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(cajaService.buscarPorId(id));
    }

    @GetMapping
    public ResponseEntity<List<CajaResponseDTO>> listarTodos(
            @RequestParam(required = false) UUID nivelId) {
        if (nivelId != null) {
            return ResponseEntity.ok(cajaService.listarPorNivel(nivelId));
        }
        return ResponseEntity.ok(cajaService.listarTodos());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        cajaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
