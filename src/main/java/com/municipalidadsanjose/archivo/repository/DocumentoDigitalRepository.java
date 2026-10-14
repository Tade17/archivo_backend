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
    @Query("update DocumentoDigital d set d.ocrEstado = com.municipalidadsanjose.archivo.enums.EstadoOcr.PENDIENTE, d.ocrJobId=null, d.ocrJobCapability=null, d.ocrEngineFingerprint=null, d.ocrJobSubmitted=null, d.ocrNextRetryAt=null where d.rutaPdf is null and d.ocrRevisado = false and d.ocrEstado in (com.municipalidadsanjose.archivo.enums.EstadoOcr.COMPLETADO, com.municipalidadsanjose.archivo.enums.EstadoOcr.REQUIERE_REVISION)")
    int programarPdfFaltantes();
    Page<DocumentoDigital> findByExpedienteId(UUID expedienteId, Pageable pageable);
    List<DocumentoDigital> findByExpedienteId(UUID expedienteId);

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

    String CLAIM_SQL = """
            WITH candidate AS (
                SELECT documento_id FROM documento_digital
                WHERE ocr_estado IN ('PENDIENTE','PROCESANDO')
                  AND (ocr_next_retry_at IS NULL OR ocr_next_retry_at <= CURRENT_TIMESTAMP)
                  AND (ocr_lease_expires_at IS NULL OR ocr_lease_expires_at <= CURRENT_TIMESTAMP)
                ORDER BY fecha_digitalizacion, documento_id
                FOR UPDATE SKIP LOCKED LIMIT 1
            )
            UPDATE documento_digital d SET ocr_estado='PROCESANDO',
                ocr_lease_owner=:owner, ocr_lease_expires_at=CURRENT_TIMESTAMP + make_interval(secs => :seconds),
                ocr_intentos=ocr_intentos + CASE WHEN ocr_job_id IS NULL AND ocr_estado='PENDIENTE' THEN 1 ELSE 0 END,
                ocr_fase=COALESCE(ocr_fase,'QUEUED'), ocr_actualizado_en=CURRENT_TIMESTAMP
            FROM candidate c WHERE d.documento_id=c.documento_id RETURNING d.documento_id
            """;
    String FENCE = " documento_id=:id AND ocr_estado='PROCESANDO' AND ocr_lease_owner=:owner AND ocr_lease_expires_at>CURRENT_TIMESTAMP ";
    String HEARTBEAT_SQL = "UPDATE documento_digital SET ocr_lease_expires_at=CURRENT_TIMESTAMP + make_interval(secs => :seconds) WHERE " + FENCE;
    String IDENTITY_SQL = "UPDATE documento_digital SET ocr_job_id=:job, ocr_job_capability=:capability, ocr_engine_fingerprint=:fingerprint, ocr_job_submitted=false WHERE " + FENCE + " AND ocr_job_id IS NULL";
    String PROGRESS_SQL = """
            UPDATE documento_digital SET ocr_job_submitted=true, ocr_paginas_procesadas=:completed,
            ocr_paginas_totales=:pages, ocr_fase=:phase, ocr_next_retry_at=CURRENT_TIMESTAMP + make_interval(secs => :delay),
            ocr_lease_owner=NULL, ocr_lease_expires_at=NULL, ocr_actualizado_en=CURRENT_TIMESTAMP
            WHERE
            """ + FENCE + " AND ocr_job_id=:job";
    String FAILURE_SQL = """
            UPDATE documento_digital SET ocr_estado=:state, ocr_error=:error, ocr_retries=COALESCE(ocr_retries,0)+1,
            ocr_fase=:phase, ocr_next_retry_at=CASE WHEN :state='PROCESANDO' THEN CURRENT_TIMESTAMP + make_interval(secs => :delay) ELSE NULL END, ocr_lease_owner=NULL, ocr_lease_expires_at=NULL,
            ocr_actualizado_en=CURRENT_TIMESTAMP WHERE
            """ + FENCE + " AND ocr_job_id IS NOT DISTINCT FROM :job";
    String PUBLISH_SQL = """
            UPDATE documento_digital SET ocr_texto=:text, ocr_confianza=:confidence, ocr_paginas=:pages,
            ocr_estado=:state, ocr_error=NULL, ocr_revisado=false, ocr_layout=:layout, ruta_pdf=:path,
            ocr_version=ocr_version+1, ocr_actualizado_en=CURRENT_TIMESTAMP,
            ocr_paginas_procesadas=:pages, ocr_paginas_totales=:pages, ocr_fase='COMPLETED',
            ocr_next_retry_at=NULL, ocr_lease_owner=NULL, ocr_lease_expires_at=NULL, ocr_job_capability=NULL
            WHERE
            """ + FENCE + " AND ocr_job_id=:job";

    @Transactional
    @Query(value = CLAIM_SQL, nativeQuery = true)
    List<UUID> reclamarSiguiente(@Param("owner") String owner, @Param("seconds") int seconds);

    @Modifying @Transactional
    @Query(value = HEARTBEAT_SQL, nativeQuery = true)
    int renovarLease(@Param("id") UUID id, @Param("owner") String owner, @Param("seconds") int seconds);

    @Modifying @Transactional
    @Query(value = IDENTITY_SQL, nativeQuery = true)
    int guardarJobIdentity(@Param("id") UUID id, @Param("owner") String owner, @Param("job") String job,
                           @Param("capability") String capability, @Param("fingerprint") String fingerprint);

    @Modifying @Transactional
    @Query(value = "UPDATE documento_digital SET ocr_job_submitted=true WHERE " + FENCE + " AND ocr_job_id=:job", nativeQuery = true)
    int confirmarJobEnvio(@Param("id") UUID id, @Param("owner") String owner, @Param("job") String job);

    @Modifying @Transactional
    @Query(value = PROGRESS_SQL, nativeQuery = true)
    int guardarProgreso(@Param("id") UUID id, @Param("owner") String owner, @Param("job") String job,
                        @Param("completed") int completed, @Param("pages") int pages, @Param("phase") String phase,
                        @Param("delay") int delaySeconds);

    @Modifying @Transactional
    @Query(value = FAILURE_SQL, nativeQuery = true)
    int guardarFalloJob(@Param("id") UUID id, @Param("owner") String owner, @Param("job") String job,
                       @Param("state") String state, @Param("error") String error, @Param("phase") String phase,
                       @Param("delay") int delaySeconds);

    @Modifying @Transactional
    @Query(value = PUBLISH_SQL, nativeQuery = true)
    int publicarJob(@Param("id") UUID id, @Param("owner") String owner, @Param("job") String job,
                    @Param("text") String text, @Param("confidence") BigDecimal confidence, @Param("pages") int pages,
                    @Param("state") String state, @Param("layout") String layout, @Param("path") String path);

    @Query("select d from DocumentoDigital d join fetch d.expediente where d.id=:id")
    java.util.Optional<DocumentoDigital> findForOcr(@Param("id") UUID id);

    @Modifying @Transactional
    @Query(value = """
            UPDATE documento_digital SET ocr_estado='PENDIENTE', ocr_error=NULL, ocr_job_id=NULL,
            ocr_job_capability=NULL, ocr_engine_fingerprint=NULL, ocr_job_submitted=NULL,
            ocr_lease_owner=NULL, ocr_lease_expires_at=NULL, ocr_next_retry_at=NULL,
            ocr_retries=0, ocr_paginas_procesadas=NULL, ocr_paginas_totales=NULL,
            ocr_fase='QUEUED', ocr_actualizado_en=CURRENT_TIMESTAMP WHERE documento_id=:id
            """, nativeQuery = true)
    int reiniciarJob(@Param("id") UUID id);
}
