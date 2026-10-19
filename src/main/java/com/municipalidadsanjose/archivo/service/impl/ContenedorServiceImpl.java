package com.municipalidadsanjose.archivo.service.impl;

import com.municipalidadsanjose.archivo.audit.Auditable;
import com.municipalidadsanjose.archivo.dto.contenedor.ContenedorRequestDTO;
import com.municipalidadsanjose.archivo.dto.contenedor.ContenedorResponseDTO;
import com.municipalidadsanjose.archivo.entity.Caja;
import com.municipalidadsanjose.archivo.entity.Contenedor;
import com.municipalidadsanjose.archivo.enums.AccionAuditoria;
import com.municipalidadsanjose.archivo.enums.EstadoDigitalizacion;
import com.municipalidadsanjose.archivo.exception.RecursoEnUsoException;
import com.municipalidadsanjose.archivo.exception.RecursoNoEncontradoException;
import com.municipalidadsanjose.archivo.exception.SolicitudInvalidaException;
import com.municipalidadsanjose.archivo.repository.CajaRepository;
import com.municipalidadsanjose.archivo.repository.ContenedorRepository;
import com.municipalidadsanjose.archivo.repository.ExpedienteRepository;
import com.municipalidadsanjose.archivo.repository.PrestamoRepository;
import com.municipalidadsanjose.archivo.repository.UsuarioRepository;
import com.municipalidadsanjose.archivo.service.ContenedorService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ContenedorServiceImpl implements ContenedorService {

    private final ContenedorRepository contenedorRepository;
    private final CajaRepository cajaRepository;
    private final ExpedienteRepository expedienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final PrestamoRepository prestamoRepository;

    public ContenedorServiceImpl(ContenedorRepository contenedorRepository,
                                 CajaRepository cajaRepository,
                                 ExpedienteRepository expedienteRepository,
                                 UsuarioRepository usuarioRepository,
                                 PrestamoRepository prestamoRepository) {
        this.prestamoRepository = prestamoRepository;
        this.contenedorRepository = contenedorRepository;
        this.cajaRepository = cajaRepository;
        this.expedienteRepository = expedienteRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional
    @Auditable(entidad = "Contenedor", accion = AccionAuditoria.CREAR)
    public ContenedorResponseDTO crear(ContenedorRequestDTO dto, UUID creadoPorId) {
        Contenedor contenedor = new Contenedor();
        contenedor.setCaja(caja(dto.cajaId()));
        contenedor.setTipo(dto.tipo());
        contenedor.setDescripcion(dto.descripcion().trim());
        contenedor.setEstado(EstadoDigitalizacion.PENDIENTE);
        contenedor.setCreadoPor(usuarioRepository.findById(creadoPorId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", creadoPorId)));
        return toResponse(contenedorRepository.saveAndFlush(contenedor));
    }

    @Override
    @Transactional
    @Auditable(entidad = "Contenedor", accion = AccionAuditoria.MODIFICAR)
    public ContenedorResponseDTO actualizar(UUID id, ContenedorRequestDTO dto) {
        Contenedor contenedor = buscar(id);
        contenedor.setCaja(caja(dto.cajaId()));
        contenedor.setTipo(dto.tipo());
        contenedor.setDescripcion(dto.descripcion().trim());
        return toResponse(contenedorRepository.saveAndFlush(contenedor));
    }

    @Override
    @Transactional
    @Auditable(entidad = "Contenedor", accion = AccionAuditoria.MODIFICAR)
    public ContenedorResponseDTO completar(UUID id) {
        Contenedor contenedor = buscar(id);
        if (!expedienteRepository.existsByContenedorId(id)) {
            throw new SolicitudInvalidaException(
                    "Registra al menos un documento antes de marcar el contenedor como completado.");
        }
        contenedor.setEstado(EstadoDigitalizacion.COMPLETADO);
        return toResponse(contenedorRepository.saveAndFlush(contenedor));
    }

    @Override
    @Transactional
    @Auditable(entidad = "Contenedor", accion = AccionAuditoria.MODIFICAR)
    public ContenedorResponseDTO reabrir(UUID id) {
        Contenedor contenedor = buscar(id);
        contenedor.setEstado(EstadoDigitalizacion.PENDIENTE);
        return toResponse(contenedorRepository.saveAndFlush(contenedor));
    }

    @Override
    @Transactional
    @Auditable(entidad = "Contenedor", accion = AccionAuditoria.ELIMINAR)
    public void eliminar(UUID id) {
        Contenedor contenedor = buscar(id);
        if (expedienteRepository.existsByContenedorId(id)) {
            throw new RecursoEnUsoException("No se puede eliminar: el contenedor ya tiene documentos registrados.");
        }
        if (prestamoRepository.existsByContenedorId(id)) {
            throw new RecursoEnUsoException("No se puede eliminar: el contenedor tiene préstamos registrados.");
        }
        contenedorRepository.delete(contenedor);
    }

    private Contenedor buscar(UUID id) {
        return contenedorRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Contenedor", id));
    }

    private Caja caja(UUID cajaId) {
        return cajaRepository.findById(cajaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Área", cajaId));
    }

    private ContenedorResponseDTO toResponse(Contenedor c) {
        return new ContenedorResponseDTO(c.getId(), c.getCaja().getId(), c.getTipo(), c.getDescripcion(), c.getEstado());
    }
}
