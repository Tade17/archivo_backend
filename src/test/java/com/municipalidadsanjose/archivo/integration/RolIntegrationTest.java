package com.municipalidadsanjose.archivo.integration;

import com.municipalidadsanjose.archivo.entity.Auditoria;
import com.municipalidadsanjose.archivo.entity.Rol;
import com.municipalidadsanjose.archivo.entity.Usuario;
import com.municipalidadsanjose.archivo.enums.AccionAuditoria;
import com.municipalidadsanjose.archivo.repository.AuditoriaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Cubre @PreAuthorize por rol, las reglas de negocio de RolServiceImpl
// (duplicado, "en uso") y que AuditoriaAspect efectivamente grabe las
// operaciones — antes de este aspecto, la tabla auditoria nunca se llenaba.
class RolIntegrationTest extends IntegrationTestBase {

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    private String tokenAdmin() throws Exception {
        Usuario admin = crearUsuario(obtenerRolAdmin(), "Password123!", true);
        return login(admin.getCorreo(), "Password123!");
    }

    private String tokenNoAdmin() throws Exception {
        Rol rolTecnico = crearRol("TECNICO");
        Usuario tecnico = crearUsuario(rolTecnico, "Password123!", true);
        return login(tecnico.getCorreo(), "Password123!");
    }

    @Test
    void crear_comoNoAdmin_devuelve403() throws Exception {
        String token = tokenNoAdmin();

        mockMvc.perform(post("/api/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"OTRO_ROL\",\"descripcion\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void crear_comoAdmin_devuelve201YQuedaAuditado() throws Exception {
        String token = tokenAdmin();

        String respuesta = mockMvc.perform(post("/api/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"NUEVO_ROL_TEST\",\"descripcion\":\"x\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID rolId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(respuesta, "$.id").toString());

        List<Auditoria> auditorias = auditoriaRepository.findByEntidadAfectadaAndEntidadId("Rol", rolId);
        assertThat(auditorias).hasSize(1);
        assertThat(auditorias.get(0).getAccion()).isEqualTo(AccionAuditoria.CREAR);
    }

    @Test
    void crear_conNombreDuplicado_devuelve409() throws Exception {
        String token = tokenAdmin();
        Rol existente = crearRol("DUPLICADO");

        mockMvc.perform(post("/api/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"%s\",\"descripcion\":\"x\"}".formatted(existente.getNombre())))
                .andExpect(status().isConflict());
    }

    @Test
    void eliminar_rolEnUso_devuelve409() throws Exception {
        String token = tokenAdmin();
        Rol rolEnUso = crearRol("EN_USO");
        crearUsuario(rolEnUso, "Password123!", true);

        mockMvc.perform(delete("/api/roles/" + rolEnUso.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void eliminar_rolInexistente_devuelve404() throws Exception {
        String token = tokenAdmin();

        mockMvc.perform(delete("/api/roles/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void eliminar_rolSinUso_devuelve204() throws Exception {
        String token = tokenAdmin();
        Rol rolLibre = crearRol("SIN_USO");

        mockMvc.perform(delete("/api/roles/" + rolLibre.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }
}
