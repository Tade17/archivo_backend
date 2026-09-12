package com.municipalidadsanjose.archivo.dto.common;

import org.springframework.data.domain.Page;

import java.util.List;

// Envoltorio propio en vez de devolver Page<T> directo: evita depender de cómo
// Spring Data serializa Page (formato distinto según versión de Jackson) y deja
// un contrato JSON estable y explícito para el cliente.
public record PaginaResponseDTO<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas
) {
    public static <T> PaginaResponseDTO<T> de(Page<T> page) {
        return new PaginaResponseDTO<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
