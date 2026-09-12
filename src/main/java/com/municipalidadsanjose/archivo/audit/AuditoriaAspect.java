package com.municipalidadsanjose.archivo.audit;

import com.municipalidadsanjose.archivo.entity.Auditoria;
import com.municipalidadsanjose.archivo.repository.AuditoriaRepository;
import com.municipalidadsanjose.archivo.repository.UsuarioRepository;
import com.municipalidadsanjose.archivo.security.UsuarioPrincipal;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.UUID;

// Productor real de la tabla auditoria: intercepta cada método @Auditable de los
// *ServiceImpl (crear/actualizar/eliminar/etc.) y, si termina sin excepción,
// graba quién hizo qué sobre qué entidad. Antes de esto la tabla nunca se llenaba.
@Aspect
@Component
public class AuditoriaAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaAspect.class);

    private final AuditoriaRepository auditoriaRepository;
    private final UsuarioRepository usuarioRepository;

    public AuditoriaAspect(AuditoriaRepository auditoriaRepository, UsuarioRepository usuarioRepository) {
        this.auditoriaRepository = auditoriaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @AfterReturning(pointcut = "@annotation(auditable)", returning = "resultado")
    public void registrar(JoinPoint joinPoint, Auditable auditable, Object resultado) {
        UUID usuarioId = usuarioAutenticadoId();
        if (usuarioId == null) {
            // No debería pasar: todo endpoint de escritura exige autenticación.
            // Si pasa (ej. un proceso interno sin contexto de seguridad), no se
            // audita en vez de reventar la operación de negocio por esto.
            log.warn("No se pudo auditar {} sobre {}: no hay usuario autenticado en el contexto",
                    auditable.accion(), auditable.entidad());
            return;
        }

        // Métodos void (eliminar/desactivar) no tienen resultado: el id afectado
        // es el primer argumento. Los que sí devuelven un *ResponseDTO usan su id().
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
        auditoriaRepository.save(auditoria);
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

    // Todos los *ResponseDTO son records con un accessor id() como primer componente.
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
