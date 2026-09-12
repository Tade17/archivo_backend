package com.municipalidadsanjose.archivo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "expediente")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Expediente {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "expediente_id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "codigo_unico", nullable = false, length = 50, unique = true)
    private String codigoUnico;

    @Column(name = "numero_documento", nullable = false, length = 50)
    private String numeroDocumento;

    @Column(nullable = false, length = 255)
    private String remitente;

    // Área a la que va dirigido el documento.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_destino_id", nullable = false)
    private AreaResponsable areaDestino;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_id", nullable = false)
    private TipoDocumental tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estado_id", nullable = false)
    private EstadoExpediente estado;

    // Fecha del documento físico original (la ingresa el archivista a mano).
    // No confundir con fechaRegistro: esta NO se autogenera.
    @Column(name = "fecha_documento", nullable = false)
    private LocalDate fechaDocumento;

    @Column(nullable = false, length = 500)
    private String asunto;

    // Resumen/cuerpo del documento: manual al inicio, luego vía OCR/IA.
    @Column(columnDefinition = "TEXT")
    private String glosa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caja_id", nullable = false)
    private Caja caja;

    @Column(name = "numero_folios")
    private Integer numeroFolios;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por_id", nullable = false)
    private Usuario creadoPor;

    // Timestamp real de inserción del registro (automático, distinto de fechaDocumento).
    @CreationTimestamp
    @Column(name = "fecha_registro", updatable = false, nullable = false)
    private LocalDateTime fechaRegistro;

    @UpdateTimestamp
    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "expediente_tag",
            joinColumns = @JoinColumn(name = "expediente_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();
}
