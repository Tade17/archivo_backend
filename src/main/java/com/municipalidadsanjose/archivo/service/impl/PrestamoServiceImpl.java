package com.municipalidadsanjose.archivo.service.impl;

import com.municipalidadsanjose.archivo.audit.Auditable;
import com.municipalidadsanjose.archivo.dto.prestamo.PrestamoRequestDTO;
import com.municipalidadsanjose.archivo.dto.prestamo.PrestamoResponseDTO;
import com.municipalidadsanjose.archivo.entity.Contenedor;
import com.municipalidadsanjose.archivo.entity.Expediente;
import com.municipalidadsanjose.archivo.entity.Prestamo;
import com.municipalidadsanjose.archivo.enums.AccionAuditoria;
import com.municipalidadsanjose.archivo.enums.EstadoPrestamo;
import com.municipalidadsanjose.archivo.exception.RecursoNoEncontradoException;
import com.municipalidadsanjose.archivo.exception.SolicitudInvalidaException;
import com.municipalidadsanjose.archivo.repository.ContenedorRepository;
import com.municipalidadsanjose.archivo.repository.ExpedienteRepository;
import com.municipalidadsanjose.archivo.repository.PrestamoRepository;
import com.municipalidadsanjose.archivo.repository.UsuarioRepository;
import com.municipalidadsanjose.archivo.service.PrestamoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PrestamoServiceImpl implements PrestamoService {

    static final int DIAS_POR_DEFECTO = 7;

    private final PrestamoRepository prestamoRepository;
    private final ExpedienteRepository expedienteRepository;
    private final ContenedorRepository contenedorRepository;
    private final UsuarioRepository usuarioRepository;

    public PrestamoServiceImpl(PrestamoRepository prestamoRepository,
                               ExpedienteRepository expedienteRepository,
                               ContenedorRepository contenedorRepository,
                               UsuarioRepository usuarioRepository) {
        this.prestamoRepository = prestamoRepository;
        this.expedienteRepository = expedienteRepository;
        this.contenedorRepository = contenedorRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional
    @Auditable(entidad = "Prestamo", accion = AccionAuditoria.CREAR)
    public PrestamoResponseDTO crear(PrestamoRequestDTO dto, UUID registradoPorId) {
        boolean conExpediente = dto.expedienteId() != null;
        boolean conContenedor = dto.contenedorId() != null;
        if (conExpediente == conContenedor) {
            throw new SolicitudInvalidaException("Elige el documento o el archivador que se presta.");
        }

        Prestamo prestamo = new Prestamo();
        if (conExpediente) {
            Expediente expediente = expedienteRepository.findById(dto.expedienteId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Expediente", dto.expedienteId()));
            if (prestamoRepository.documentoPrestado(expediente.getId(), expediente.getContenedor().getId())) {
                throw new SolicitudInvalidaException(
                        "El documento " + expediente.getCodigoUnico() + " ya está prestado y no ha sido devuelto.");
            }
            prestamo.setExpediente(expediente);
        } else {
            Contenedor contenedor = contenedorRepository.findById(dto.contenedorId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Contenedor", dto.contenedorId()));
            if (prestamoRepository.contenedorPrestado(contenedor.getId())) {
                throw new SolicitudInvalidaException("«" + contenedor.getDescripcion()
                        + "» tiene un préstamo sin devolver (él o alguno de sus documentos).");
            }
            if (!expedienteRepository.existsByContenedorId(contenedor.getId())) {
                throw new SolicitudInvalidaException("El contenedor no tiene documentos registrados para prestar.");
            }
            prestamo.setContenedor(contenedor);
        }

        int dias = dto.dias() == null ? DIAS_POR_DEFECTO : dto.dias();
        prestamo.setSolicitanteNombre(dto.solicitante().trim());
        prestamo.setObservaciones(dto.observaciones() == null || dto.observaciones().isBlank()
                ? null : dto.observaciones().trim());
        prestamo.setFechaDevolucionPrevista(LocalDate.now().plusDays(dias));
        prestamo.setRegistradoPor(usuarioRepository.findById(registradoPorId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", registradoPorId)));
        return new PrestamoResponseDTO(prestamoRepository.saveAndFlush(prestamo).getId());
    }

    @Override
    @Transactional
    @Auditable(entidad = "Prestamo", accion = AccionAuditoria.MODIFICAR)
    public PrestamoResponseDTO devolver(UUID id) {
        Prestamo prestamo = prestamoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Prestamo", id));
        if (prestamo.getEstado() == EstadoPrestamo.DEVUELTO) {
            throw new SolicitudInvalidaException("Este préstamo ya fue devuelto.");
        }
        prestamo.setEstado(EstadoPrestamo.DEVUELTO);
        prestamo.setFechaDevolucionReal(LocalDateTime.now());
        prestamoRepository.flush();
        return new PrestamoResponseDTO(prestamo.getId());
    }
}
