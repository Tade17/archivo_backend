package com.municipalidadsanjose.archivo.entity;

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

    // ocr_tsv NO se mapea: es una columna TSVECTOR que Postgres recalcula solo
    // (trigger trg_documento_ocr_tsv) a partir de ocr_texto. La app nunca la
    // lee ni la escribe directamente; la búsqueda full-text se hace con un
    // query nativo (to_tsquery) contra esa columna desde el repository.

    @UpdateTimestamp
    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;
}
