package com.municipalidadsanjose.archivo.audit;

import com.municipalidadsanjose.archivo.enums.AccionAuditoria;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
// para la auditoria de los metodos de los controladores y los reportes posteriores
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Auditable {
    String entidad();
    AccionAuditoria accion();
}
