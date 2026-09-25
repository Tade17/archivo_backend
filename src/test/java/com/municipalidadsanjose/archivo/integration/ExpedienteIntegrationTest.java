package com.municipalidadsanjose.archivo.integration;

import com.municipalidadsanjose.archivo.entity.Caja;
import com.municipalidadsanjose.archivo.entity.Rol;
import com.municipalidadsanjose.archivo.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Regresión del bug de flush: crear()/actualizar() devolvían fechaRegistro/
// fechaActualizacion en null (o con el valor viejo) porque el INSERT/UPDATE
// recién se ejecutaba al hacer commit, no al llamar a save(). Se corrigió con
// saveAndFlush()/flush() explícito. También cubre la paginación de listarTodos().
class ExpedienteIntegrationTest extends IntegrationTestBase {

    @Test
    void recepcionDigital_generaCorrelativoSinPedirNumeroAlUsuario() throws Exception {
        var area = crearArea();
        var tipo = crearTipoDocumental();
        Usuario usuario = crearUsuario(obtenerRol("GESTOR_DOCUMENTAL"), "Password123!", true);
        String token = login(usuario.getCorreo(), "Password123!");
        String body = """
                {
                  "numeroDocumento":"DOC-PRUEBA",
                  "remitente":"Remitente Test",
                  "areaDestinoId":"%s",
                  "tipoId":"%s",
                  "fechaDocumento":"%s",
                  "asunto":"Documento digital de prueba",
                  "glosa":""
                }
                """.formatted(area.getId(), tipo.getId(), java.time.LocalDate.now());

        mockMvc.perform(post("/api/workspace/recepcion")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoUnico", matchesPattern("EXP-\\d{4}-\\d{6}")));
    }

    @Test
    void buscar_ignoraTildesYMayusculas() throws Exception {
        var area = crearArea();
        var tipo = crearTipoDocumental();
        Usuario usuario = crearUsuario(obtenerRol("GESTOR_DOCUMENTAL"), "Password123!", true);
        String token = login(usuario.getCorreo(), "Password123!");
        String body = """
                {
                  "numeroDocumento":"DOC-TILDES",
                  "remitente":"Remitente Test",
                  "areaDestinoId":"%s",
                  "tipoId":"%s",
                  "fechaDocumento":"%s",
                  "asunto":"Inspección del CAMIÓN zorzalito",
                  "glosa":""
                }
                """.formatted(area.getId(), tipo.getId(), java.time.LocalDate.now());
        mockMvc.perform(post("/api/workspace/recepcion")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // Sin tilde y en minúsculas encuentra el asunto escrito con tilde y en mayúsculas...
        mockMvc.perform(get("/api/workspace/buscar")
                        .param("texto", "inspeccion del camion zorzalito")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.contenido[0].asunto", containsString("zorzalito")));

        // ...y con tilde también.
        mockMvc.perform(get("/api/workspace/buscar")
                        .param("texto", "camión zorzalito")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos", greaterThanOrEqualTo(1)));
    }

    private String crearExpedienteYObtenerRespuesta() throws Exception {
        Caja caja = crearCajaConJerarquia();
        var area = crearArea();
        var tipo = crearTipoDocumental();
        var estado = crearEstadoExpediente();
        Usuario usuario = crearUsuario(obtenerRol("GESTOR_DOCUMENTAL"), "Password123!", true);
        String token = login(usuario.getCorreo(), "Password123!");

        String body = expedienteRequestJson(area.getId(), tipo.getId(), estado.getId(), caja.getId(), usuario.getId());

        return mockMvc.perform(post("/api/expedientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fechaRegistro").isNotEmpty())
                .andExpect(jsonPath("$.fechaActualizacion").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void crear_devuelveTimestampsPobladosEnLaMismaRespuesta() throws Exception {
        crearExpedienteYObtenerRespuesta();
        // Las aserciones de fecha ya corrieron dentro de crearExpedienteYObtenerRespuesta();
        // si el bug de flush reapareciera, ese método fallaría antes de llegar acá.
    }

    @Test
    void actualizar_devuelveFechaActualizacionRenovada() throws Exception {
        Caja caja = crearCajaConJerarquia();
        var area = crearArea();
        var tipo = crearTipoDocumental();
        var estado = crearEstadoExpediente();
        Usuario usuario = crearUsuario(obtenerRol("GESTOR_DOCUMENTAL"), "Password123!", true);
        String token = login(usuario.getCorreo(), "Password123!");

        String bodyCrear = expedienteRequestJson(area.getId(), tipo.getId(), estado.getId(), caja.getId(), usuario.getId());
        String respuestaCrear = mockMvc.perform(post("/api/expedientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyCrear))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(com.jayway.jsonpath.JsonPath.read(respuestaCrear, "$.id").toString());
        String fechaActualizacionOriginal = com.jayway.jsonpath.JsonPath.read(respuestaCrear, "$.fechaActualizacion");

        // Cambia el asunto para forzar un UPDATE real.
        String bodyActualizar = expedienteRequestJson(area.getId(), tipo.getId(), estado.getId(), caja.getId(), usuario.getId())
                .replace("\"asunto\": \"Asunto de prueba\"", "\"asunto\": \"Asunto editado\"");
        // El código único debe mantenerse igual al original para no chocar con existsByCodigoUnico.
        String codigoOriginal = com.jayway.jsonpath.JsonPath.read(respuestaCrear, "$.codigoUnico");
        bodyActualizar = bodyActualizar.replaceFirst("\"codigoUnico\": \"EXP-[^\"]+\"", "\"codigoUnico\": \"" + codigoOriginal + "\"");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/expedientes/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyActualizar))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asunto").value("Asunto editado"))
                .andExpect(jsonPath("$.fechaActualizacion").value(org.hamcrest.Matchers.not(fechaActualizacionOriginal)));
    }

    @Test
    void listarTodos_respetaPaginacion() throws Exception {
        Usuario usuario = crearUsuario(crearRol("TECNICO"), "Password123!", true);
        String token = login(usuario.getCorreo(), "Password123!");

        mockMvc.perform(get("/api/expedientes?page=0&size=5")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamano").value(5))
                .andExpect(jsonPath("$.contenido").isArray());
    }
}
