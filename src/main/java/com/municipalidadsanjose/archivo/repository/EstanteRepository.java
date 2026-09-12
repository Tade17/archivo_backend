package com.municipalidadsanjose.archivo.repository;

import com.municipalidadsanjose.archivo.entity.Estante;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EstanteRepository extends JpaRepository<Estante, UUID> {
    List<Estante> findByArchivoCentralId(UUID archivoCentralId);
    boolean existsByArchivoCentralId(UUID archivoCentralId);
    boolean existsByArchivoCentralIdAndCodigo(UUID archivoCentralId, String codigo);
}
