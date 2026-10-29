package com.municipalidadsanjose.archivo.service;

import com.municipalidadsanjose.archivo.dto.reporte.ColumnaReporteDTO;
import com.municipalidadsanjose.archivo.dto.reporte.ReporteDefinicionDTO;
import com.municipalidadsanjose.archivo.dto.reporte.ReporteResultadoDTO;
import com.municipalidadsanjose.archivo.exception.RecursoNoEncontradoException;
import com.municipalidadsanjose.archivo.exception.SolicitudInvalidaException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Reportes de consulta sobre los datos del archivo. Cada reporte devuelve columnas, filas y
 * totales con la misma forma, de modo que la pantalla y la exportación a CSV son genéricas.
 * Solo lee: no modifica nada.
 */
@Service
public class ReporteService {

    private static final int LIMITE_FILAS = 5000;
    private static final int DIAS_AVISO = 2;

    private final JdbcTemplate db;

    public ReporteService(JdbcTemplate db) {
        this.db = db;
    }

    private static ColumnaReporteDTO col(String clave, String etiqueta, String tipo) {
        return new ColumnaReporteDTO(clave, etiqueta, tipo);
    }

    private static ReporteDefinicionDTO def(String codigo, String grupo, String titulo, String descripcion,
                                            String... filtros) {
        return new ReporteDefinicionDTO(codigo, grupo, titulo, descripcion, List.of(filtros), false);
    }

    /** Reportes de seguridad: solo los ve el Administrador. */
    private static ReporteDefinicionDTO defAdmin(String codigo, String titulo, String descripcion, String... filtros) {
        return new ReporteDefinicionDTO(codigo, "Seguridad y auditoría", titulo, descripcion, List.of(filtros), true);
    }

    private static final List<ReporteDefinicionDTO> DEFINICIONES = List.of(
            def("expedientes-periodo", "Producción del archivo", "Expedientes por periodo",
                    "Expedientes, archivos digitales y folios registrados por día, mes o año.",
                    "desde", "hasta", "fecha", "agrupar", "areaId", "tipoId"),
            def("expedientes-tipo", "Producción del archivo", "Expedientes por tipo documental",
                    "Cuántos Oficios, Informes, Solicitudes, etc. hay registrados.",
                    "desde", "hasta", "fecha", "areaId"),
            def("expedientes-area", "Producción del archivo", "Expedientes por área de destino",
                    "A qué áreas o gerencias llega más documentación.",
                    "desde", "hasta", "fecha", "tipoId"),
            def("productividad-gestor", "Producción del archivo", "Productividad por gestor",
                    "Expedientes y folios registrados por cada persona.",
                    "desde", "hasta", "fecha"),
            def("avance-contenedores", "Estado físico y digitalización", "Avance por archivador o folder",
                    "Estado de digitalización de cada archivador o folder, con sus documentos.",
                    "desde", "hasta", "estadoContenedor", "texto"),
            def("contenedores-pendientes", "Estado físico y digitalización", "Archivadores pendientes",
                    "Los que aún no se completan, empezando por los que más tiempo llevan sin cambios.",
                    "texto"),
            def("inventario-ubicacion", "Estado físico y digitalización", "Inventario por ubicación",
                    "Qué hay en cada sección, nivel y área; permite detectar ubicaciones vacías.",
                    "soloVacias"),
            def("prestamos-vencidos", "Préstamos", "Préstamos vencidos",
                    "Lo que no volvió a tiempo: quién lo tiene y cuántos días de atraso lleva.",
                    "texto"),
            def("prestamos-vigentes", "Préstamos", "Préstamos vigentes",
                    "Lo que está prestado y aún está dentro del plazo.",
                    "texto"),
            def("prestamos-entidad", "Préstamos", "Préstamos por entidad",
                    "Qué entidades piden más y si devuelven a tiempo.",
                    "desde", "hasta", "texto"),
            def("prestamos-historial", "Préstamos", "Historial de préstamos",
                    "Todos los préstamos; busque por documento, archivador o entidad.",
                    "desde", "hasta", "texto"),
            def("prestamos-mensual", "Préstamos", "Resumen mensual de préstamos",
                    "Por mes: cuántos se prestaron, cuántos se devolvieron y cuántos siguen fuera.",
                    "desde", "hasta"),
            defAdmin("actividad-usuario", "Actividad por usuario",
                    "Qué hizo cada persona en el periodo: cuántas veces creó, modificó, consultó, descargó y eliminó.",
                    "desde", "hasta", "texto"),
            defAdmin("eliminaciones", "Eliminaciones realizadas",
                    "Quién eliminó qué y cuándo (expedientes, documentos, archivadores y valores de catálogos).",
                    "desde", "hasta", "texto"),
            defAdmin("descargas-consultas", "Descargas y consultas por usuario",
                    "Cuántos documentos consultó o descargó cada persona.",
                    "desde", "hasta", "texto"),
            defAdmin("usuarios-estado", "Usuarios activos, suspendidos y sin actividad reciente",
                    "Estado de cada cuenta y cuándo fue su última actividad en el sistema.",
                    "estadoUsuario", "dias", "texto")
    );

