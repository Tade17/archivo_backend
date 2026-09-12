package com.municipalidadsanjose.archivo.audit;

import com.municipalidadsanjose.archivo.enums.AccionAuditoria;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Marca un método de *ServiceImpl para que AuditoriaAspect registre la operación
// en la tabla auditoria una vez que el método termina exitosamente.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Auditable {
    // Nombre de la entidad afectada, tal como se usa en RecursoNoEncontradoException
    // (ej. "Expediente", "Usuario", "Rol") para poder filtrar por entidadAfectada+entidadId.
    String entidad();
    AccionAuditoria accion();
}
