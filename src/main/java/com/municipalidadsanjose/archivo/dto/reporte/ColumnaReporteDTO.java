package com.municipalidadsanjose.archivo.dto.reporte;

// tipo: texto | numero (se suma en los totales) | promedio | porcentaje | fecha | fechahora
public record ColumnaReporteDTO(
        String clave,
        String etiqueta,
        String tipo
) {
}
