package com.municipalidadsanjose.archivo.integration;

import com.jayway.jsonpath.JsonPath;
import com.municipalidadsanjose.archivo.entity.Contenedor;
import com.municipalidadsanjose.archivo.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PrestamoIntegrationTest extends IntegrationTestBase {

    @Autowired JdbcTemplate db;

    private String crearExpediente(String token, Usuario gestor, Contenedor contenedor) throws Exception {
        String json = expedienteRequestJson(crearArea().getId(), crearTipoDocumental().getId(),
                crearEstadoExpediente().getId(), contenedor.getId(), gestor.getId());
        String respuesta = mockMvc.perform(post("/api/expedientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.id");
    }

    private String prestar(String token, String campo, String id, String solicitante, int esperado) throws Exception {
        String body = """
                {"%s":"%s","solicitante":"%s","dias":5,"observaciones":"Para auditoría"}
                """.formatted(campo, id, solicitante);
        return mockMvc.perform(post("/api/workspace/prestamos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(esperado))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void prestarUnDocumento_aEntidadExterna_conSemaforoYDevolucion() throws Exception {
        Usuario gestor = crearUsuario(obtenerRol("GESTOR_DOCUMENTAL"), "Password123!", true);
        String token = login(gestor.getCorreo(), "Password123!");
        String expedienteId = crearExpediente(token, gestor, crearContenedor(gestor));

        String creado = prestar(token, "expedienteId", expedienteId, "Contraloría - Sr. Pérez", 201);
        String prestamoId = JsonPath.read(creado, "$.id");
        org.junit.jupiter.api.Assertions.assertEquals("VERDE", JsonPath.read(creado, "$.semaforo"));
        org.junit.jupiter.api.Assertions.assertEquals("Contraloría - Sr. Pérez", JsonPath.read(creado, "$.solicitante"));

        // El mismo documento no se presta dos veces.
        mockMvc.perform(post("/api/workspace/prestamos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expedienteId\":\"" + expedienteId + "\",\"solicitante\":\"Fiscalía\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("ya está prestado")));

        // Vence ayer y no volvió: rojo; aparece entre los vencidos.
        db.update("UPDATE prestamo SET fecha_devolucion_prevista = CURRENT_DATE - 1 WHERE prestamo_id = ?::uuid",
                prestamoId);
        mockMvc.perform(get("/api/workspace/prestamos/" + prestamoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semaforo").value("ROJO"));
        mockMvc.perform(get("/api/workspace/prestamos").param("estado", "VENCIDOS")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos", greaterThanOrEqualTo(1)));
        mockMvc.perform(get("/api/workspace/prestamos/resumen")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vencidos", greaterThanOrEqualTo(1)));

        // El documento figura como prestado para cualquiera que lo consulte.
        mockMvc.perform(get("/api/workspace/expedientes/" + expedienteId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.prestado").value(true));

        mockMvc.perform(post("/api/workspace/prestamos/" + prestamoId + "/devolver")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DEVUELTO"))
                .andExpect(jsonPath("$.semaforo").value("GRIS"));
        mockMvc.perform(get("/api/workspace/expedientes/" + expedienteId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.prestado").value(false));
        mockMvc.perform(post("/api/workspace/prestamos/" + prestamoId + "/devolver")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void prestarUnContenedor_marcaSusDocumentosYBloqueaPrestamosCruzados() throws Exception {
        Usuario gestor = crearUsuario(obtenerRol("GESTOR_DOCUMENTAL"), "Password123!", true);
        String token = login(gestor.getCorreo(), "Password123!");
        Contenedor contenedor = crearContenedor(gestor);
        String expedienteId = crearExpediente(token, gestor, contenedor);

        String creado = prestar(token, "contenedorId", contenedor.getId().toString(), "Fiscalía Provincial", 201);
        String prestamoId = JsonPath.read(creado, "$.id");
        org.junit.jupiter.api.Assertions.assertEquals("CONTENEDOR", JsonPath.read(creado, "$.objeto"));

        // Los documentos del contenedor cuentan como prestados.
        mockMvc.perform(get("/api/workspace/expedientes/" + expedienteId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.prestado").value(true));
        prestar(token, "expedienteId", expedienteId, "Otra entidad", 400);
        prestar(token, "contenedorId", contenedor.getId().toString(), "Otra entidad", 400);

        mockMvc.perform(post("/api/workspace/prestamos/" + prestamoId + "/devolver")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        prestar(token, "expedienteId", expedienteId, "Otra entidad", 201);
    }

    @Test
    void contenedorSinDocumentos_noSePresta_yElLectorNoVeLosPrestamos() throws Exception {
        Usuario gestor = crearUsuario(obtenerRol("GESTOR_DOCUMENTAL"), "Password123!", true);
        Usuario lector = crearUsuario(obtenerRol("LECTOR"), "Password123!", true);
        String tokenGestor = login(gestor.getCorreo(), "Password123!");
        String tokenLector = login(lector.getCorreo(), "Password123!");

        prestar(tokenGestor, "contenedorId", crearContenedor(gestor).getId().toString(), "Contraloría", 400);
        prestar(tokenLector, "expedienteId", UUID.randomUUID().toString(), "Contraloría", 403);
        mockMvc.perform(get("/api/workspace/prestamos")
                        .header("Authorization", "Bearer " + tokenLector))
                .andExpect(status().isForbidden());
    }
}
