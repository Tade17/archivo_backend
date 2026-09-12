package com.municipalidadsanjose.archivo.repository;

import com.municipalidadsanjose.archivo.entity.Caja;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CajaRepository extends JpaRepository<Caja, UUID> {
    List<Caja> findByNivelId(UUID nivelId);
    boolean existsByNivelId(UUID nivelId);
    boolean existsByNivelIdAndCodigo(UUID nivelId, String codigo);
}
