package com.municipalidadsanjose.archivo.mapper;

import com.municipalidadsanjose.archivo.dto.rol.RolRequestDTO;
import com.municipalidadsanjose.archivo.dto.rol.RolResponseDTO;
import com.municipalidadsanjose.archivo.entity.Rol;
import org.springframework.stereotype.Component;

@Component
public class RolMapper {
    public Rol toEntity(RolRequestDTO dto){
        Rol rol = new Rol();
        rol.setNombre(dto.nombre());
        rol.setDescripcion(dto.descripcion());
        return  rol;
    }

    public RolResponseDTO toResponseDTO(Rol rol){
        return new RolResponseDTO(
                rol.getId(),
                rol.getNombre(),
                rol.getDescripcion()
        );

    }
}
