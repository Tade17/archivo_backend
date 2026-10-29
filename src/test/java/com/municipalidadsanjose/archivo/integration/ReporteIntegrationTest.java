package com.municipalidadsanjose.archivo.integration;

import com.jayway.jsonpath.JsonPath;
import com.municipalidadsanjose.archivo.entity.Contenedor;
import com.municipalidadsanjose.archivo.entity.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReporteIntegrationTest extends IntegrationTestBase {

    private static final List<String> CODIGOS = List.of(
            "expedientes-periodo", "expedientes-tipo", "expedientes-area", "productividad-gestor",
            "avance-contenedores", "contenedores-pendientes", "inventario-ubicacion",
            "prestamos-vencidos", "prestamos-vigentes", "prestamos-entidad", "prestamos-historial",
            "prestamos-mensual");

    @Autowired JdbcTemplate db;
    @Autowired jakarta.persistence.EntityManager em;

    private String tokenGestor() throws Exception {
        Usuario gestor = crearUsuario(obtenerRol("GESTOR_DOCUMENTAL"), "Password123!", true);
        return login(gestor.getCorreo(), "Password123!");
    }

    private String reporte(String token, String codigo, String... filtros) throws Exception {
        em.flush(); // los reportes leen por SQL: lo creado por JPA en la prueba debe estar escrito
        var request = get("/api/workspace/reportes/" + codigo).header("Authorization", "Bearer " + token);
        for (int i = 0; i < filtros.length; i += 2) request = request.param(filtros[i], filtros[i + 1]);
        return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private record Datos(String expedienteId, UUID areaId, UUID tipoId, Contenedor contenedor) { }

    private Datos crearExpediente(String token, Usuario gestor) throws Exception {
        var area = crearArea();
        var tipo = crearTipoDocumental();
        var contenedor = crearContenedor(gestor);
        String json = expedienteRequestJson(area.getId(), tipo.getId(), crearEstadoExpediente().getId(),
                contenedor.getId(), gestor.getId());
        String respuesta = mockMvc.perform(post("/api/expedientes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Datos(JsonPath.read(respuesta, "$.id"), area.getId(), tipo.getId(), contenedor);
    }

    private String prestar(String token, String campo, String id, String solicitante) throws Exception {
        String respuesta = mockMvc.perform(post("/api/workspace/prestamos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"%s\":\"%s\",\"solicitante\":\"%s\",\"dias\":5}".formatted(campo, id, solicitante)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.id");
    }

    @Test
    void soloAdminYGestorVenLosReportes() throws Exception {
        Usuario lector = crearUsuario(obtenerRol("LECTOR"), "Password123!", true);
        String tokenLector = login(lector.getCorreo(), "Password123!");
        mockMvc.perform(get("/api/workspace/reportes").header("Authorization", "Bearer " + tokenLector))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/workspace/reportes/prestamos-vencidos")
                        .header("Authorization", "Bearer " + tokenLector))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/workspace/reportes")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/workspace/reportes").header("Authorization", "Bearer " + tokenGestor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(CODIGOS.size()))
                .andExpect(jsonPath("$[*].codigo", containsInAnyOrder(CODIGOS.toArray())));
    }

    @Test
    void cadaReporteResponde_conSusColumnas() throws Exception {
        String token = tokenGestor();
        for (String codigo : CODIGOS) {
            mockMvc.perform(get("/api/workspace/reportes/" + codigo).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.codigo").value(codigo))
                    .andExpect(jsonPath("$.columnas", not(empty())))
                    .andExpect(jsonPath("$.filas").isArray())
                    .andExpect(jsonPath("$.truncado").value(false));
        }
    }

    @Test
    void reportesDeProduccion_cuentanElExpedienteRegistrado() throws Exception {
        Usuario gestor = crearUsuario(obtenerRol("GESTOR_DOCUMENTAL"), "Password123!", true);
        String token = login(gestor.getCorreo(), "Password123!");
        Datos datos = crearExpediente(token, gestor);
        String hoy = LocalDate.now().toString();

        String porPeriodo = reporte(token, "expedientes-periodo", "agrupar", "DIA", "desde", hoy, "hasta", hoy,
                "areaId", datos.areaId().toString());
        org.junit.jupiter.api.Assertions.assertEquals(1, (int) JsonPath.read(porPeriodo, "$.filas.length()"));
        org.junit.jupiter.api.Assertions.assertEquals(1, (int) JsonPath.read(porPeriodo, "$.filas[0].expedientes"));
        org.junit.jupiter.api.Assertions.assertEquals(3, (int) JsonPath.read(porPeriodo, "$.filas[0].folios"));
        org.junit.jupiter.api.Assertions.assertEquals(3, (int) JsonPath.read(porPeriodo, "$.totales.folios"));

        // Por documento: el expediente se ubica por la fecha del documento (hoy en el helper de pruebas).
        String porDocumento = reporte(token, "expedientes-periodo", "fecha", "DOCUMENTO", "agrupar", "ANIO",
                "areaId", datos.areaId().toString());
        org.junit.jupiter.api.Assertions.assertEquals(1, (int) JsonPath.read(porDocumento, "$.filas[0].expedientes"));

        String porTipo = reporte(token, "expedientes-tipo", "areaId", datos.areaId().toString());
        org.junit.jupiter.api.Assertions.assertEquals(1, (int) JsonPath.read(porTipo, "$.filas.length()"));
        org.junit.jupiter.api.Assertions.assertEquals(100.0, ((Number) JsonPath.read(porTipo, "$.filas[0].porcentaje")).doubleValue());

        String porArea = reporte(token, "expedientes-area", "tipoId", datos.tipoId().toString());
        org.junit.jupiter.api.Assertions.assertEquals(1, (int) JsonPath.read(porArea, "$.filas[0].expedientes"));

        // Una fecha pasada no incluye el expediente de hoy.
        String vacio = reporte(token, "expedientes-periodo", "hasta", LocalDate.now().minusDays(1).toString(),
                "areaId", datos.areaId().toString());
        org.junit.jupiter.api.Assertions.assertEquals(0, (int) JsonPath.read(vacio, "$.filas.length()"));

        String productividad = reporte(token, "productividad-gestor", "desde", hoy, "hasta", hoy);
        org.junit.jupiter.api.Assertions.assertTrue(
                ((List<?>) JsonPath.read(productividad, "$.filas[?(@.gestor == 'Usuario Test')].expedientes")).size() >= 1);
    }

    @Test
    void reportesDeContenedores_ylaUbicacion() throws Exception {
        Usuario gestor = crearUsuario(obtenerRol("GESTOR_DOCUMENTAL"), "Password123!", true);
        String token = login(gestor.getCorreo(), "Password123!");
        Datos datos = crearExpediente(token, gestor);

        String avance = reporte(token, "avance-contenedores", "estadoContenedor", "PENDIENTE", "texto", "archivador de prueba");
        org.junit.jupiter.api.Assertions.assertTrue((int) JsonPath.read(avance, "$.filas.length()") >= 1);
        org.junit.jupiter.api.Assertions.assertEquals("Pendiente", JsonPath.read(avance, "$.filas[0].estado"));

        String pendientes = reporte(token, "contenedores-pendientes");
        org.junit.jupiter.api.Assertions.assertTrue((int) JsonPath.read(pendientes, "$.filas.length()") >= 1);

        // Un contenedor pendiente no aparece al pedir solo los completados con ese texto.
        String completados = reporte(token, "avance-contenedores", "estadoContenedor", "COMPLETADO", "texto", "archivador de prueba");
        org.junit.jupiter.api.Assertions.assertEquals(0, (int) JsonPath.read(completados, "$.filas.length()"));

        // El área con contenedor no es "vacía"; la ubicación creada por la prueba figura en el inventario.
        mockMvc.perform(get("/api/workspace/reportes/inventario-ubicacion").param("soloVacias", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas[*].contenedores", everyItem(equalTo(0))));
        String inventario = reporte(token, "inventario-ubicacion");
        org.junit.jupiter.api.Assertions.assertTrue(
                ((List<?>) JsonPath.read(inventario, "$.filas[?(@.contenedores >= 1)]")).size() >= 1);
        org.junit.jupiter.api.Assertions.assertNotNull(datos.contenedor());
    }

    @Test
    void reportesDePrestamos_siguenElCicloVencidoYDevolucion() throws Exception {
        Usuario gestor = crearUsuario(obtenerRol("GESTOR_DOCUMENTAL"), "Password123!", true);
        String token = login(gestor.getCorreo(), "Password123!");
        Datos datos = crearExpediente(token, gestor);
        String entidad = "Entidad " + UUID.randomUUID().toString().substring(0, 8);
        String prestamoId = prestar(token, "expedienteId", datos.expedienteId(), entidad);

        // Recién prestado: vigente, no vencido.
        org.junit.jupiter.api.Assertions.assertEquals(1,
                (int) JsonPath.read(reporte(token, "prestamos-vigentes", "texto", entidad), "$.filas.length()"));
        org.junit.jupiter.api.Assertions.assertEquals(0,
                (int) JsonPath.read(reporte(token, "prestamos-vencidos", "texto", entidad), "$.filas.length()"));

        // Vence hace 3 días.
        db.update("UPDATE prestamo SET fecha_devolucion_prevista = CURRENT_DATE - 3 WHERE prestamo_id = ?::uuid", prestamoId);
        String vencidos = reporte(token, "prestamos-vencidos", "texto", entidad);
        org.junit.jupiter.api.Assertions.assertEquals(1, (int) JsonPath.read(vencidos, "$.filas.length()"));
        org.junit.jupiter.api.Assertions.assertEquals(3, (int) JsonPath.read(vencidos, "$.filas[0].dias"));
        org.junit.jupiter.api.Assertions.assertEquals(0,
                (int) JsonPath.read(reporte(token, "prestamos-vigentes", "texto", entidad), "$.filas.length()"));

        String porEntidad = reporte(token, "prestamos-entidad", "texto", entidad);
        org.junit.jupiter.api.Assertions.assertEquals(1, (int) JsonPath.read(porEntidad, "$.filas[0].prestamos"));
        org.junit.jupiter.api.Assertions.assertEquals(1, (int) JsonPath.read(porEntidad, "$.filas[0].vencidos"));

        mockMvc.perform(post("/api/workspace/prestamos/" + prestamoId + "/devolver")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        // Hibernate conserva la fecha prevista original en memoria y la reescribe al devolver.
        db.update("UPDATE prestamo SET fecha_devolucion_prevista = CURRENT_DATE - 3 WHERE prestamo_id = ?::uuid", prestamoId);

        // Devuelto con atraso: sale de vencidos, queda en el historial y en el resumen por entidad.
        org.junit.jupiter.api.Assertions.assertEquals(0,
                (int) JsonPath.read(reporte(token, "prestamos-vencidos", "texto", entidad), "$.filas.length()"));
        String historial = reporte(token, "prestamos-historial", "texto", entidad);
        org.junit.jupiter.api.Assertions.assertEquals("Devuelto", JsonPath.read(historial, "$.filas[0].situacion"));
        String despues = reporte(token, "prestamos-entidad", "texto", entidad);
        org.junit.jupiter.api.Assertions.assertEquals(1, (int) JsonPath.read(despues, "$.filas[0].devueltos"));
        org.junit.jupiter.api.Assertions.assertEquals(1, (int) JsonPath.read(despues, "$.filas[0].devueltos_tarde"));

        String mensual = reporte(token, "prestamos-mensual", "desde", LocalDate.now().toString(), "hasta", LocalDate.now().toString());
        org.junit.jupiter.api.Assertions.assertTrue((int) JsonPath.read(mensual, "$.filas[0].prestados") >= 1);
    }

    @Test
    void prestarUnArchivador_apareceEnLosReportes() throws Exception {
        Usuario gestor = crearUsuario(obtenerRol("GESTOR_DOCUMENTAL"), "Password123!", true);
        String token = login(gestor.getCorreo(), "Password123!");
        Contenedor contenedor = crearExpediente(token, gestor).contenedor();
        String entidad = "Fiscalía " + UUID.randomUUID().toString().substring(0, 8);
        prestar(token, "contenedorId", contenedor.getId().toString(), entidad);

        String vigentes = reporte(token, "prestamos-vigentes", "texto", entidad);
        org.junit.jupiter.api.Assertions.assertEquals("Archivador / folder completo", JsonPath.read(vigentes, "$.filas[0].tipo"));
        org.junit.jupiter.api.Assertions.assertEquals("Archivador de prueba", JsonPath.read(vigentes, "$.filas[0].objeto"));
    }

    private static final List<String> CODIGOS_ADMIN = List.of(
            "actividad-usuario", "eliminaciones", "descargas-consultas", "usuarios-estado");

    private String tokenAdmin(Usuario admin) throws Exception {
        return login(admin.getCorreo(), "Password123!");
    }

    @Test
    void reportesDeSeguridad_sonSoloParaElAdministrador() throws Exception {
        Usuario admin = crearUsuario(obtenerRolAdmin(), "Password123!", true);
        String tokenAdmin = tokenAdmin(admin);
        String tokenGestor = tokenGestor();

        mockMvc.perform(get("/api/workspace/reportes").header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(CODIGOS.size() + CODIGOS_ADMIN.size()))
                .andExpect(jsonPath("$[*].codigo", hasItems(CODIGOS_ADMIN.toArray())));
        mockMvc.perform(get("/api/workspace/reportes").header("Authorization", "Bearer " + tokenGestor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].codigo", not(hasItem("eliminaciones"))));
        for (String codigo : CODIGOS_ADMIN) {
            mockMvc.perform(get("/api/workspace/reportes/" + codigo).header("Authorization", "Bearer " + tokenGestor))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/workspace/reportes/" + codigo).header("Authorization", "Bearer " + tokenAdmin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.columnas", not(empty())));
        }
    }

    @Test
    void actividadYEliminaciones_muestranQuienHizoQueYCuando() throws Exception {
        Usuario admin = crearUsuario(obtenerRolAdmin(), "Password123!", true);
        String tokenAdmin = tokenAdmin(admin);
        Usuario gestor = crearUsuario(obtenerRol("GESTOR_DOCUMENTAL"), "Password123!", true);
        String tokenGestor = login(gestor.getCorreo(), "Password123!");
        Datos datos = crearExpediente(tokenGestor, gestor);
        String hoy = LocalDate.now().toString();

        // El gestor creó un expediente: aparece en su actividad.
        String actividad = reporte(tokenAdmin, "actividad-usuario", "texto", gestor.getCorreo(), "desde", hoy, "hasta", hoy);
        org.junit.jupiter.api.Assertions.assertEquals(1, (int) JsonPath.read(actividad, "$.filas.length()"));
        org.junit.jupiter.api.Assertions.assertTrue((int) JsonPath.read(actividad, "$.filas[0].creaciones") >= 1);
        org.junit.jupiter.api.Assertions.assertEquals(0, (int) JsonPath.read(actividad, "$.filas[0].eliminaciones"));

        // El administrador elimina el expediente: queda registrado con el nombre de lo eliminado.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/expedientes/" + datos.expedienteId())
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());
        String eliminaciones = reporte(tokenAdmin, "eliminaciones", "texto", "Asunto de prueba", "desde", hoy, "hasta", hoy);
        org.junit.jupiter.api.Assertions.assertTrue((int) JsonPath.read(eliminaciones, "$.filas.length()") >= 1);
        org.junit.jupiter.api.Assertions.assertEquals("Expediente", JsonPath.read(eliminaciones, "$.filas[0].tipo"));
        org.junit.jupiter.api.Assertions.assertEquals(datos.expedienteId(), JsonPath.read(eliminaciones, "$.filas[0].identificador"));
        org.junit.jupiter.api.Assertions.assertTrue(((String) JsonPath.read(eliminaciones, "$.filas[0].que")).contains("Asunto de prueba"));

        String actividadAdmin = reporte(tokenAdmin, "actividad-usuario", "texto", admin.getCorreo(), "desde", hoy, "hasta", hoy);
        org.junit.jupiter.api.Assertions.assertEquals(1, (int) JsonPath.read(actividadAdmin, "$.filas[0].eliminaciones"));
        // Una fecha pasada no incluye lo de hoy.
        String vacio = reporte(tokenAdmin, "eliminaciones", "texto", "Asunto de prueba", "hasta", LocalDate.now().minusDays(1).toString());
        org.junit.jupiter.api.Assertions.assertEquals(0, (int) JsonPath.read(vacio, "$.filas.length()"));
    }

    @Test
    void descargasYConsultas_cuentanPorUsuario() throws Exception {
        Usuario admin = crearUsuario(obtenerRolAdmin(), "Password123!", true);
        String tokenAdmin = tokenAdmin(admin);
        Usuario lector = crearUsuario(obtenerRol("LECTOR"), "Password123!", true);
        em.flush();
        UUID documento = UUID.randomUUID();
        for (String accion : List.of("DESCARGAR", "CONSULTAR", "CONSULTAR")) {
            db.update("INSERT INTO auditoria(usuario_id, entidad_afectada, entidad_id, accion) VALUES (?, 'DocumentoDigital', ?, ?)",
                    lector.getId(), documento, accion);
        }
        String r = reporte(tokenAdmin, "descargas-consultas", "texto", lector.getCorreo());
        org.junit.jupiter.api.Assertions.assertEquals(1, (int) JsonPath.read(r, "$.filas.length()"));
        org.junit.jupiter.api.Assertions.assertEquals(1, (int) JsonPath.read(r, "$.filas[0].descargas"));
        org.junit.jupiter.api.Assertions.assertEquals(2, (int) JsonPath.read(r, "$.filas[0].consultas"));
        org.junit.jupiter.api.Assertions.assertEquals(1, (int) JsonPath.read(r, "$.filas[0].documentos_distintos"));
    }

    @Test
    void usuariosEstado_distingueActivosSuspendidosEInactivos() throws Exception {
        Usuario admin = crearUsuario(obtenerRolAdmin(), "Password123!", true);
        String tokenAdmin = tokenAdmin(admin);
        Usuario nuncaActivo = crearUsuario(obtenerRol("LECTOR"), "Password123!", true);
        Usuario suspendido = crearUsuario(obtenerRol("LECTOR"), "Password123!", false);
        Usuario antiguo = crearUsuario(obtenerRol("GESTOR_DOCUMENTAL"), "Password123!", true);
        em.flush();
        db.update("INSERT INTO auditoria(usuario_id, entidad_afectada, entidad_id, accion, fecha) VALUES (?, 'Expediente', ?, 'CREAR', now() - interval '60 days')",
                antiguo.getId(), UUID.randomUUID());

        String sinActividad = reporte(tokenAdmin, "usuarios-estado", "texto", nuncaActivo.getCorreo());
        org.junit.jupiter.api.Assertions.assertEquals("Sin actividad registrada", JsonPath.read(sinActividad, "$.filas[0].situacion"));
        String susp = reporte(tokenAdmin, "usuarios-estado", "texto", suspendido.getCorreo());
        org.junit.jupiter.api.Assertions.assertEquals("Suspendida", JsonPath.read(susp, "$.filas[0].situacion"));
        org.junit.jupiter.api.Assertions.assertEquals("Suspendida", JsonPath.read(susp, "$.filas[0].estado"));

        // 60 días sin actividad: inactivo con umbral de 30, pero reciente con umbral de 90.
        String a30 = reporte(tokenAdmin, "usuarios-estado", "texto", antiguo.getCorreo(), "dias", "30");
        org.junit.jupiter.api.Assertions.assertEquals("Sin actividad reciente", JsonPath.read(a30, "$.filas[0].situacion"));
        org.junit.jupiter.api.Assertions.assertEquals(60, (int) JsonPath.read(a30, "$.filas[0].dias_sin_actividad"));
        String a90 = reporte(tokenAdmin, "usuarios-estado", "texto", antiguo.getCorreo(), "dias", "90");
        org.junit.jupiter.api.Assertions.assertEquals("Con actividad reciente", JsonPath.read(a90, "$.filas[0].situacion"));

        // Filtros por estado.
        org.junit.jupiter.api.Assertions.assertEquals(1,
                (int) JsonPath.read(reporte(tokenAdmin, "usuarios-estado", "estadoUsuario", "INACTIVOS", "texto", antiguo.getCorreo()), "$.filas.length()"));
        org.junit.jupiter.api.Assertions.assertEquals(0,
                (int) JsonPath.read(reporte(tokenAdmin, "usuarios-estado", "estadoUsuario", "INACTIVOS", "texto", suspendido.getCorreo()), "$.filas.length()"));
        org.junit.jupiter.api.Assertions.assertEquals(1,
                (int) JsonPath.read(reporte(tokenAdmin, "usuarios-estado", "estadoUsuario", "SUSPENDIDOS", "texto", suspendido.getCorreo()), "$.filas.length()"));
        org.junit.jupiter.api.Assertions.assertEquals(0,
                (int) JsonPath.read(reporte(tokenAdmin, "usuarios-estado", "estadoUsuario", "ACTIVOS", "texto", suspendido.getCorreo()), "$.filas.length()"));

        mockMvc.perform(get("/api/workspace/reportes/usuarios-estado").param("dias", "abc")
                .header("Authorization", "Bearer " + tokenAdmin)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/workspace/reportes/usuarios-estado").param("estadoUsuario", "OTRO")
                .header("Authorization", "Bearer " + tokenAdmin)).andExpect(status().isBadRequest());
    }

    @Test
    void filtrosInvalidos_sonRechazados() throws Exception {
        String token = tokenGestor();
        mockMvc.perform(get("/api/workspace/reportes/expedientes-periodo").param("agrupar", "SEMANA")
                .header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/workspace/reportes/expedientes-periodo").param("desde", "2026-12-31").param("hasta", "2026-01-01")
                .header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/workspace/reportes/expedientes-periodo").param("desde", "ayer")
                .header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/workspace/reportes/expedientes-tipo").param("areaId", "no-es-uuid")
                .header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/workspace/reportes/avance-contenedores").param("estadoContenedor", "OTRO")
                .header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/workspace/reportes/no-existe")
                .header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
    }
}
