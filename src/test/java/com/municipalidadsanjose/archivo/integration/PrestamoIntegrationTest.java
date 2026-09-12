package com.municipalidadsanjose.archivo.integration;

import com.municipalidadsanjose.archivo.entity.Caja;
import com.municipalidadsanjose.archivo.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Cubre la transición de estado de Prestamo.devolver() y su guard de
// idempotencia (no se puede devolver dos veces el mismo préstamo).
class PrestamoIntegrationTest extends IntegrationTestBase {

    private UUID crearExpediente(String token, Usuario usuario) throws Exception {
        Caja caja = crearCajaConJerarquia();
        var area = crearArea();
        var tipo = crearTipoDocumental();
        var estado = crearEstadoExpediente();

        String body = expedienteRequestJson(area.getId(), tipo.getId(), estado.getId(), caja.getId(), usuario.getId());
        String respuesta = mockMvc.perform(post("/api/expedientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(com.jayway.jsonpath.JsonPath.read(respuesta, "$.id").toString());
    }

    @Test
    void devolver_dosVeces_laSegundaDevuelve400() throws Exception {
        Usuario usuario = crearUsuario(crearRol("TECNICO"), "Password123!", true);
        String token = login(usuario.getCorreo(), "Password123!");
        UUID expedienteId = crearExpediente(token, usuario);

        String bodyPrestamo = """
                {"expedienteId":"%s","solicitanteId":"%s","tipoSolicitud":"FISICO","fechaDevolucionPrevista":"2027-01-01"}
                """.formatted(expedienteId, usuario.getId());

        String respuesta = mockMvc.perform(post("/api/prestamos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyPrestamo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID prestamoId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(respuesta, "$.id").toString());

        mockMvc.perform(patch("/api/prestamos/" + prestamoId + "/devolver")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DEVUELTO"));

        mockMvc.perform(patch("/api/prestamos/" + prestamoId + "/devolver")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}
