package com.municipalidadsanjose.archivo.audit;

import com.municipalidadsanjose.archivo.entity.Auditoria;
import com.municipalidadsanjose.archivo.enums.AccionAuditoria;
import com.municipalidadsanjose.archivo.repository.AuditoriaRepository;
import com.municipalidadsanjose.archivo.repository.UsuarioRepository;
import com.municipalidadsanjose.archivo.security.UsuarioPrincipal;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Aspect
@Component
public class AuditoriaAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaAspect.class);

    private final AuditoriaRepository auditoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final JdbcTemplate db;

    public AuditoriaAspect(AuditoriaRepository auditoriaRepository, UsuarioRepository usuarioRepository,
                           JdbcTemplate db) {
        this.auditoriaRepository = auditoriaRepository;
        this.usuarioRepository = usuarioRepository;
        this.db = db;
    }

    // Around (y no AfterReturning) para poder leer, antes de borrar, el nombre de lo que se elimina.
    @Around("@annotation(auditable)")
    public Object registrar(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Map<String, Object> detalle = auditable.accion() == AccionAuditoria.ELIMINAR
                ? detalleDeEliminacion(joinPoint, auditable.entidad())
                : null;
        Object resultado = joinPoint.proceed();
        guardar(joinPoint, auditable, resultado, detalle);
        return resultado;
    }

    private void guardar(JoinPoint joinPoint, Auditable auditable, Object resultado, Map<String, Object> detalle) {
        UUID usuarioId = usuarioAutenticadoId();
        if (usuarioId == null) {
            log.warn("No se pudo auditar {} sobre {}: no hay usuario autenticado en el contexto",
                    auditable.accion(), auditable.entidad());
            return;
        }

        // Métodos void: el id afectado es el primer argumento.
        UUID entidadId = resultado == null
                ? primerArgumentoUUID(joinPoint)
                : idDelResultado(resultado);
        if (entidadId == null) {
            log.warn("No se pudo determinar el id afectado para auditar {} sobre {}",
                    auditable.accion(), auditable.entidad());
            return;
        }

        Auditoria auditoria = new Auditoria();
        auditoria.setUsuario(usuarioRepository.getReferenceById(usuarioId));
        auditoria.setEntidadAfectada(auditable.entidad());
        auditoria.setEntidadId(entidadId);
        auditoria.setAccion(auditable.accion());
        auditoria.setDetalle(detalle);
        auditoriaRepository.save(auditoria);
    }

    /** Nombre legible de lo que se va a eliminar (el registro ya no existirá cuando se consulte la auditoría). */
    private Map<String, Object> detalleDeEliminacion(JoinPoint joinPoint, String entidad) {
        UUID id = primerArgumentoUUID(joinPoint);
        if (id == null) return null;
        String sql = switch (entidad) {
            case "Expediente" -> "SELECT codigo_unico || ' · ' || asunto FROM expediente WHERE expediente_id = ?";
            case "DocumentoDigital" -> "SELECT d.nombre_archivo || ' (' || e.codigo_unico || ')' FROM documento_digital d"
                    + " JOIN expediente e ON e.expediente_id = d.expediente_id WHERE d.documento_id = ?";
            case "Contenedor" -> "SELECT descripcion FROM contenedor WHERE contenedor_id = ?";
            case "AreaResponsable" -> "SELECT nombre FROM area_responsable WHERE area_id = ?";
            case "TipoDocumental" -> "SELECT nombre FROM tipo_documental WHERE tipo_id = ?";
            case "EstadoExpediente" -> "SELECT nombre FROM estado_expediente WHERE estado_id = ?";
            case "Estante" -> "SELECT 'Sección ' || codigo FROM estante WHERE estante_id = ?";
            case "Nivel" -> "SELECT 'Nivel ' || codigo FROM nivel WHERE nivel_id = ?";
            case "Caja" -> "SELECT 'Área ' || codigo FROM caja WHERE caja_id = ?";
            case "Rol" -> "SELECT nombre FROM rol WHERE rol_id = ?";
            default -> null;
        };
        if (sql == null) return null;
        try {
            List<String> nombres = db.queryForList(sql, String.class, id);
            return nombres.isEmpty() || nombres.get(0) == null ? null : Map.of("nombre", nombres.get(0));
        } catch (DataAccessException e) {
            // La auditoría no debe impedir la eliminación por no poder leer el nombre.
            log.warn("No se pudo leer el nombre de {} {} para la auditoría: {}", entidad, id, e.getMessage());
            return null;
        }
    }

    private UUID usuarioAutenticadoId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UsuarioPrincipal principal)) {
            return null;
        }
        return principal.getId();
    }

    private UUID primerArgumentoUUID(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        return (args.length > 0 && args[0] instanceof UUID id) ? id : null;
    }

    private UUID idDelResultado(Object resultado) {
        if (resultado == null) {
            return null;
        }
        try {
            Method idAccessor = resultado.getClass().getMethod("id");
            Object valor = idAccessor.invoke(resultado);
            return valor instanceof UUID id ? id : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
