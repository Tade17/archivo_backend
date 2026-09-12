package com.municipalidadsanjose.archivo.controller;

import com.municipalidadsanjose.archivo.dto.arearesponsable.AreaResponsableRequestDTO;
import com.municipalidadsanjose.archivo.dto.arearesponsable.AreaResponsableResponseDTO;
import com.municipalidadsanjose.archivo.service.AreaResponsableService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/areas-responsables")
public class AreaResponsableController {

    private final AreaResponsableService areaResponsableService;

    public AreaResponsableController(AreaResponsableService areaResponsableService) {
        this.areaResponsableService = areaResponsableService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<AreaResponsableResponseDTO> crear(@Valid @RequestBody AreaResponsableRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(areaResponsableService.crear(dto));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<AreaResponsableResponseDTO> actualizar(@PathVariable UUID id,
                                                                  @Valid @RequestBody AreaResponsableRequestDTO dto) {
        return ResponseEntity.ok(areaResponsableService.actualizar(id, dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AreaResponsableResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(areaResponsableService.buscarPorId(id));
    }

    @GetMapping
    public ResponseEntity<List<AreaResponsableResponseDTO>> listarTodos() {
        return ResponseEntity.ok(areaResponsableService.listarTodos());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        areaResponsableService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
