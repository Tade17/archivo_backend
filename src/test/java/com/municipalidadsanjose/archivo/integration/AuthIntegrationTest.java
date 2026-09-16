package com.municipalidadsanjose.archivo.integration;

import com.municipalidadsanjose.archivo.entity.Rol;
import com.municipalidadsanjose.archivo.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Cubre el login por JWT y el fix del LazyInitializationException en
// UsuarioDetailsService (usuario.getRol() se leía fuera de transacción).
class AuthIntegrationTest extends IntegrationTestBase {

    @Test
    void login_correcto_devuelveTokenYRol() throws Exception {
        Rol rol = crearRol("TECNICO");
        Usuario usuario = crearUsuario(rol, "Password123!", true);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"password\":\"Password123!\"}".formatted(usuario.getCorreo())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.correo").value(usuario.getCorreo()))
                .andExpect(jsonPath("$.rol").value(rol.getNombre()));
    }

    @Test
    void login_passwordIncorrecta_devuelve401() throws Exception {
        Rol rol = crearRol("TECNICO");
        Usuario usuario = crearUsuario(rol, "Password123!", true);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"password\":\"otra-cosa\"}".formatted(usuario.getCorreo())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_usuarioInactivo_devuelve401() throws Exception {
        Rol rol = crearRol("TECNICO");
        Usuario usuario = crearUsuario(rol, "Password123!", false);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"%s\",\"password\":\"Password123!\"}".formatted(usuario.getCorreo())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endpointProtegido_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endpointProtegido_conTokenInvalido_devuelve401() throws Exception {
        mockMvc.perform(get("/api/usuarios").header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endpointProtegido_conTokenValido_devuelve200() throws Exception {
        Rol rol = obtenerRolAdmin();
        Usuario usuario = crearUsuario(rol, "Password123!", true);
        String token = login(usuario.getCorreo(), "Password123!");

        mockMvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
