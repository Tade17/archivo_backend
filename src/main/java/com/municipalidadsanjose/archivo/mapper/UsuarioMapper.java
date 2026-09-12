package com.municipalidadsanjose.archivo.mapper;

import com.municipalidadsanjose.archivo.dto.usuario.UsuarioRequestDTO;
import com.municipalidadsanjose.archivo.dto.usuario.UsuarioResponseDTO;
import com.municipalidadsanjose.archivo.entity.Rol;
import com.municipalidadsanjose.archivo.entity.Usuario;
import org.springframework.stereotype.Component;

@Component
public class UsuarioMapper {

    // El mapper nunca hashea la contraseña: eso es responsabilidad del Service
    // (que tiene el PasswordEncoder). Aquí solo recibimos el hash ya calculado.
    public Usuario toEntity(UsuarioRequestDTO dto, Rol rol, String passwordHash) {
        Usuario usuario = new Usuario();
        usuario.setNombre(dto.nombre());
        usuario.setCorreo(dto.correo());
        usuario.setPasswordHash(passwordHash);
        usuario.setRol(rol);
        usuario.setActivo(dto.activo() != null ? dto.activo() : true);
        return usuario;
    }

    public UsuarioResponseDTO toResponseDTO(Usuario usuario) {
        return new UsuarioResponseDTO(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getCorreo(),
                usuario.getRol().getNombre(),
                usuario.isActivo(),
                usuario.getFechaCreacion(),
                usuario.getFechaActualizacion()
        );
    }
}
