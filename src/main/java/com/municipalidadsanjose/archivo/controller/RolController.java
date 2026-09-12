package com.municipalidadsanjose.archivo.controller;

import com.municipalidadsanjose.archivo.dto.rol.RolRequestDTO;
import com.municipalidadsanjose.archivo.dto.rol.RolResponseDTO;
import com.municipalidadsanjose.archivo.service.RolService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/roles")
public class RolController {
    private final RolService rolService;

    public RolController(RolService rolService){
        this.rolService=rolService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<RolResponseDTO> crear(@Valid @RequestBody RolRequestDTO dto){
        RolResponseDTO creado = rolService.crear(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }


    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<RolResponseDTO> actualizar(@PathVariable UUID id,
                                                     @Valid @RequestBody RolRequestDTO dto){
        return ResponseEntity.ok(rolService.actualizar(id,dto));
    }

    @GetMapping
    public ResponseEntity<List<RolResponseDTO>> listarTodos(){
        return  ResponseEntity.ok(rolService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RolResponseDTO> buscarPorId(@PathVariable UUID id){
        return ResponseEntity.ok(rolService.buscarPorID(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public  ResponseEntity<Void> eliminar(@PathVariable UUID id){
        rolService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
