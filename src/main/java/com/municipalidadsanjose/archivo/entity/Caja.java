package com.municipalidadsanjose.archivo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "caja", uniqueConstraints = @UniqueConstraint(columnNames = {"nivel_id", "codigo"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Caja {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "caja_id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nivel_id", nullable = false)
    private Nivel nivel;

    // Único solo dentro de su Nivel, no globalmente.
    @Column(nullable = false, length = 50)
    private String codigo;
}
