package com.municipalidadsanjose.archivo.dto.contenedor;

import com.municipalidadsanjose.archivo.enums.EstadoDigitalizacion;
import com.municipalidadsanjose.archivo.enums.TipoContenedor;

import java.util.UUID;

public record ContenedorResponseDTO(
        UUID id,
        UUID cajaId,
        TipoContenedor tipo,
        String descripcion,
        EstadoDigitalizacion estado
) {
}
