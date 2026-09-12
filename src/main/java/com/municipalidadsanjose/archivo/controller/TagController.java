package com.municipalidadsanjose.archivo.controller;

import com.municipalidadsanjose.archivo.dto.tag.TagRequestDTO;
import com.municipalidadsanjose.archivo.dto.tag.TagResponseDTO;
import com.municipalidadsanjose.archivo.service.TagService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<TagResponseDTO> crear(@Valid @RequestBody TagRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tagService.crear(dto));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<TagResponseDTO> actualizar(@PathVariable UUID id,
                                                      @Valid @RequestBody TagRequestDTO dto) {
        return ResponseEntity.ok(tagService.actualizar(id, dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TagResponseDTO> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(tagService.buscarPorId(id));
    }

    @GetMapping
    public ResponseEntity<List<TagResponseDTO>> listarTodos() {
        return ResponseEntity.ok(tagService.listarTodos());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        tagService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
