package com.municipalidadsanjose.archivo.controller;

import com.municipalidadsanjose.archivo.dto.estadoexpediente.EstadoExpedienteRequestDTO;
import com.municipalidadsanjose.archivo.dto.estadoexpediente.EstadoExpedienteResponseDTO;
import com.municipalidadsanjose.archivo.service.EstadoExpedienteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/estados-expediente")
public class EstadoExpedienteController {

    private final EstadoExpedienteService estadoExpedienteService;

    public EstadoExpedienteController(EstadoExpedienteService estadoExpedienteService) {
        this.estadoExpedienteService = estadoExpedienteService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<EstadoExpedienteResponseDTO> crear(@Valid @RequestBody EstadoExpedienteRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(estadoExpedienteService.crear(dto));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<EstadoExpedienteResponseDTO> actualizar(@PathVariable UUID id,
                                                                   @Valid @RequestBody EstadoExpedienteRequestDTO dto) {
        return ResponseEntity.ok(estadoExpedienteService.actualizar(id, dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EstadoExpedienteResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(estadoExpedienteService.buscarPorId(id));
    }

    @GetMapping
    public ResponseEntity<List<EstadoExpedienteResponseDTO>> listarTodos() {
        return ResponseEntity.ok(estadoExpedienteService.listarTodos());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        estadoExpedienteService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
