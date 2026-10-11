package com.municipalidadsanjose.archivo.mapper;

import com.municipalidadsanjose.archivo.dto.expediente.ExpedienteRequestDTO;
import com.municipalidadsanjose.archivo.dto.expediente.ExpedienteResponseDTO;
import com.municipalidadsanjose.archivo.entity.*;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ExpedienteMapper {

    public Expediente toEntity(ExpedienteRequestDTO dto,
                                AreaResponsable areaDestino,
                                TipoDocumental tipo,
                                EstadoExpediente estado,
                                Contenedor contenedor,
                                Usuario creadoPor) {
        Expediente expediente = new Expediente();
        expediente.setCodigoUnico(dto.codigoUnico());
        expediente.setNumeroDocumento(dto.numeroDocumento());
        expediente.setRemitente(dto.remitente());
        expediente.setAreaDestino(areaDestino);
        expediente.setTipo(tipo);
        expediente.setEstado(estado);
        expediente.setFechaDocumento(dto.fechaDocumento());
        expediente.setAsunto(dto.asunto());
        expediente.setGlosa(dto.glosa());
        expediente.setDestinatario(dto.destinatario());
        expediente.setContenedor(contenedor);
        expediente.setNumeroFolios(dto.numeroFolios());
        expediente.setCreadoPor(creadoPor);
        return expediente;
    }

    public ExpedienteResponseDTO toResponseDTO(Expediente expediente) {
        Set<String> tagNombres = expediente.getTags().stream()
                .map(Tag::getNombre)
                .collect(Collectors.toSet());

        return new ExpedienteResponseDTO(
                expediente.getId(),
                expediente.getCodigoUnico(),
                expediente.getNumeroDocumento(),
                expediente.getRemitente(),
                expediente.getAreaDestino().getNombre(),
                expediente.getTipo().getNombre(),
                expediente.getEstado().getNombre(),
                expediente.getFechaDocumento(),
                expediente.getAsunto(),
                expediente.getGlosa(),
                expediente.getDestinatario(),
                expediente.getContenedor().getId(),
                expediente.getContenedor().getDescripcion(),
                expediente.getNumeroFolios(),
                expediente.getCreadoPor().getNombre(),
                expediente.getFechaRegistro(),
                expediente.getFechaActualizacion(),
                tagNombres
        );
    }
}
