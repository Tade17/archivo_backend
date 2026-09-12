package com.municipalidadsanjose.archivo.repository;

import com.municipalidadsanjose.archivo.entity.TipoDocumental;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TipoDocumentalRepository extends JpaRepository<TipoDocumental, UUID> {
    boolean existsByNombre(String nombre);
}
