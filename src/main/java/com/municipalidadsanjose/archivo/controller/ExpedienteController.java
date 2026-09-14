package com.municipalidadsanjose.archivo.controller;

import com.municipalidadsanjose.archivo.dto.common.PaginaResponseDTO;
import com.municipalidadsanjose.archivo.dto.expediente.ExpedienteRequestDTO;
import com.municipalidadsanjose.archivo.dto.expediente.ExpedienteResponseDTO;
import com.municipalidadsanjose.archivo.service.ExpedienteService;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// Sin DELETE a propósito: el expediente nunca se borra, ver ExpedienteService.
@RestController
@RequestMapping("/api/expedientes")
public class ExpedienteController {

    private final ExpedienteService expedienteService;

    public ExpedienteController(ExpedienteService expedienteService) {
        this.expedienteService = expedienteService;
    }

    @PostMapping
    public ResponseEntity<ExpedienteResponseDTO> crear(@Valid @RequestBody ExpedienteRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(expedienteService.crear(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExpedienteResponseDTO> actualizar(@PathVariable UUID id,
                                                             @Valid @RequestBody ExpedienteRequestDTO dto) {
        return ResponseEntity.ok(expedienteService.actualizar(id, dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExpedienteResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(expedienteService.buscarPorId(id));
    }

    @GetMapping("/codigo/{codigoUnico}")
    public ResponseEntity<ExpedienteResponseDTO> buscarPorCodigoUnico(@PathVariable String codigoUnico) {
        return ResponseEntity.ok(expedienteService.buscarPorCodigoUnico(codigoUnico));
    }

    @GetMapping
    public ResponseEntity<PaginaResponseDTO<ExpedienteResponseDTO>> listarTodos(
            @ParameterObject @PageableDefault(size = 20, sort = "fechaRegistro") Pageable pageable) {
        return ResponseEntity.ok(PaginaResponseDTO.de(expedienteService.listarTodos(pageable)));
    }
}
