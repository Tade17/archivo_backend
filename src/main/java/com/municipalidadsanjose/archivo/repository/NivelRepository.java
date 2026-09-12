package com.municipalidadsanjose.archivo.repository;

import com.municipalidadsanjose.archivo.entity.Nivel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NivelRepository extends JpaRepository<Nivel, UUID> {
    List<Nivel> findByEstanteId(UUID estanteId);
    boolean existsByEstanteId(UUID estanteId);
    boolean existsByEstanteIdAndCodigo(UUID estanteId, String codigo);
}
