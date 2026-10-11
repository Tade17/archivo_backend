package com.municipalidadsanjose.archivo.dto.contenedor;

import com.municipalidadsanjose.archivo.enums.TipoContenedor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ContenedorRequestDTO(
        @NotNull(message = "El área de la ubicación es obligatoria")
        UUID cajaId,

        @NotNull(message = "Indica si es un archivador o un folder")
        TipoContenedor tipo,

        @NotBlank(message = "La descripción es obligatoria")
        @Size(max = 255, message = "La descripción no puede superar los 255 caracteres")
        String descripcion
) {
}
