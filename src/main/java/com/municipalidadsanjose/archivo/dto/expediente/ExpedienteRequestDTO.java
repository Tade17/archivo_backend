package com.municipalidadsanjose.archivo.dto.expediente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record ExpedienteRequestDTO(
        @NotBlank(message = "El código único es obligatorio")
        String codigoUnico,

        @NotBlank(message = "El número de documento es obligatorio")
        String numeroDocumento,

        @NotBlank(message = "El remitente es obligatorio")
        String remitente,

        @NotNull(message = "El área destino es obligatoria")
        UUID areaDestinoId,

        @NotNull(message = "El tipo documental es obligatorio")
        UUID tipoId,

        @NotNull(message = "El estado es obligatorio")
        UUID estadoId,

        @NotNull(message = "La fecha del documento es obligatoria")
        LocalDate fechaDocumento,

        @NotBlank(message = "El asunto es obligatorio")
        String asunto,

        String glosa,

        @NotNull(message = "La caja es obligatoria")
        UUID cajaId,

        Integer numeroFolios,

        @NotNull(message = "El usuario que registra es obligatorio")
        UUID creadoPorId,

        // Opcional: si no manda tags, el expediente queda sin etiquetar.
        Set<UUID> tagIds
) {
}
