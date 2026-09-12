package com.municipalidadsanjose.archivo.security;

import com.municipalidadsanjose.archivo.entity.Usuario;
import com.municipalidadsanjose.archivo.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String correo) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByCorreoConRol(correo)
                .orElseThrow(() -> new UsernameNotFoundException("No existe un usuario con el correo: " + correo));
        return new UsuarioPrincipal(usuario);
    }
}
