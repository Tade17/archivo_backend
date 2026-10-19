package com.municipalidadsanjose.archivo.dto.common;

import org.springframework.data.domain.Page;

import java.util.List;

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
