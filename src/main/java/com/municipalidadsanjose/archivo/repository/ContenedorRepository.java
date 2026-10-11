package com.municipalidadsanjose.archivo.repository;

import com.municipalidadsanjose.archivo.entity.Contenedor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ContenedorRepository extends JpaRepository<Contenedor, UUID> {
    boolean existsByCajaId(UUID cajaId);
}
