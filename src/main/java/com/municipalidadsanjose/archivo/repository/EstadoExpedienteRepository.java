package com.municipalidadsanjose.archivo.repository;

import com.municipalidadsanjose.archivo.entity.EstadoExpediente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EstadoExpedienteRepository extends JpaRepository<EstadoExpediente, UUID> {
    Optional<EstadoExpediente> findByNombre(String nombre);
    boolean existsByNombre(String nombre);
}
