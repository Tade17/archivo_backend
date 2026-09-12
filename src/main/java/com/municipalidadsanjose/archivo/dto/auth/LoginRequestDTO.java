package com.municipalidadsanjose.archivo.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDTO(
        @NotBlank(message = "El correo es obligatorio") String correo,
        @NotBlank(message = "La contraseña es obligatoria") String password
) {}
