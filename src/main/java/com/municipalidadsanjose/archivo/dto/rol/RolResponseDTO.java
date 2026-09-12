package com.municipalidadsanjose.archivo.dto.rol;

import java.util.UUID;

public record RolResponseDTO(
         UUID id,
         String nombre,
         String descripcion
        ) { }
