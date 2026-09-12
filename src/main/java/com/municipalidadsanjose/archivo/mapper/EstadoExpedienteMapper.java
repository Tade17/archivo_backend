package com.municipalidadsanjose.archivo.mapper;

import com.municipalidadsanjose.archivo.dto.estadoexpediente.EstadoExpedienteRequestDTO;
import com.municipalidadsanjose.archivo.dto.estadoexpediente.EstadoExpedienteResponseDTO;
import com.municipalidadsanjose.archivo.entity.EstadoExpediente;
import org.springframework.stereotype.Component;

@Component
public class EstadoExpedienteMapper {

    public EstadoExpediente toEntity(EstadoExpedienteRequestDTO dto) {
        EstadoExpediente estado = new EstadoExpediente();
        estado.setNombre(dto.nombre());
        return estado;
    }

    public EstadoExpedienteResponseDTO toResponseDTO(EstadoExpediente estado) {
        return new EstadoExpedienteResponseDTO(estado.getId(), estado.getNombre());
    }
}
