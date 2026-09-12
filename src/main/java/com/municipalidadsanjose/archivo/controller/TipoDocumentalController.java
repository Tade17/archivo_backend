package com.municipalidadsanjose.archivo.controller;

import com.municipalidadsanjose.archivo.dto.tipodocumental.TipoDocumentalRequestDTO;
import com.municipalidadsanjose.archivo.dto.tipodocumental.TipoDocumentalResponseDTO;
import com.municipalidadsanjose.archivo.service.TipoDocumentalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tipos-documentales")
public class TipoDocumentalController {

    private final TipoDocumentalService tipoDocumentalService;

    public TipoDocumentalController(TipoDocumentalService tipoDocumentalService) {
        this.tipoDocumentalService = tipoDocumentalService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<TipoDocumentalResponseDTO> crear(@Valid @RequestBody TipoDocumentalRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tipoDocumentalService.crear(dto));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<TipoDocumentalResponseDTO> actualizar(@PathVariable UUID id,
                                                                 @Valid @RequestBody TipoDocumentalRequestDTO dto) {
        return ResponseEntity.ok(tipoDocumentalService.actualizar(id, dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TipoDocumentalResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(tipoDocumentalService.buscarPorId(id));
    }

    @GetMapping
    public ResponseEntity<List<TipoDocumentalResponseDTO>> listarTodos() {
        return ResponseEntity.ok(tipoDocumentalService.listarTodos());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        tipoDocumentalService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
