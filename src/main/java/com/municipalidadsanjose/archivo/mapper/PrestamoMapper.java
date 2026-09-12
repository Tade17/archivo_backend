package com.municipalidadsanjose.archivo.mapper;

import com.municipalidadsanjose.archivo.dto.prestamo.PrestamoRequestDTO;
import com.municipalidadsanjose.archivo.dto.prestamo.PrestamoResponseDTO;
import com.municipalidadsanjose.archivo.entity.Expediente;
import com.municipalidadsanjose.archivo.entity.Prestamo;
import com.municipalidadsanjose.archivo.entity.Usuario;
import org.springframework.stereotype.Component;

@Component
public class PrestamoMapper {

    public Prestamo toEntity(PrestamoRequestDTO dto, Expediente expediente, Usuario solicitante) {
        Prestamo prestamo = new Prestamo();
        prestamo.setExpediente(expediente);
        prestamo.setSolicitante(solicitante);
        prestamo.setTipoSolicitud(dto.tipoSolicitud());
        prestamo.setFechaDevolucionPrevista(dto.fechaDevolucionPrevista());
        return prestamo;
    }

    public PrestamoResponseDTO toResponseDTO(Prestamo prestamo) {
        return new PrestamoResponseDTO(
                prestamo.getId(),
                prestamo.getExpediente().getId(),
                prestamo.getExpediente().getCodigoUnico(),
                prestamo.getSolicitante().getNombre(),
                prestamo.getTipoSolicitud(),
                prestamo.getFechaSolicitud(),
                prestamo.getFechaDevolucionPrevista(),
                prestamo.getFechaDevolucionReal(),
                prestamo.getEstado(),
                prestamo.getFechaActualizacion()
        );
    }
}
