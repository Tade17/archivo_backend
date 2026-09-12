package com.municipalidadsanjose.archivo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "estante", uniqueConstraints = @UniqueConstraint(columnNames = {"archivo_central_id", "codigo"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Estante {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "estante_id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "archivo_central_id", nullable = false)
    private ArchivoCentral archivoCentral;

    // Único solo dentro de su ArchivoCentral, no globalmente (ver uniqueConstraints arriba).
    @Column(nullable = false, length = 50)
    private String codigo;
}
