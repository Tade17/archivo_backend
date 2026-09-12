package com.municipalidadsanjose.archivo.repository;

import com.municipalidadsanjose.archivo.entity.DocumentoDigital;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentoDigitalRepository extends JpaRepository<DocumentoDigital, UUID> {
    Page<DocumentoDigital> findByExpedienteId(UUID expedienteId, Pageable pageable);
}
