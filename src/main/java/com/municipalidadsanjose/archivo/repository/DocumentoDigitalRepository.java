package com.municipalidadsanjose.archivo.repository;

import com.municipalidadsanjose.archivo.entity.DocumentoDigital;
import com.municipalidadsanjose.archivo.enums.EstadoOcr;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DocumentoDigitalRepository extends JpaRepository<DocumentoDigital, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DocumentoDigital d where d.id = :id")
    java.util.Optional<DocumentoDigital> findLockedById(@Param("id") UUID id);

    @Modifying
    @Transactional
    @Query("update DocumentoDigital d set d.ocrEstado = com.municipalidadsanjose.archivo.enums.EstadoOcr.PENDIENTE where d.rutaPdf is null and d.ocrRevisado = false and d.ocrEstado in (com.municipalidadsanjose.archivo.enums.EstadoOcr.COMPLETADO, com.municipalidadsanjose.archivo.enums.EstadoOcr.REQUIERE_REVISION)")
    int programarPdfFaltantes();
    Page<DocumentoDigital> findByExpedienteId(UUID expedienteId, Pageable pageable);

    @Query("select d.id from DocumentoDigital d where d.ocrEstado in :estados")
    List<UUID> findIdsByOcrEstadoIn(@Param("estados") Collection<EstadoOcr> estados);

    @Modifying
    @Transactional
    @Query("""
            update DocumentoDigital d
               set d.ocrEstado = com.municipalidadsanjose.archivo.enums.EstadoOcr.PROCESANDO,
                   d.ocrError = null,
                   d.ocrIntentos = d.ocrIntentos + 1,
                   d.ocrActualizadoEn = :ahora
             where d.id = :id
               and d.ocrEstado = com.municipalidadsanjose.archivo.enums.EstadoOcr.PENDIENTE
            """)
    int marcarProcesando(@Param("id") UUID id, @Param("ahora") LocalDateTime ahora);

    @Modifying
    @Transactional
    @Query("""
            update DocumentoDigital d
               set d.ocrEstado = com.municipalidadsanjose.archivo.enums.EstadoOcr.PENDIENTE,
                   d.ocrError = null,
                   d.ocrActualizadoEn = :ahora
             where d.id = :id
            """)
    int marcarPendiente(@Param("id") UUID id, @Param("ahora") LocalDateTime ahora);

    @Modifying
    @Transactional
    @Query("""
            update DocumentoDigital d
               set d.ocrEstado = com.municipalidadsanjose.archivo.enums.EstadoOcr.PENDIENTE,
                   d.ocrError = 'El procesamiento se interrumpió y fue reprogramado al iniciar.',
                   d.ocrActualizadoEn = :ahora
             where d.ocrEstado = com.municipalidadsanjose.archivo.enums.EstadoOcr.PROCESANDO
            """)
    int recuperarProcesamientosInterrumpidos(@Param("ahora") LocalDateTime ahora);

    @Modifying
    @Transactional
    @Query("""
            update DocumentoDigital d
               set d.ocrTexto = :texto,
                   d.ocrConfianza = :confianza,
                   d.ocrPaginas = :paginas,
                   d.ocrEstado = :estado,
                   d.ocrError = null,
                   d.ocrRevisado = false,
                   d.ocrLayout = :layout,
                   d.rutaPdf = :rutaPdf,
                   d.ocrVersion = d.ocrVersion + 1,
                   d.ocrActualizadoEn = :ahora
             where d.id = :id
            """)
    int guardarResultadoOcr(@Param("id") UUID id,
                            @Param("texto") String texto,
                            @Param("confianza") BigDecimal confianza,
                            @Param("paginas") int paginas,
                            @Param("estado") EstadoOcr estado,
                            @Param("layout") String layout,
                            @Param("rutaPdf") String rutaPdf,
                            @Param("ahora") LocalDateTime ahora);

    @Modifying
    @Transactional
    @Query("""
            update DocumentoDigital d
               set d.ocrEstado = com.municipalidadsanjose.archivo.enums.EstadoOcr.ERROR,
                   d.ocrError = :error,
                   d.ocrActualizadoEn = :ahora
             where d.id = :id
            """)
    int marcarErrorOcr(@Param("id") UUID id,
                       @Param("error") String error,
                       @Param("ahora") LocalDateTime ahora);
}
