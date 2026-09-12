package com.municipalidadsanjose.archivo.dto.auth;

import java.util.UUID;

public record LoginResponseDTO(
        String token,
        String tipo,
        UUID usuarioId,
        String nombre,
        String correo,
        String rol
) {
    public LoginResponseDTO(String token, UUID usuarioId, String nombre, String correo, String rol) {
        this(token, "Bearer", usuarioId, nombre, correo, rol);
    }
}
