package com.municipalidadsanjose.archivo.integration;

import com.jayway.jsonpath.JsonPath;
import com.municipalidadsanjose.archivo.entity.Caja;
import com.municipalidadsanjose.archivo.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ContenedorIntegrationTest extends IntegrationTestBase {

    private String crearContenedor(String token, UUID cajaId) throws Exception {
        String respuesta = mockMvc.perform(post("/api/workspace/contenedores")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cajaId":"%s","tipo":"ARCHIVADOR","descripcion":"Archivador negro - Obra Pista"}
                                """.formatted(cajaId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.id");
    }

    private org.springframework.test.web.servlet.ResultActions registrarDocumento(String token, String contenedorId)
            throws Exception {
        var area = crearArea();
        var tipo = crearTipoDocumental();
        return mockMvc.perform(post("/api/workspace/recepcion")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"numeroDocumento":"INF-20-2023","remitente":"Sub Gerencia de Obras",
                         "destinatario":"Gerencia Municipal","areaDestinoId":"%s","tipoId":"%s",
                         "fechaDocumento":"%s","asunto":"Informe de avance de obra","glosa":"",
                         "numeroFolios":4,"contenedorId":"%s"}
                        """.formatted(area.getId(), tipo.getId(), LocalDate.now(), contenedorId)));
    }

    @Test
    void flujoCompleto_pendienteCompletadoYReabrir() throws Exception {
        Usuario gestor = crearUsuario(obtenerRol("GESTOR_DOCUMENTAL"), "Password123!", true);
        String token = login(gestor.getCorreo(), "Password123!");
        Caja caja = crearCajaConJerarquia();
        String contenedorId = crearContenedor(token, caja.getId());

        // No se puede completar un contenedor sin documentos.
        mockMvc.perform(post("/api/workspace/contenedores/" + contenedorId + "/completar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        registrarDocumento(token, contenedorId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinatario").value("Gerencia Municipal"))
                .andExpect(jsonPath("$.numeroFolios").value(4));

        mockMvc.perform(post("/api/workspace/contenedores/" + contenedorId + "/completar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COMPLETADO"));

        // Completado: no admite más documentos hasta reabrirlo.
        registrarDocumento(token, contenedorId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("Reábrelo")));

        mockMvc.perform(post("/api/workspace/contenedores/" + contenedorId + "/reabrir")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));

        registrarDocumento(token, contenedorId).andExpect(status().isOk());

        // La búsqueda devuelve la ubicación completa del documento.
        mockMvc.perform(get("/api/workspace/buscar")
                        .param("contenedorId", contenedorId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].seccion").value("E1"))
                .andExpect(jsonPath("$.contenido[0].nivel").value("N1"))
                .andExpect(jsonPath("$.contenido[0].area").value("C1"))
                .andExpect(jsonPath("$.contenido[0].contenedorDescripcion").value("Archivador negro - Obra Pista"));

        mockMvc.perform(get("/api/workspace/contenedores/" + contenedorId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDocumentos").value(2))
                .andExpect(jsonPath("$.seccion").value("E1"));

        // Con documentos no se puede borrar.
        mockMvc.perform(delete("/api/workspace/contenedores/" + contenedorId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void contenedorVacio_sePuedeEliminar() throws Exception {
        Usuario gestor = crearUsuario(obtenerRol("GESTOR_DOCUMENTAL"), "Password123!", true);
        String token = login(gestor.getCorreo(), "Password123!");
        String contenedorId = crearContenedor(token, crearCajaConJerarquia().getId());

        mockMvc.perform(delete("/api/workspace/contenedores/" + contenedorId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void lector_noPuedeCrearContenedores() throws Exception {
        Usuario lector = crearUsuario(obtenerRol("LECTOR"), "Password123!", true);
        String token = login(lector.getCorreo(), "Password123!");

        mockMvc.perform(post("/api/workspace/contenedores")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cajaId":"%s","tipo":"FOLDER","descripcion":"Folder verde"}
                                """.formatted(crearCajaConJerarquia().getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void recepcion_sinContenedor_esRechazada() throws Exception {
        Usuario gestor = crearUsuario(obtenerRol("GESTOR_DOCUMENTAL"), "Password123!", true);
        String token = login(gestor.getCorreo(), "Password123!");
        var area = crearArea();
        var tipo = crearTipoDocumental();

        mockMvc.perform(post("/api/workspace/recepcion")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"numeroDocumento":"X-1","remitente":"R","areaDestinoId":"%s","tipoId":"%s",
                                 "fechaDocumento":"%s","asunto":"Sin contenedor"}
                                """.formatted(area.getId(), tipo.getId(), LocalDate.now())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void gestor_noPuedeCrearUbicaciones_peroSiConsultarlas() throws Exception {
        Usuario gestor = crearUsuario(obtenerRol("GESTOR_DOCUMENTAL"), "Password123!", true);
        String token = login(gestor.getCorreo(), "Password123!");
        Caja caja = crearCajaConJerarquia();

        mockMvc.perform(post("/api/cajas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nivelId":"%s","codigo":"NUEVA"}
                                """.formatted(caja.getNivel().getId())))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/workspace/ubicaciones")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.areas").isArray());
    }

    @Test
    void admin_creaYEliminaUbicaciones() throws Exception {
        Usuario admin = crearUsuario(obtenerRolAdmin(), "Password123!", true);
        String token = login(admin.getCorreo(), "Password123!");
        Caja caja = crearCajaConJerarquia();

        String respuesta = mockMvc.perform(post("/api/cajas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nivelId":"%s","codigo":"A2"}
                                """.formatted(caja.getNivel().getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String nuevaId = JsonPath.read(respuesta, "$.id");

        // Un Área con contenedores no se puede borrar; una vacía sí.
        crearContenedor(token, caja.getId());
        mockMvc.perform(delete("/api/cajas/" + caja.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/cajas/" + nuevaId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }
}
