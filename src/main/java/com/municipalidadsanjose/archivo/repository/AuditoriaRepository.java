package com.municipalidadsanjose.archivo.repository;

import com.municipalidadsanjose.archivo.entity.Auditoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditoriaRepository extends JpaRepository<Auditoria, UUID> {
    List<Auditoria> findByEntidadAfectadaAndEntidadId(String entidadAfectada, UUID entidadId);
}
