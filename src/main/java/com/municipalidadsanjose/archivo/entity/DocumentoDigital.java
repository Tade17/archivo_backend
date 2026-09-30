package com.municipalidadsanjose.archivo.entity;

import com.municipalidadsanjose.archivo.enums.EstadoOcr;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "documento_digital")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentoDigital {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "documento_id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expediente_id", nullable = false)
    private Expediente expediente;

    @Column(name = "nombre_archivo", nullable = false, length = 255)
    private String nombreArchivo;

    @Column(name = "ruta_almacenamiento", nullable = false, length = 500)
    private String rutaAlmacenamiento;

    @Column(name = "tipo_mime", nullable = false, length = 100)
    private String tipoMime;

    @Column(name = "hash_sha256", nullable = false, length = 64)
    private String hashSha256;

    @CreationTimestamp
    @Column(name = "fecha_digitalizacion", updatable = false, nullable = false)
    private LocalDateTime fechaDigitalizacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tecnico_responsable_id", nullable = false)
    private Usuario tecnicoResponsable;

    @Column(name = "escaner_utilizado", length = 100)
    private String escanerUtilizado;

    @Column(name = "resolucion_dpi")
    private Integer resolucionDpi;

    @Column(name = "formato_salida", length = 20)
    private String formatoSalida;

    @Column(name = "ocr_texto", columnDefinition = "TEXT")
    private String ocrTexto;

    @Enumerated(EnumType.STRING)
    @Column(name = "ocr_estado", nullable = false, length = 30)
    private EstadoOcr ocrEstado = EstadoOcr.PENDIENTE;

    @Column(name = "ocr_confianza", precision = 5, scale = 4)
    private java.math.BigDecimal ocrConfianza;

    @Column(name = "ocr_paginas")
    private Integer ocrPaginas;

    @Column(name = "ocr_error", columnDefinition = "TEXT")
    private String ocrError;

    @Column(name = "ocr_intentos", nullable = false)
    private int ocrIntentos;

    @Column(name = "ocr_revisado", nullable = false)
    private boolean ocrRevisado;

    @Column(name = "ocr_actualizado_en")
    private LocalDateTime ocrActualizadoEn;

    @Column(name = "ocr_layout", columnDefinition = "TEXT")
    private String ocrLayout;

    @Column(name = "ruta_pdf", length = 500)
    private String rutaPdf;

    @Column(name = "ocr_version", nullable = false)
    private long ocrVersion;

    // ocr_tsv NO se mapea: es una columna TSVECTOR que Postgres recalcula solo
    // (trigger trg_documento_ocr_tsv) a partir de ocr_texto. La app nunca la
    // lee ni la escribe directamente; la búsqueda full-text se hace con un
    // query nativo (to_tsquery) contra esa columna desde el repository.

    @UpdateTimestamp
    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;
}
