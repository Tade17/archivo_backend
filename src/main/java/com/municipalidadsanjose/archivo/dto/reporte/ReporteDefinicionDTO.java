package com.municipalidadsanjose.archivo.dto.reporte;

import java.util.List;

// Un reporte disponible y los filtros que acepta (la pantalla arma el formulario con esto).
public record ReporteDefinicionDTO(
        String codigo,
        String grupo,
        String titulo,
        String descripcion,
        List<String> filtros,
        boolean soloAdmin
) {
}
