package com.municipalidadsanjose.archivo.repository;

import com.municipalidadsanjose.archivo.entity.ArchivoCentral;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ArchivoCentralRepository extends JpaRepository<ArchivoCentral, UUID> {
    boolean existsByNombre(String nombre);
}
