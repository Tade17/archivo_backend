package com.municipalidadsanjose.archivo.dto.reporte;

import java.util.List;
import java.util.Map;

public record ReporteResultadoDTO(
        String codigo,
        String titulo,
        List<ColumnaReporteDTO> columnas,
        List<Map<String, Object>> filas,
        Map<String, Object> totales,
        boolean truncado
) {
}
