package com.municipalidadsanjose.archivo.dto.usuario;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UsuarioRequestDTO(
        @NotBlank(message = "El nombre es obligatorio")
        String nombre,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        String correo,

        // Opcional al actualizar; al crear se exige en el Service.
        String password,

        @NotNull(message = "El rol es obligatorio")
        UUID rolId,

        Boolean activo
) {
}
