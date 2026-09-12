package com.municipalidadsanjose.archivo.security;

import com.municipalidadsanjose.archivo.entity.Usuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

// Adapta Usuario al contrato que Spring Security necesita para autenticar y
// autorizar, sin ensuciar la entidad de dominio con métodos de UserDetails.
public class UsuarioPrincipal implements UserDetails {

    private final Usuario usuario;

    public UsuarioPrincipal(Usuario usuario) {
        this.usuario = usuario;
    }

    public UUID getId() {
        return usuario.getId();
    }

    public Usuario getUsuario() {
        return usuario;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Convención: el nombre del Rol en base de datos se expone como autoridad
        // "ROLE_<NOMBRE_EN_MAYUSCULAS>" (espacios -> "_") para usar hasRole(...) en @PreAuthorize.
        String autoridad = "ROLE_" + usuario.getRol().getNombre()
                .trim()
                .toUpperCase()
                .replace(' ', '_');
        return List.of(new SimpleGrantedAuthority(autoridad));
    }

    @Override
    public String getPassword() {
        return usuario.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return usuario.getCorreo();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return usuario.isActivo();
    }
}