    public List<ReporteDefinicionDTO> definiciones(boolean esAdmin) {
        return DEFINICIONES.stream().filter(d -> esAdmin || !d.soloAdmin()).toList();
    }

    public record Filtros(LocalDate desde, LocalDate hasta, UUID areaId, UUID tipoId, String fecha,
                          String agrupar, String estadoContenedor, String texto, boolean soloVacias,
                          int dias, String estadoUsuario) {

        public static Filtros de(Map<String, String> q) {
            return new Filtros(fechaDe(q, "desde"), fechaDe(q, "hasta"), uuidDe(q, "areaId"), uuidDe(q, "tipoId"),
                    q.getOrDefault("fecha", "REGISTRO").toUpperCase(),
                    q.getOrDefault("agrupar", "MES").toUpperCase(),
                    q.getOrDefault("estadoContenedor", "TODOS").toUpperCase(),
                    q.getOrDefault("texto", "").trim(),
                    "true".equalsIgnoreCase(q.get("soloVacias")),
                    diasDe(q), q.getOrDefault("estadoUsuario", "TODOS").toUpperCase());
        }

        private static int diasDe(Map<String, String> q) {
            String v = q.get("dias");
            if (v == null || v.isBlank()) return 30;
            try {
                int dias = Integer.parseInt(v.trim());
                if (dias >= 1 && dias <= 3650) return dias;
            } catch (NumberFormatException e) {
                // cae en el mensaje de abajo
            }
            throw new SolicitudInvalidaException("Los días sin actividad deben ser un número entre 1 y 3650");
        }

        private static LocalDate fechaDe(Map<String, String> q, String clave) {
            String v = q.get(clave);
            if (v == null || v.isBlank()) return null;
            try {
                return LocalDate.parse(v.trim());
            } catch (DateTimeParseException e) {
                throw new SolicitudInvalidaException("La fecha '" + clave + "' no es válida (use AAAA-MM-DD)");
            }
        }

        private static UUID uuidDe(Map<String, String> q, String clave) {
            String v = q.get(clave);
            if (v == null || v.isBlank()) return null;
            try {
                return UUID.fromString(v.trim());
            } catch (IllegalArgumentException e) {
                throw new SolicitudInvalidaException("El filtro '" + clave + "' no es válido");
            }
        }
    }

