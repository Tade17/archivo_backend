package com.municipalidadsanjose.archivo.repository;

import com.municipalidadsanjose.archivo.entity.Expediente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExpedienteRepository extends JpaRepository<Expediente, UUID> {
    Optional<Expediente> findByCodigoUnico(String codigoUnico);
    boolean existsByCodigoUnico(String codigoUnico);
    boolean existsByAreaDestinoId(UUID areaDestinoId);
    boolean existsByTipoId(UUID tipoId);
    boolean existsByEstadoId(UUID estadoId);
    boolean existsByCajaId(UUID cajaId);
    boolean existsByTagsId(UUID tagId);
}
