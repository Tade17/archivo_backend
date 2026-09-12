package com.municipalidadsanjose.archivo.service.impl;

import com.municipalidadsanjose.archivo.audit.Auditable;
import com.municipalidadsanjose.archivo.dto.usuario.UsuarioRequestDTO;
import com.municipalidadsanjose.archivo.dto.usuario.UsuarioResponseDTO;
import com.municipalidadsanjose.archivo.entity.Rol;
import com.municipalidadsanjose.archivo.entity.Usuario;
import com.municipalidadsanjose.archivo.enums.AccionAuditoria;
import com.municipalidadsanjose.archivo.exception.RecursoDuplicadoException;
import com.municipalidadsanjose.archivo.exception.RecursoNoEncontradoException;
import com.municipalidadsanjose.archivo.exception.SolicitudInvalidaException;
import com.municipalidadsanjose.archivo.mapper.UsuarioMapper;
import com.municipalidadsanjose.archivo.repository.RolRepository;
import com.municipalidadsanjose.archivo.repository.UsuarioRepository;
import com.municipalidadsanjose.archivo.service.UsuarioService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final UsuarioMapper usuarioMapper;
    private final PasswordEncoder passwordEncoder;

    public UsuarioServiceImpl(UsuarioRepository usuarioRepository,
                               RolRepository rolRepository,
                               UsuarioMapper usuarioMapper,
                               PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.usuarioMapper = usuarioMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    @Auditable(entidad = "Usuario", accion = AccionAuditoria.CREAR)
    public UsuarioResponseDTO crear(UsuarioRequestDTO dto) {
        if (usuarioRepository.existsByCorreo(dto.correo())) {
            throw new RecursoDuplicadoException("Ya existe un usuario registrado con el correo: " + dto.correo());
        }
        if (dto.password() == null || dto.password().isBlank()) {
            throw new SolicitudInvalidaException("La contraseña es obligatoria al crear un usuario");
        }

        Rol rol = rolRepository.findById(dto.rolId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Rol", dto.rolId()));

        String passwordHash = passwordEncoder.encode(dto.password());
        Usuario usuario = usuarioMapper.toEntity(dto, rol, passwordHash);

        // flush explícito: sin esto, fechaCreacion/fechaActualizacion (generadas por
        // Hibernate con @CreationTimestamp/@UpdateTimestamp) quedan null en la respuesta,
        // porque el INSERT recién se ejecuta al hacer commit, no al llamar a save().
        Usuario guardado = usuarioRepository.saveAndFlush(usuario);
        return usuarioMapper.toResponseDTO(guardado);
    }

    @Override
    @Transactional
    @Auditable(entidad = "Usuario", accion = AccionAuditoria.MODIFICAR)
    public UsuarioResponseDTO actualizar(UUID id, UsuarioRequestDTO dto) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", id));

        boolean cambiaCorreo = !usuario.getCorreo().equals(dto.correo());
        if (cambiaCorreo && usuarioRepository.existsByCorreo(dto.correo())) {
            throw new RecursoDuplicadoException("Ya existe un usuario registrado con el correo: " + dto.correo());
        }

        Rol rol = rolRepository.findById(dto.rolId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Rol", dto.rolId()));

        usuario.setNombre(dto.nombre());
        usuario.setCorreo(dto.correo());
        usuario.setRol(rol);
        if (dto.activo() != null) {
            usuario.setActivo(dto.activo());
        }
        if (dto.password() != null && !dto.password().isBlank()) {
            usuario.setPasswordHash(passwordEncoder.encode(dto.password()));
        }

        // No hace falta usuarioRepository.save(usuario): al estar "usuario" gestionado
        // dentro de esta transacción, Hibernate detecta los cambios (dirty checking)
        // y los persiste solo al hacer commit, sin que lo pidamos explícitamente.
        // Sí hace falta el flush: sin él, fechaActualizacion en la respuesta muestra
        // el valor viejo en vez del que Hibernate acaba de generar.
        usuarioRepository.flush();
        return usuarioMapper.toResponseDTO(usuario);
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioResponseDTO buscarPorId(UUID id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", id));
        return usuarioMapper.toResponseDTO(usuario);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UsuarioResponseDTO> listarTodos(Pageable pageable) {
        return usuarioRepository.findAll(pageable).map(usuarioMapper::toResponseDTO);
    }

    @Override
    @Transactional
    @Auditable(entidad = "Usuario", accion = AccionAuditoria.MODIFICAR)
    public void desactivar(UUID id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", id));
        usuario.setActivo(false);
    }
}
