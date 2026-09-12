package com.municipalidadsanjose.archivo.repository;

import com.municipalidadsanjose.archivo.entity.AreaResponsable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AreaResponsableRepository extends JpaRepository<AreaResponsable, UUID> {
    boolean existsByNombre(String nombre);
    boolean existsByCodigo(String codigo);
}
