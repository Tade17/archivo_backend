package com.municipalidadsanjose.archivo.mapper;

import com.municipalidadsanjose.archivo.dto.caja.CajaRequestDTO;
import com.municipalidadsanjose.archivo.dto.caja.CajaResponseDTO;
import com.municipalidadsanjose.archivo.entity.Caja;
import com.municipalidadsanjose.archivo.entity.Nivel;
import org.springframework.stereotype.Component;

@Component
public class CajaMapper {

    public Caja toEntity(CajaRequestDTO dto, Nivel nivel) {
        Caja caja = new Caja();
        caja.setNivel(nivel);
        caja.setCodigo(dto.codigo());
        return caja;
    }

    public CajaResponseDTO toResponseDTO(Caja caja) {
        return new CajaResponseDTO(
                caja.getId(),
                caja.getCodigo(),
                caja.getNivel().getId(),
                caja.getNivel().getCodigo()
        );
    }
}
