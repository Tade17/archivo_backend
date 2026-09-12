package com.municipalidadsanjose.archivo.controller;

import com.municipalidadsanjose.archivo.dto.auditoria.AuditoriaResponseDTO;
import com.municipalidadsanjose.archivo.service.AuditoriaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Solo lectura a propósito: los registros los crea el aspecto AOP, no un cliente.
// Acceso restringido a ADMIN: el historial de auditoría es información sensible.
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuditoriaResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(auditoriaService.buscarPorId(id));
    }

    @GetMapping
    public ResponseEntity<List<AuditoriaResponseDTO>> listarTodos(
            @RequestParam(required = false) String entidadAfectada,
            @RequestParam(required = false) UUID entidadId) {
        if (entidadAfectada != null && entidadId != null) {
            return ResponseEntity.ok(auditoriaService.buscarPorEntidad(entidadAfectada, entidadId));
        }
        return ResponseEntity.ok(auditoriaService.listarTodos());
    }
}
