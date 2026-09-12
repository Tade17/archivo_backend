package com.municipalidadsanjose.archivo.repository;

import com.municipalidadsanjose.archivo.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, UUID> {
    Optional<Tag> findByNombre(String nombre);
    boolean existsByNombre(String nombre);
}
