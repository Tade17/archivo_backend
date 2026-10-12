package com.municipalidadsanjose.archivo.controller;

import com.municipalidadsanjose.archivo.dto.prestamo.PrestamoRequestDTO;
import com.municipalidadsanjose.archivo.exception.RecursoNoEncontradoException;
import com.municipalidadsanjose.archivo.security.UsuarioPrincipal;
import com.municipalidadsanjose.archivo.service.PrestamoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Préstamos físicos a entidades externas. Solo administrador y gestor documental:
 * es información sensible. Las lecturas van por SQL para calcular el semáforo
 * en la misma consulta: rojo si venció y no volvió, amarillo si vence en 2 días
 * o menos, verde en plazo, gris si ya fue devuelto.
 */
@RestController
@RequestMapping("/api/workspace/prestamos")
@PreAuthorize("hasAnyRole('ADMIN','GESTOR_DOCUMENTAL')")
public class PrestamoController {

    private static final int DIAS_AVISO = 2;

    private final JdbcTemplate db;
    private final PrestamoService prestamos;

    public PrestamoController(JdbcTemplate db, PrestamoService prestamos) {
        this.db = db;
        this.prestamos = prestamos;
    }

    private static final String FROM = """
            FROM prestamo p
            LEFT JOIN expediente e ON e.expediente_id = p.expediente_id
            LEFT JOIN contenedor k ON k.contenedor_id = p.contenedor_id
            JOIN usuario u ON u.usuario_id = p.registrado_por_id
            """;

    private static final String COLUMNS = """
            p.prestamo_id AS id,
            CASE WHEN p.contenedor_id IS NULL THEN 'DOCUMENTO' ELSE 'CONTENEDOR' END AS objeto,
            p.expediente_id AS "expedienteId", e.codigo_unico AS "expedienteCodigo", e.asunto AS "expedienteAsunto",
            p.contenedor_id AS "contenedorId", k.descripcion AS "contenedorDescripcion",
            p.solicitante_nombre AS solicitante, p.observaciones,
            p.fecha_solicitud AS "fechaSolicitud",
            p.fecha_devolucion_prevista AS "fechaDevolucionPrevista",
            p.fecha_devolucion_real AS "fechaDevolucionReal",
            p.estado, u.nombre AS "registradoPor",
            CASE WHEN p.estado = 'DEVUELTO' THEN NULL
                 ELSE p.fecha_devolucion_prevista - CURRENT_DATE END AS "diasRestantes",
            CASE WHEN p.estado = 'DEVUELTO' THEN 'GRIS'
                 WHEN p.fecha_devolucion_prevista < CURRENT_DATE THEN 'ROJO'
                 WHEN p.fecha_devolucion_prevista - CURRENT_DATE <= %d THEN 'AMARILLO'
                 ELSE 'VERDE' END AS semaforo
            """.formatted(DIAS_AVISO);

    @GetMapping
    public Map<String, Object> listar(@RequestParam Map<String, String> q,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        var where = new StringBuilder(FROM + " WHERE true");
        var args = new ArrayList<Object>();
        // ACTIVOS (por defecto) = sin devolver; VENCIDOS; DEVUELTOS; TODOS.
        switch (q.getOrDefault("estado", "ACTIVOS")) {
            case "VENCIDOS" -> where.append(" AND p.estado = 'PRESTADO' AND p.fecha_devolucion_prevista < CURRENT_DATE");
            case "DEVUELTOS" -> where.append(" AND p.estado = 'DEVUELTO'");
            case "TODOS" -> { }
            default -> where.append(" AND p.estado = 'PRESTADO'");
        }
        String texto = q.getOrDefault("texto", "").trim();
        if (!texto.isEmpty()) {
            where.append(" AND (unaccent(p.solicitante_nombre) ILIKE unaccent(?)"
                    + " OR unaccent(coalesce(e.codigo_unico,'') || ' ' || coalesce(e.asunto,'')) ILIKE unaccent(?)"
                    + " OR unaccent(coalesce(k.descripcion,'')) ILIKE unaccent(?))");
            for (int i = 0; i < 3; i++) args.add("%" + texto + "%");
        }
        page = Math.max(page, 0);
        size = Math.max(1, Math.min(size, 100));
        long total = db.queryForObject("SELECT count(*) " + where, Long.class, args.toArray());
        var values = new ArrayList<>(args);
        values.add(size);
        values.add(page * size);
        // Los sin devolver primero, y entre ellos los que vencen antes.
        var contenido = db.queryForList("SELECT " + COLUMNS + " " + where
                + " ORDER BY (p.estado = 'DEVUELTO'),"
                + " CASE WHEN p.estado = 'PRESTADO' THEN p.fecha_devolucion_prevista END ASC,"
                + " p.fecha_devolucion_real DESC NULLS LAST, p.prestamo_id LIMIT ? OFFSET ?", values.toArray());
        return Map.of("contenido", contenido, "pagina", page, "tamano", size,
                "totalElementos", total, "totalPaginas", (total + size - 1) / size);
    }

    /** Contadores para la alerta: sin devolver, por vencer y vencidos. */
    @GetMapping("/resumen")
    public Map<String, Object> resumen() {
        return db.queryForMap("""
                SELECT count(*) FILTER (WHERE estado = 'PRESTADO') AS "sinDevolver",
                       count(*) FILTER (WHERE estado = 'PRESTADO'
                                          AND fecha_devolucion_prevista < CURRENT_DATE) AS vencidos,
                       count(*) FILTER (WHERE estado = 'PRESTADO'
                                          AND fecha_devolucion_prevista >= CURRENT_DATE
                                          AND fecha_devolucion_prevista - CURRENT_DATE <= %d) AS "porVencer"
                FROM prestamo
                """.formatted(DIAS_AVISO));
    }

    @GetMapping("/{id}")
    public Map<String, Object> detalle(@PathVariable UUID id) {
        var rows = db.queryForList("SELECT " + COLUMNS + " " + FROM + " WHERE p.prestamo_id = ?", id);
        if (rows.isEmpty()) throw new RecursoNoEncontradoException("Prestamo", id);
        return rows.get(0);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@Valid @RequestBody PrestamoRequestDTO dto,
                                                     @AuthenticationPrincipal UsuarioPrincipal user) {
        var creado = prestamos.crear(dto, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(detalle(creado.id()));
    }

    @PostMapping("/{id}/devolver")
    public Map<String, Object> devolver(@PathVariable UUID id) {
        prestamos.devolver(id);
        return detalle(id);
    }
}
