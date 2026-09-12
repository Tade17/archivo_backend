package com.municipalidadsanjose.archivo.repository;

import com.municipalidadsanjose.archivo.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    Optional<Usuario> findByCorreo(String correo);
    boolean existsByCorreo(String correo);
    boolean existsByRolId(UUID rolId);

    // join fetch: sin esto, usuario.getRol() revienta con LazyInitializationException
    // al leerse fuera de una transacción (ej. en UsuarioDetailsService, con open-in-view=false).
    @Query("select u from Usuario u join fetch u.rol where u.correo = :correo")
    Optional<Usuario> findByCorreoConRol(String correo);
}
