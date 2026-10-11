package com.municipalidadsanjose.archivo.controller;

import com.municipalidadsanjose.archivo.dto.contenedor.ContenedorRequestDTO;
import com.municipalidadsanjose.archivo.dto.contenedor.ContenedorResponseDTO;
import com.municipalidadsanjose.archivo.exception.RecursoNoEncontradoException;
import com.municipalidadsanjose.archivo.security.UsuarioPrincipal;
import com.municipalidadsanjose.archivo.service.ContenedorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Ubicación física (Sección -> Nivel -> Área, tablas estante/nivel/caja) y los
 * contenedores (archivadores/folders) guardados en cada Área. Las lecturas van
 * por SQL para devolver la ruta completa de una vez; las escrituras pasan por el
 * servicio auditado. El alta/edición de Secciones, Niveles y Áreas es solo del
 * administrador y vive en EstanteController/NivelController/CajaController.
 */
@RestController
@RequestMapping("/api/workspace")
public class ContenedorController {

    private final JdbcTemplate db;
    private final ContenedorService contenedores;

    public ContenedorController(JdbcTemplate db, ContenedorService contenedores) {
        this.db = db;
        this.contenedores = contenedores;
    }

    // Orden natural aproximado: "2" antes que "10".
    private static final String ORDEN_CODIGO = "length(%1$s), %1$s";

    @GetMapping("/ubicaciones")
    public Map<String, Object> ubicaciones() {
        var archivo = db.queryForList(
                "SELECT archivo_central_id AS id, nombre FROM archivo_central ORDER BY nombre LIMIT 1");
        return Map.of(
                "archivoCentral", archivo.isEmpty() ? Map.of() : archivo.get(0),
                "secciones", db.queryForList("SELECT estante_id AS id, codigo FROM estante ORDER BY "
                        + ORDEN_CODIGO.formatted("codigo")),
                "niveles", db.queryForList("SELECT nivel_id AS id, codigo, estante_id AS \"seccionId\" FROM nivel ORDER BY "
                        + ORDEN_CODIGO.formatted("codigo")),
                "areas", db.queryForList("""
                        SELECT c.caja_id AS id, c.codigo, c.nivel_id AS "nivelId",
                          (SELECT count(*) FROM contenedor k WHERE k.caja_id = c.caja_id) AS "totalContenedores"
                        FROM caja c ORDER BY """ + " " + ORDEN_CODIGO.formatted("c.codigo")));
    }

    private static final String FROM = """
            FROM contenedor k
            JOIN caja c ON c.caja_id = k.caja_id
            JOIN nivel n ON n.nivel_id = c.nivel_id
            JOIN estante s ON s.estante_id = n.estante_id
            JOIN usuario u ON u.usuario_id = k.creado_por_id
            """;
    private static final String COLUMNS = """
            k.contenedor_id AS id, k.tipo_contenedor AS tipo, k.descripcion,
            k.estado_digitalizacion AS estado, k.fecha_registro AS "fechaRegistro",
            k.fecha_actualizacion AS "fechaActualizacion", u.nombre AS "creadoPorNombre",
            k.caja_id AS "areaId", c.codigo AS area, n.nivel_id AS "nivelId", n.codigo AS nivel,
            s.estante_id AS "seccionId", s.codigo AS seccion,
            (SELECT count(*) FROM expediente e WHERE e.contenedor_id = k.contenedor_id) AS "totalDocumentos"
            """;

    @GetMapping("/contenedores")
    public Map<String, Object> listar(@RequestParam Map<String, String> q,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        var where = new StringBuilder(FROM + " WHERE true");
        var args = new ArrayList<Object>();
        filtro(where, args, q, "estado", "k.estado_digitalizacion");
        filtro(where, args, q, "areaId", "k.caja_id::text");
        filtro(where, args, q, "nivelId", "n.nivel_id::text");
        filtro(where, args, q, "seccionId", "s.estante_id::text");
        String texto = q.getOrDefault("texto", "").trim();
        if (!texto.isEmpty()) {
            where.append(" AND unaccent(k.descripcion) ILIKE unaccent(?)");
            args.add("%" + texto + "%");
        }
        page = Math.max(page, 0);
        size = Math.max(1, Math.min(size, 100));
        long total = db.queryForObject("SELECT count(*) " + where, Long.class, args.toArray());
        var values = new ArrayList<>(args);
        values.add(size);
        values.add(page * size);
        // Los pendientes primero: son los que alguien tiene que retomar.
        var contenido = db.queryForList("SELECT " + COLUMNS + " " + where
                + " ORDER BY (k.estado_digitalizacion = 'COMPLETADO'), k.fecha_actualizacion DESC, k.contenedor_id"
                + " LIMIT ? OFFSET ?", values.toArray());
        return Map.of("contenido", contenido, "pagina", page, "tamano", size,
                "totalElementos", total, "totalPaginas", (total + size - 1) / size);
    }

    @GetMapping("/contenedores/{id}")
    public Map<String, Object> detalle(@PathVariable UUID id) {
        var rows = db.queryForList("SELECT " + COLUMNS + " " + FROM + " WHERE k.contenedor_id = ?", id);
        if (rows.isEmpty()) throw new RecursoNoEncontradoException("Contenedor", id);
        return rows.get(0);
    }

    @PostMapping("/contenedores")
    @PreAuthorize("hasAnyRole('ADMIN','GESTOR_DOCUMENTAL')")
    public ResponseEntity<ContenedorResponseDTO> crear(@Valid @RequestBody ContenedorRequestDTO dto,
                                                       @AuthenticationPrincipal UsuarioPrincipal user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contenedores.crear(dto, user.getId()));
    }

    @PutMapping("/contenedores/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','GESTOR_DOCUMENTAL')")
    public ContenedorResponseDTO actualizar(@PathVariable UUID id, @Valid @RequestBody ContenedorRequestDTO dto) {
        return contenedores.actualizar(id, dto);
    }

    @PostMapping("/contenedores/{id}/completar")
    @PreAuthorize("hasAnyRole('ADMIN','GESTOR_DOCUMENTAL')")
    public ContenedorResponseDTO completar(@PathVariable UUID id) {
        return contenedores.completar(id);
    }

    @PostMapping("/contenedores/{id}/reabrir")
    @PreAuthorize("hasAnyRole('ADMIN','GESTOR_DOCUMENTAL')")
    public ContenedorResponseDTO reabrir(@PathVariable UUID id) {
        return contenedores.reabrir(id);
    }

    @DeleteMapping("/contenedores/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','GESTOR_DOCUMENTAL')")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        contenedores.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    private void filtro(StringBuilder where, List<Object> args, Map<String, String> q, String key, String field) {
        if (q.containsKey(key) && !q.get(key).isBlank()) {
            where.append(" AND ").append(field).append(" = ?");
            args.add(q.get(key));
        }
    }
}
