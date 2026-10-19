package com.municipalidadsanjose.archivo.integration;

import com.jayway.jsonpath.JsonPath;
import com.municipalidadsanjose.archivo.entity.*;
import com.municipalidadsanjose.archivo.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Usa la base Postgres de desarrollo; cada test se revierte al terminar.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "app.ocr.enabled=false")
@AutoConfigureMockMvc
@Transactional
public abstract class IntegrationTestBase {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected RolRepository rolRepository;
    @Autowired protected UsuarioRepository usuarioRepository;
    @Autowired protected AreaResponsableRepository areaResponsableRepository;
    @Autowired protected TipoDocumentalRepository tipoDocumentalRepository;
    @Autowired protected EstadoExpedienteRepository estadoExpedienteRepository;
    @Autowired protected ArchivoCentralRepository archivoCentralRepository;
    @Autowired protected EstanteRepository estanteRepository;
    @Autowired protected NivelRepository nivelRepository;
    @Autowired protected CajaRepository cajaRepository;
    @Autowired protected ContenedorRepository contenedorRepository;
    @Autowired protected PasswordEncoder passwordEncoder;

    private String sufijoUnico() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    protected Rol obtenerRolAdmin() {
        return rolRepository.findByNombre("ADMIN")
                .orElseThrow(() -> new IllegalStateException(
                        "Falta el rol ADMIN sembrado por V2__seed_admin.sql; ¿corrió Flyway?"));
    }

    protected Rol obtenerRol(String nombre) {
        return rolRepository.findByNombre(nombre)
                .orElseThrow(() -> new IllegalStateException(
                        "Falta el rol " + nombre + " sembrado por las migraciones de Flyway"));
    }

    protected Rol crearRol(String prefijoNombre) {
        Rol rol = new Rol();
        rol.setNombre(prefijoNombre + "_" + sufijoUnico());
        rol.setDescripcion("Rol de prueba");
        return rolRepository.save(rol);
    }

    protected Usuario crearUsuario(Rol rol, String password, boolean activo) {
        Usuario usuario = new Usuario();
        usuario.setNombre("Usuario Test");
        usuario.setCorreo("test_" + sufijoUnico() + "@sanjose.gob.pe");
        usuario.setPasswordHash(passwordEncoder.encode(password));
        usuario.setRol(rol);
        usuario.setActivo(activo);
        return usuarioRepository.save(usuario);
    }

    protected Caja crearCajaConJerarquia() {
        AreaResponsable area = new AreaResponsable();
        area.setNombre("Area_" + sufijoUnico());
        area.setCodigo("COD_" + sufijoUnico());
        areaResponsableRepository.save(area);

        ArchivoCentral archivoCentral = new ArchivoCentral();
        archivoCentral.setNombre("Archivo_" + sufijoUnico());
        archivoCentralRepository.save(archivoCentral);

        Estante estante = new Estante();
        estante.setArchivoCentral(archivoCentral);
        estante.setCodigo("E1");
        estanteRepository.save(estante);

        Nivel nivel = new Nivel();
        nivel.setEstante(estante);
        nivel.setCodigo("N1");
        nivelRepository.save(nivel);

        Caja caja = new Caja();
        caja.setNivel(nivel);
        caja.setCodigo("C1");
        return cajaRepository.save(caja);
    }

    protected Contenedor crearContenedor(Usuario creador) {
        Contenedor contenedor = new Contenedor();
        contenedor.setCaja(crearCajaConJerarquia());
        contenedor.setTipo(com.municipalidadsanjose.archivo.enums.TipoContenedor.ARCHIVADOR);
        contenedor.setDescripcion("Archivador de prueba");
        contenedor.setCreadoPor(creador);
        return contenedorRepository.save(contenedor);
    }

    protected AreaResponsable crearArea() {
        AreaResponsable area = new AreaResponsable();
        area.setNombre("Area_" + sufijoUnico());
        area.setCodigo("COD_" + sufijoUnico());
        return areaResponsableRepository.save(area);
    }

    protected TipoDocumental crearTipoDocumental() {
        TipoDocumental tipo = new TipoDocumental();
        tipo.setNombre("Tipo_" + sufijoUnico());
        return tipoDocumentalRepository.save(tipo);
    }

    protected EstadoExpediente crearEstadoExpediente() {
        EstadoExpediente estado = new EstadoExpediente();
        estado.setNombre("Estado_" + sufijoUnico());
        return estadoExpedienteRepository.save(estado);
    }

    protected String expedienteRequestJson(UUID areaId, UUID tipoId, UUID estadoId, UUID contenedorId, UUID creadoPorId) {
        return """
                {
                  "codigoUnico": "EXP-%s",
                  "numeroDocumento": "DOC-%s",
                  "remitente": "Remitente Test",
                  "areaDestinoId": "%s",
                  "tipoId": "%s",
                  "estadoId": "%s",
                  "fechaDocumento": "%s",
                  "asunto": "Asunto de prueba",
                  "glosa": "glosa",
                  "contenedorId": "%s",
                  "numeroFolios": 3,
                  "creadoPorId": "%s"
                }
                """.formatted(sufijoUnico(), sufijoUnico(), areaId, tipoId, estadoId,
                LocalDate.now(), contenedorId, creadoPorId);
    }

    protected String login(String correo, String password) throws Exception {
        String body = """
                {"correo":"%s","password":"%s"}
                """.formatted(correo, password);
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.token");
    }
}
