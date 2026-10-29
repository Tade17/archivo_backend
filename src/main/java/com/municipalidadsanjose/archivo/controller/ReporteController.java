package com.municipalidadsanjose.archivo.controller;

import com.municipalidadsanjose.archivo.dto.reporte.ReporteDefinicionDTO;
import com.municipalidadsanjose.archivo.dto.reporte.ReporteResultadoDTO;
import com.municipalidadsanjose.archivo.service.ReporteService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workspace/reportes")
@PreAuthorize("hasAnyRole('ADMIN','GESTOR_DOCUMENTAL')")
public class ReporteController {

    private final ReporteService reportes;

    public ReporteController(ReporteService reportes) {
        this.reportes = reportes;
    }

    // Los reportes de seguridad y auditoría solo los ve el Administrador.
    private static boolean esAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    @GetMapping
    public List<ReporteDefinicionDTO> listar(Authentication auth) {
        return reportes.definiciones(esAdmin(auth));
    }

    @GetMapping("/{codigo}")
    public ReporteResultadoDTO generar(@PathVariable String codigo, @RequestParam Map<String, String> q,
                                       Authentication auth) {
        return reportes.generar(codigo, ReporteService.Filtros.de(q), esAdmin(auth));
    }
}