    public ReporteResultadoDTO generar(String codigo, Filtros f, boolean esAdmin) {
        ReporteDefinicionDTO definicion = DEFINICIONES.stream().filter(d -> d.codigo().equals(codigo)).findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("Reporte no encontrado: " + codigo));
        if (definicion.soloAdmin() && !esAdmin) {
            throw new AccessDeniedException("Reporte solo para el Administrador");
        }
        String titulo = definicion.titulo();
        if (f.desde() != null && f.hasta() != null && f.desde().isAfter(f.hasta())) {
            throw new SolicitudInvalidaException("La fecha 'desde' no puede ser posterior a 'hasta'");
        }
        if (!List.of("REGISTRO", "DOCUMENTO").contains(f.fecha())) {
            throw new SolicitudInvalidaException("El campo de fecha debe ser REGISTRO o DOCUMENTO");
        }
        return switch (codigo) {
            case "expedientes-periodo" -> expedientesPeriodo(titulo, f);
            case "expedientes-tipo" -> expedientesPor(codigo, titulo, f, "tipo", "t.nombre",
                    "tipo_documental t ON t.tipo_id = b.tipo_id", "Tipo documental");
            case "expedientes-area" -> expedientesPor(codigo, titulo, f, "area", "a.nombre",
                    "area_responsable a ON a.area_id = b.area_destino_id", "Área de destino");
            case "productividad-gestor" -> productividadGestor(titulo, f);
            case "avance-contenedores" -> contenedores(codigo, titulo, f, f.estadoContenedor());
            case "contenedores-pendientes" -> contenedores(codigo, titulo, f, "PENDIENTE");
            case "inventario-ubicacion" -> inventarioUbicacion(titulo, f);
            case "prestamos-vencidos" -> prestamosAbiertos(codigo, titulo, f, true);
            case "prestamos-vigentes" -> prestamosAbiertos(codigo, titulo, f, false);
            case "prestamos-entidad" -> prestamosEntidad(titulo, f);
            case "prestamos-historial" -> prestamosHistorial(titulo, f);
            case "prestamos-mensual" -> prestamosMensual(titulo, f);
            case "actividad-usuario" -> actividadUsuario(titulo, f);
            case "eliminaciones" -> eliminaciones(titulo, f);
            case "descargas-consultas" -> descargasConsultas(titulo, f);
            case "usuarios-estado" -> usuariosEstado(titulo, f);
            default -> throw new RecursoNoEncontradoException("Reporte no encontrado: " + codigo);
        };
    }

    // ---------------------------------------------------------------- utilidades

    private ReporteResultadoDTO armar(String codigo, String titulo, List<ColumnaReporteDTO> columnas,
                                      String sql, List<Object> args) {
        var filas = new ArrayList<>(db.queryForList(sql + " LIMIT " + (LIMITE_FILAS + 1), args.toArray()));
        boolean truncado = filas.size() > LIMITE_FILAS;
        if (truncado) filas.subList(LIMITE_FILAS, filas.size()).clear();
        var totales = new LinkedHashMap<String, Object>();
        for (var c : columnas) {
            if (!"numero".equals(c.tipo())) continue;
            BigDecimal suma = BigDecimal.ZERO;
            for (var fila : filas) {
                if (fila.get(c.clave()) instanceof Number n) suma = suma.add(new BigDecimal(n.toString()));
            }
            totales.put(c.clave(), suma);
        }
        return new ReporteResultadoDTO(codigo, titulo, columnas, filas, totales, truncado);
    }

    /** Agrega "campo >= desde" y "campo < hasta + 1 día" (el día 'hasta' entra completo). */
    private static void rango(StringBuilder where, List<Object> args, String campo, Filtros f) {
        if (f.desde() != null) {
            where.append(" AND ").append(campo).append(" >= ?");
            args.add(Date.valueOf(f.desde()));
        }
        if (f.hasta() != null) {
            where.append(" AND ").append(campo).append(" < ?::date + 1");
            args.add(Date.valueOf(f.hasta()));
        }
    }

    // ---------------------------------------------------------------- producción del archivo

    /** CTE con los expedientes filtrados y su cantidad de archivos digitales. */
    private static String baseExpedientes(Filtros f, List<Object> args) {
        String campoFecha = "DOCUMENTO".equals(f.fecha()) ? "e.fecha_documento" : "e.fecha_registro";
        var where = new StringBuilder("true");
        rango(where, args, campoFecha, f);
        if (f.areaId() != null) {
            where.append(" AND e.area_destino_id = ?");
            args.add(f.areaId());
        }
        if (f.tipoId() != null) {
            where.append(" AND e.tipo_id = ?");
            args.add(f.tipoId());
        }
        return "WITH base AS (SELECT e.expediente_id, e.tipo_id, e.area_destino_id, e.creado_por_id, e.numero_folios,"
                + " " + campoFecha + " AS fecha_ref,"
                + " (SELECT count(*) FROM documento_digital d WHERE d.expediente_id = e.expediente_id) AS ndocs"
                + " FROM expediente e WHERE " + where + ") ";
    }

    private ReporteResultadoDTO expedientesPeriodo(String titulo, Filtros f) {
        String unidad, formato;
        switch (f.agrupar()) {
            case "DIA" -> { unidad = "day"; formato = "DD/MM/YYYY"; }
            case "ANIO" -> { unidad = "year"; formato = "YYYY"; }
            case "MES" -> { unidad = "month"; formato = "MM/YYYY"; }
            default -> throw new SolicitudInvalidaException("Agrupar debe ser DIA, MES o ANIO");
        }
        var args = new ArrayList<Object>();
        String sql = baseExpedientes(f, args)
                + "SELECT to_char(date_trunc('" + unidad + "', fecha_ref), '" + formato + "') AS periodo,"
                + " count(*) AS expedientes, coalesce(sum(ndocs), 0) AS archivos, coalesce(sum(numero_folios), 0) AS folios"
                + " FROM base GROUP BY date_trunc('" + unidad + "', fecha_ref)"
                + " ORDER BY date_trunc('" + unidad + "', fecha_ref)";
        return armar("expedientes-periodo", titulo, List.of(col("periodo", "Periodo", "texto"),
                col("expedientes", "Expedientes", "numero"), col("archivos", "Archivos digitales", "numero"),
                col("folios", "Folios", "numero")), sql, args);
    }

    private ReporteResultadoDTO expedientesPor(String codigo, String titulo, Filtros f, String alias, String campo,
                                               String join, String etiqueta) {
        var args = new ArrayList<Object>();
        String sql = baseExpedientes(f, args)
                + "SELECT " + campo + " AS " + alias + ", count(*) AS expedientes,"
                + " coalesce(sum(b.ndocs), 0) AS archivos, coalesce(sum(b.numero_folios), 0) AS folios,"
                + " round(100.0 * count(*) / sum(count(*)) OVER (), 1) AS porcentaje"
                + " FROM base b JOIN " + join + " GROUP BY " + campo + " ORDER BY count(*) DESC, " + campo;
        return armar(codigo, titulo, List.of(col(alias, etiqueta, "texto"), col("expedientes", "Expedientes", "numero"),
                col("archivos", "Archivos digitales", "numero"), col("folios", "Folios", "numero"),
                col("porcentaje", "% del total", "porcentaje")), sql, args);
    }

    private ReporteResultadoDTO productividadGestor(String titulo, Filtros f) {
        var args = new ArrayList<Object>();
        String sql = baseExpedientes(f, args)
                + "SELECT u.nombre AS gestor, count(*) AS expedientes, coalesce(sum(b.ndocs), 0) AS archivos,"
                + " coalesce(sum(b.numero_folios), 0) AS folios"
                + " FROM base b JOIN usuario u ON u.usuario_id = b.creado_por_id"
                + " GROUP BY u.usuario_id, u.nombre ORDER BY count(*) DESC, u.nombre";
        return armar("productividad-gestor", titulo, List.of(col("gestor", "Gestor", "texto"),
                col("expedientes", "Expedientes registrados", "numero"), col("archivos", "Archivos digitales", "numero"),
                col("folios", "Folios", "numero")), sql, args);
    }

    // ---------------------------------------------------------------- estado físico

    private static final String UBICACION =
            "'Sección ' || st.codigo || ' · Nivel ' || nv.codigo || ' · Área ' || cj.codigo";

    private ReporteResultadoDTO contenedores(String codigo, String titulo, Filtros f, String estado) {
        var args = new ArrayList<Object>();
        var where = new StringBuilder("true");
        switch (estado) {
            case "PENDIENTE", "COMPLETADO" -> {
                where.append(" AND k.estado_digitalizacion = ?");
                args.add(estado);
            }
            case "TODOS" -> { }
            default -> throw new SolicitudInvalidaException("El estado debe ser PENDIENTE, COMPLETADO o TODOS");
        }
        rango(where, args, "k.fecha_registro", f);
        if (!f.texto().isEmpty()) {
            where.append(" AND unaccent(k.descripcion) ILIKE unaccent(?)");
            args.add("%" + f.texto() + "%");
        }
        String sql = "SELECT k.descripcion AS contenedor,"
                + " CASE k.tipo_contenedor WHEN 'FOLDER' THEN 'Folder' ELSE 'Archivador' END AS tipo,"
                + " " + UBICACION + " AS ubicacion,"
                + " CASE k.estado_digitalizacion WHEN 'COMPLETADO' THEN 'Completado' ELSE 'Pendiente' END AS estado,"
                + " (SELECT count(*) FROM expediente e WHERE e.contenedor_id = k.contenedor_id) AS expedientes,"
                + " (SELECT count(*) FROM documento_digital d JOIN expediente e ON e.expediente_id = d.expediente_id"
                + "   WHERE e.contenedor_id = k.contenedor_id) AS archivos,"
                + " k.fecha_registro AS registrado, k.fecha_actualizacion AS ultimo_cambio,"
                + " CURRENT_DATE - k.fecha_actualizacion::date AS dias_sin_cambio"
                + " FROM contenedor k JOIN caja cj ON cj.caja_id = k.caja_id JOIN nivel nv ON nv.nivel_id = cj.nivel_id"
                + " JOIN estante st ON st.estante_id = nv.estante_id WHERE " + where
                + " ORDER BY (k.estado_digitalizacion = 'COMPLETADO'), k.fecha_actualizacion, k.descripcion";
        return armar(codigo, titulo, List.of(col("contenedor", "Archivador / folder", "texto"), col("tipo", "Tipo", "texto"),
                col("ubicacion", "Ubicación", "texto"), col("estado", "Estado", "texto"),
                col("expedientes", "Expedientes", "numero"), col("archivos", "Archivos digitales", "numero"),
                col("registrado", "Creado", "fechahora"), col("ultimo_cambio", "Último cambio", "fechahora"),
                col("dias_sin_cambio", "Días sin cambios", "promedio")), sql, args);
    }

    private ReporteResultadoDTO inventarioUbicacion(String titulo, Filtros f) {
        String sql = "SELECT st.codigo AS seccion, nv.codigo AS nivel, cj.codigo AS area,"
                + " count(k.contenedor_id) AS contenedores,"
                + " count(*) FILTER (WHERE k.estado_digitalizacion = 'PENDIENTE') AS pendientes,"
                + " count(*) FILTER (WHERE k.estado_digitalizacion = 'COMPLETADO') AS completados,"
                + " coalesce(sum(x.n), 0) AS expedientes"
                + " FROM caja cj JOIN nivel nv ON nv.nivel_id = cj.nivel_id JOIN estante st ON st.estante_id = nv.estante_id"
                + " LEFT JOIN contenedor k ON k.caja_id = cj.caja_id"
                + " LEFT JOIN LATERAL (SELECT count(*) AS n FROM expediente e WHERE e.contenedor_id = k.contenedor_id) x ON true"
                + " GROUP BY st.codigo, nv.codigo, cj.codigo"
                + (f.soloVacias() ? " HAVING count(k.contenedor_id) = 0" : "")
                + " ORDER BY length(st.codigo), st.codigo, length(nv.codigo), nv.codigo, length(cj.codigo), cj.codigo";
        return armar("inventario-ubicacion", titulo, List.of(col("seccion", "Sección", "texto"), col("nivel", "Nivel", "texto"),
                col("area", "Área", "texto"), col("contenedores", "Archivadores / folders", "numero"),
                col("pendientes", "Pendientes", "numero"), col("completados", "Completados", "numero"),
                col("expedientes", "Expedientes", "numero")), sql, new ArrayList<>());
    }

    // ---------------------------------------------------------------- préstamos

    private static final String PRESTAMO_FROM = " FROM prestamo p"
            + " LEFT JOIN expediente e ON e.expediente_id = p.expediente_id"
            + " LEFT JOIN contenedor k ON k.contenedor_id = p.contenedor_id"
            + " JOIN usuario u ON u.usuario_id = p.registrado_por_id";

    private static final String QUE_SE_PRESTO =
            "CASE WHEN p.contenedor_id IS NULL THEN e.codigo_unico || ' · ' || e.asunto ELSE k.descripcion END";

    private static final String TIPO_OBJETO =
            "CASE WHEN p.contenedor_id IS NULL THEN 'Documento' ELSE 'Archivador / folder completo' END";

    /** Filtro de texto común a los reportes de préstamos (entidad, documento o archivador). */
    private static void textoPrestamo(StringBuilder where, List<Object> args, Filtros f) {
        if (f.texto().isEmpty()) return;
        where.append(" AND (unaccent(p.solicitante_nombre) ILIKE unaccent(?)"
                + " OR unaccent(coalesce(e.codigo_unico,'') || ' ' || coalesce(e.asunto,'')) ILIKE unaccent(?)"
                + " OR unaccent(coalesce(k.descripcion,'')) ILIKE unaccent(?))");
        for (int i = 0; i < 3; i++) args.add("%" + f.texto() + "%");
    }

    private ReporteResultadoDTO prestamosAbiertos(String codigo, String titulo, Filtros f, boolean vencidos) {
        var args = new ArrayList<Object>();
        var where = new StringBuilder("p.estado = 'PRESTADO' AND p.fecha_devolucion_prevista "
                + (vencidos ? "< CURRENT_DATE" : ">= CURRENT_DATE"));
        textoPrestamo(where, args, f);
        String sql = "SELECT p.solicitante_nombre AS entidad, " + QUE_SE_PRESTO + " AS objeto, " + TIPO_OBJETO + " AS tipo,"
                + " p.fecha_solicitud AS salio, p.fecha_devolucion_prevista AS debe_volver,"
                + (vencidos
                ? " CURRENT_DATE - p.fecha_devolucion_prevista AS dias,"
                : " p.fecha_devolucion_prevista - CURRENT_DATE AS dias,"
                + " CASE WHEN p.fecha_devolucion_prevista - CURRENT_DATE <= " + DIAS_AVISO
                + " THEN 'Por vencer' ELSE 'En plazo' END AS situacion,")
                + " u.nombre AS registrado_por, p.observaciones"
                + PRESTAMO_FROM + " WHERE " + where
                + (vencidos ? " ORDER BY dias DESC, p.solicitante_nombre" : " ORDER BY dias, p.solicitante_nombre");
        var columnas = new ArrayList<>(List.of(col("entidad", "Entidad o persona", "texto"),
                col("objeto", "Qué se prestó", "texto"), col("tipo", "Tipo", "texto"), col("salio", "Salió", "fechahora"),
                col("debe_volver", "Debía volver", "fecha"),
                col("dias", vencidos ? "Días de atraso" : "Días restantes", "promedio")));
        if (!vencidos) columnas.add(col("situacion", "Situación", "texto"));
        columnas.add(col("registrado_por", "Registró", "texto"));
        columnas.add(col("observaciones", "Observaciones", "texto"));
        return armar(codigo, titulo, columnas, sql, args);
    }

    private ReporteResultadoDTO prestamosEntidad(String titulo, Filtros f) {
        var args = new ArrayList<Object>();
        var where = new StringBuilder("true");
        rango(where, args, "p.fecha_solicitud", f);
        textoPrestamo(where, args, f);
        String sql = "SELECT min(p.solicitante_nombre) AS entidad, count(*) AS prestamos,"
                + " count(*) FILTER (WHERE p.estado = 'DEVUELTO') AS devueltos,"
                + " count(*) FILTER (WHERE p.estado = 'PRESTADO') AS sin_devolver,"
                + " count(*) FILTER (WHERE p.estado = 'PRESTADO' AND p.fecha_devolucion_prevista < CURRENT_DATE) AS vencidos,"
                + " count(*) FILTER (WHERE p.estado = 'DEVUELTO'"
                + "   AND p.fecha_devolucion_real::date > p.fecha_devolucion_prevista) AS devueltos_tarde,"
                + " round(avg(coalesce(p.fecha_devolucion_real::date, CURRENT_DATE) - p.fecha_solicitud::date), 1) AS promedio_dias"
                + PRESTAMO_FROM + " WHERE " + where
                + " GROUP BY lower(trim(p.solicitante_nombre)) ORDER BY count(*) DESC, min(p.solicitante_nombre)";
        return armar("prestamos-entidad", titulo, List.of(col("entidad", "Entidad o persona", "texto"),
                col("prestamos", "Préstamos", "numero"), col("devueltos", "Devueltos", "numero"),
                col("sin_devolver", "Sin devolver", "numero"), col("vencidos", "Vencidos hoy", "numero"),
                col("devueltos_tarde", "Devueltos con atraso", "numero"),
                col("promedio_dias", "Días promedio", "promedio")), sql, args);
    }

    private ReporteResultadoDTO prestamosHistorial(String titulo, Filtros f) {
        var args = new ArrayList<Object>();
        var where = new StringBuilder("true");
        rango(where, args, "p.fecha_solicitud", f);
        textoPrestamo(where, args, f);
        String sql = "SELECT p.fecha_solicitud AS salio, " + QUE_SE_PRESTO + " AS objeto, " + TIPO_OBJETO + " AS tipo,"
                + " p.solicitante_nombre AS entidad, p.fecha_devolucion_prevista AS debia_volver,"
                + " p.fecha_devolucion_real AS devuelto,"
                + " CASE WHEN p.estado = 'DEVUELTO' THEN 'Devuelto'"
                + "      WHEN p.fecha_devolucion_prevista < CURRENT_DATE THEN 'Vencido'"
                + "      WHEN p.fecha_devolucion_prevista - CURRENT_DATE <= " + DIAS_AVISO + " THEN 'Por vencer'"
                + "      ELSE 'En plazo' END AS situacion,"
                + " u.nombre AS registrado_por, p.observaciones"
                + PRESTAMO_FROM + " WHERE " + where + " ORDER BY p.fecha_solicitud DESC, p.prestamo_id";
        return armar("prestamos-historial", titulo, List.of(col("salio", "Salió", "fechahora"),
                col("objeto", "Qué se prestó", "texto"), col("tipo", "Tipo", "texto"),
                col("entidad", "Entidad o persona", "texto"), col("debia_volver", "Debía volver", "fecha"),
                col("devuelto", "Devuelto el", "fechahora"), col("situacion", "Situación", "texto"),
                col("registrado_por", "Registró", "texto"), col("observaciones", "Observaciones", "texto")), sql, args);
    }

    private ReporteResultadoDTO prestamosMensual(String titulo, Filtros f) {
        var args = new ArrayList<Object>();
        var where = new StringBuilder("true");
        rango(where, args, "p.fecha_solicitud", f);
        String sql = "SELECT to_char(date_trunc('month', p.fecha_solicitud), 'MM/YYYY') AS mes, count(*) AS prestados,"
                + " count(*) FILTER (WHERE p.estado = 'DEVUELTO') AS devueltos,"
                + " count(*) FILTER (WHERE p.estado = 'DEVUELTO'"
                + "   AND p.fecha_devolucion_real::date > p.fecha_devolucion_prevista) AS devueltos_tarde,"
                + " count(*) FILTER (WHERE p.estado = 'PRESTADO') AS sin_devolver,"
                + " count(*) FILTER (WHERE p.estado = 'PRESTADO' AND p.fecha_devolucion_prevista < CURRENT_DATE) AS vencidos"
                + " FROM prestamo p WHERE " + where
                + " GROUP BY date_trunc('month', p.fecha_solicitud) ORDER BY date_trunc('month', p.fecha_solicitud)";
        return armar("prestamos-mensual", titulo, List.of(col("mes", "Mes", "texto"), col("prestados", "Prestados", "numero"),
                col("devueltos", "Devueltos", "numero"), col("devueltos_tarde", "Devueltos con atraso", "numero"),
                col("sin_devolver", "Sin devolver", "numero"), col("vencidos", "Vencidos hoy", "numero")), sql, args);
    }

    // ---------------------------------------------------------------- seguridad y auditoría (solo Administrador)

    private static void textoUsuario(StringBuilder where, List<Object> args, Filtros f) {
        if (f.texto().isEmpty()) return;
        where.append(" AND unaccent(u.nombre || ' ' || u.correo) ILIKE unaccent(?)");
        args.add("%" + f.texto() + "%");
    }

    private ReporteResultadoDTO actividadUsuario(String titulo, Filtros f) {
        var args = new ArrayList<Object>();
        var where = new StringBuilder("true");
        rango(where, args, "a.fecha", f);
        textoUsuario(where, args, f);
        String sql = "SELECT u.nombre AS usuario, u.correo AS correo, count(*) AS total,"
                + " count(*) FILTER (WHERE a.accion = 'CREAR') AS creaciones,"
                + " count(*) FILTER (WHERE a.accion = 'MODIFICAR') AS modificaciones,"
                + " count(*) FILTER (WHERE a.accion = 'CONSULTAR') AS consultas,"
                + " count(*) FILTER (WHERE a.accion = 'DESCARGAR') AS descargas,"
                + " count(*) FILTER (WHERE a.accion = 'ELIMINAR') AS eliminaciones,"
                + " max(a.fecha) AS ultima_actividad"
                + " FROM auditoria a JOIN usuario u ON u.usuario_id = a.usuario_id WHERE " + where
                + " GROUP BY u.usuario_id, u.nombre, u.correo ORDER BY count(*) DESC, u.nombre";
        return armar("actividad-usuario", titulo, List.of(col("usuario", "Usuario", "texto"), col("correo", "Correo", "texto"),
                col("total", "Acciones", "numero"), col("creaciones", "Creó", "numero"),
                col("modificaciones", "Modificó", "numero"), col("consultas", "Consultó", "numero"),
                col("descargas", "Descargó", "numero"), col("eliminaciones", "Eliminó", "numero"),
                col("ultima_actividad", "Última actividad", "fechahora")), sql, args);
    }

    private ReporteResultadoDTO eliminaciones(String titulo, Filtros f) {
        var args = new ArrayList<Object>();
        var where = new StringBuilder("a.accion = 'ELIMINAR'");
        rango(where, args, "a.fecha", f);
        if (!f.texto().isEmpty()) {
            where.append(" AND unaccent(u.nombre || ' ' || coalesce(a.detalle ->> 'nombre', '')) ILIKE unaccent(?)");
            args.add("%" + f.texto() + "%");
        }
        String sql = "SELECT a.fecha AS fecha, u.nombre AS usuario,"
                + " CASE a.entidad_afectada WHEN 'Expediente' THEN 'Expediente'"
                + "   WHEN 'DocumentoDigital' THEN 'Documento digital' WHEN 'Contenedor' THEN 'Archivador o folder'"
                + "   WHEN 'AreaResponsable' THEN 'Área de destino' WHEN 'TipoDocumental' THEN 'Tipo documental'"
                + "   WHEN 'EstadoExpediente' THEN 'Estado de expediente' WHEN 'Estante' THEN 'Sección'"
                + "   WHEN 'Nivel' THEN 'Nivel' WHEN 'Caja' THEN 'Área de ubicación' ELSE a.entidad_afectada END AS tipo,"
                + " a.detalle ->> 'nombre' AS que, a.entidad_id::text AS identificador"
                + " FROM auditoria a JOIN usuario u ON u.usuario_id = a.usuario_id WHERE " + where
                + " ORDER BY a.fecha DESC, a.auditoria_id";
        return armar("eliminaciones", titulo, List.of(col("fecha", "Fecha y hora", "fechahora"), col("usuario", "Quién", "texto"),
                col("tipo", "Tipo", "texto"), col("que", "Qué se eliminó", "texto"),
                col("identificador", "Identificador", "texto")), sql, args);
    }

    private ReporteResultadoDTO descargasConsultas(String titulo, Filtros f) {
        var args = new ArrayList<Object>();
        var where = new StringBuilder("a.accion IN ('DESCARGAR', 'CONSULTAR')");
        rango(where, args, "a.fecha", f);
        textoUsuario(where, args, f);
        String sql = "SELECT u.nombre AS usuario, u.correo AS correo,"
                + " count(*) FILTER (WHERE a.accion = 'DESCARGAR') AS descargas,"
                + " count(*) FILTER (WHERE a.accion = 'CONSULTAR') AS consultas, count(*) AS total,"
                + " count(DISTINCT a.entidad_id) FILTER (WHERE a.entidad_afectada = 'DocumentoDigital') AS documentos_distintos,"
                + " max(a.fecha) AS ultima_actividad"
                + " FROM auditoria a JOIN usuario u ON u.usuario_id = a.usuario_id WHERE " + where
                + " GROUP BY u.usuario_id, u.nombre, u.correo"
                + " ORDER BY count(*) FILTER (WHERE a.accion = 'DESCARGAR') DESC, count(*) DESC, u.nombre";
        return armar("descargas-consultas", titulo, List.of(col("usuario", "Usuario", "texto"), col("correo", "Correo", "texto"),
                col("descargas", "Descargas", "numero"), col("consultas", "Consultas", "numero"),
                col("total", "Total", "numero"), col("documentos_distintos", "Documentos distintos", "promedio"),
                col("ultima_actividad", "Última actividad", "fechahora")), sql, args);
    }

    private ReporteResultadoDTO usuariosEstado(String titulo, Filtros f) {
        // El primer "?" es el umbral de días del CASE de la situación; los demás vienen del filtro.
        var args = new ArrayList<Object>(List.of(f.dias()));
        var where = new StringBuilder("true");
        switch (f.estadoUsuario()) {
            case "TODOS" -> { }
            case "ACTIVOS" -> where.append(" AND u.activo");
            case "SUSPENDIDOS" -> where.append(" AND NOT u.activo");
            case "INACTIVOS" -> {
                where.append(" AND u.activo AND (x.ultima IS NULL OR CURRENT_DATE - x.ultima::date > ?)");
                args.add(f.dias());
            }
            default -> throw new SolicitudInvalidaException("El estado debe ser TODOS, ACTIVOS, SUSPENDIDOS o INACTIVOS");
        }
        textoUsuario(where, args, f);
        String sql = "SELECT u.nombre AS usuario, u.correo AS correo,"
                + " CASE r.nombre WHEN 'ADMIN' THEN 'Administrador' WHEN 'GESTOR_DOCUMENTAL' THEN 'Gestor de documentos'"
                + "   WHEN 'LECTOR' THEN 'Solo lectura' ELSE r.nombre END AS rol,"
                + " CASE WHEN u.activo THEN 'Activa' ELSE 'Suspendida' END AS estado,"
                + " u.fecha_creacion AS creada, x.ultima AS ultima_actividad,"
                + " CASE WHEN x.ultima IS NULL THEN NULL ELSE CURRENT_DATE - x.ultima::date END AS dias_sin_actividad,"
                + " CASE WHEN NOT u.activo THEN 'Suspendida' WHEN x.ultima IS NULL THEN 'Sin actividad registrada'"
                + "      WHEN CURRENT_DATE - x.ultima::date > ? THEN 'Sin actividad reciente'"
                + "      ELSE 'Con actividad reciente' END AS situacion"
                + " FROM usuario u JOIN rol r ON r.rol_id = u.rol_id"
                + " LEFT JOIN LATERAL (SELECT max(a.fecha) AS ultima FROM auditoria a WHERE a.usuario_id = u.usuario_id) x ON true"
                + " WHERE " + where
                + " ORDER BY (NOT u.activo), x.ultima NULLS FIRST, u.nombre";
        return armar("usuarios-estado", titulo, List.of(col("usuario", "Usuario", "texto"), col("correo", "Correo", "texto"),
                col("rol", "Rol", "texto"), col("estado", "Cuenta", "texto"), col("creada", "Creada", "fechahora"),
                col("ultima_actividad", "Última actividad", "fechahora"),
                col("dias_sin_actividad", "Días sin actividad", "promedio"), col("situacion", "Situación", "texto")),
                sql, args);
    }
}
