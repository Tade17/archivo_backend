package com.municipalidadsanjose.archivo.repository;

import com.municipalidadsanjose.archivo.entity.Prestamo;
import com.municipalidadsanjose.archivo.enums.EstadoPrestamo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PrestamoRepository extends JpaRepository<Prestamo, UUID> {
    Page<Prestamo> findByExpedienteId(UUID expedienteId, Pageable pageable);
    Page<Prestamo> findBySolicitanteId(UUID solicitanteId, Pageable pageable);
    List<Prestamo> findByEstado(EstadoPrestamo estado);
}
