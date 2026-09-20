package com.municipalidadsanjose.archivo.service.impl;

import com.municipalidadsanjose.archivo.audit.Auditable;
import com.municipalidadsanjose.archivo.dto.prestamo.PrestamoRequestDTO;
import com.municipalidadsanjose.archivo.dto.prestamo.PrestamoResponseDTO;
import com.municipalidadsanjose.archivo.entity.Expediente;
import com.municipalidadsanjose.archivo.entity.Prestamo;
import com.municipalidadsanjose.archivo.entity.Usuario;
import com.municipalidadsanjose.archivo.enums.AccionAuditoria;
import com.municipalidadsanjose.archivo.enums.EstadoPrestamo;
import com.municipalidadsanjose.archivo.enums.TipoSolicitud;
import com.municipalidadsanjose.archivo.exception.RecursoNoEncontradoException;
import com.municipalidadsanjose.archivo.exception.SolicitudInvalidaException;
import com.municipalidadsanjose.archivo.mapper.PrestamoMapper;
import com.municipalidadsanjose.archivo.repository.ExpedienteRepository;
import com.municipalidadsanjose.archivo.repository.PrestamoRepository;
import com.municipalidadsanjose.archivo.repository.UsuarioRepository;
import com.municipalidadsanjose.archivo.service.PrestamoService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PrestamoServiceImpl implements PrestamoService {

    private final PrestamoRepository prestamoRepository;
    private final ExpedienteRepository expedienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final PrestamoMapper prestamoMapper;

    public PrestamoServiceImpl(PrestamoRepository prestamoRepository,
                                ExpedienteRepository expedienteRepository,
                                UsuarioRepository usuarioRepository,
                                PrestamoMapper prestamoMapper) {
        this.prestamoRepository = prestamoRepository;
        this.expedienteRepository = expedienteRepository;
        this.usuarioRepository = usuarioRepository;
        this.prestamoMapper = prestamoMapper;
    }

    @Override
    @Transactional
    @Auditable(entidad = "Prestamo", accion = AccionAuditoria.CREAR)
    public PrestamoResponseDTO crear(PrestamoRequestDTO dto) {
        if (dto.tipoSolicitud() != TipoSolicitud.FISICO) {
            throw new SolicitudInvalidaException("El sistema controla únicamente préstamos físicos. La consulta digital se registra mediante auditoría.");
        }
        Expediente expediente = expedienteRepository.findById(dto.expedienteId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Expediente", dto.expedienteId()));
        Usuario solicitante = usuarioRepository.findById(dto.solicitanteId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", dto.solicitanteId()));

        // flush explícito: sin esto, fechaSolicitud/fechaActualizacion (generadas por
        // Hibernate con @CreationTimestamp/@UpdateTimestamp) quedan null en la respuesta,
        // porque el INSERT recién se ejecuta al hacer commit, no al llamar a save().
        Prestamo guardado = prestamoRepository.saveAndFlush(prestamoMapper.toEntity(dto, expediente, solicitante));
        return prestamoMapper.toResponseDTO(guardado);
    }

    @Override
    @Transactional
    @Auditable(entidad = "Prestamo", accion = AccionAuditoria.MODIFICAR)
    public PrestamoResponseDTO actualizar(UUID id, PrestamoRequestDTO dto) {
        if (dto.tipoSolicitud() != TipoSolicitud.FISICO) {
            throw new SolicitudInvalidaException("Un préstamo no puede cambiarse a modalidad digital.");
        }
        Prestamo prestamo = prestamoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Prestamo", id));

        // expediente y solicitante no se reasignan en un update: identifican
        // "quién pidió qué"; si eso cambió, es un préstamo distinto, no una edición.
        prestamo.setTipoSolicitud(dto.tipoSolicitud());
        prestamo.setFechaDevolucionPrevista(dto.fechaDevolucionPrevista());

        // idem que en crear(): sin flush, fechaActualizacion en la respuesta
        // muestra el valor viejo en vez del que Hibernate acaba de generar.
        prestamoRepository.flush();
        return prestamoMapper.toResponseDTO(prestamo);
    }

    @Override
    @Transactional(readOnly = true)
    public PrestamoResponseDTO buscarPorId(UUID id) {
        return prestamoMapper.toResponseDTO(
                prestamoRepository.findById(id)
                        .orElseThrow(() -> new RecursoNoEncontradoException("Prestamo", id)));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PrestamoResponseDTO> listarTodos(Pageable pageable) {
        return prestamoRepository.findAll(pageable).map(prestamoMapper::toResponseDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PrestamoResponseDTO> listarPorExpediente(UUID expedienteId, Pageable pageable) {
        return prestamoRepository.findByExpedienteId(expedienteId, pageable).map(prestamoMapper::toResponseDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PrestamoResponseDTO> listarPorSolicitante(UUID solicitanteId, Pageable pageable) {
        return prestamoRepository.findBySolicitanteId(solicitanteId, pageable).map(prestamoMapper::toResponseDTO);
    }

    @Override
    @Transactional
    @Auditable(entidad = "Prestamo", accion = AccionAuditoria.MODIFICAR)
    public PrestamoResponseDTO devolver(UUID id) {
        Prestamo prestamo = prestamoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Prestamo", id));

        if (prestamo.getEstado() == EstadoPrestamo.DEVUELTO) {
            throw new SolicitudInvalidaException("Este préstamo ya fue devuelto");
        }

        prestamo.setEstado(EstadoPrestamo.DEVUELTO);
        prestamo.setFechaDevolucionReal(LocalDateTime.now());

        // idem que en crear()/actualizar(): sin flush, fechaActualizacion en la
        // respuesta muestra el valor viejo en vez del que Hibernate acaba de generar.
        prestamoRepository.flush();
        return prestamoMapper.toResponseDTO(prestamo);
    }

    @Override
    @Transactional
    @Auditable(entidad = "Prestamo", accion = AccionAuditoria.ELIMINAR)
    public void eliminar(UUID id) {
        if (!prestamoRepository.existsById(id)) {
            throw new RecursoNoEncontradoException("Prestamo", id);
        }
        prestamoRepository.deleteById(id);
    }
}
