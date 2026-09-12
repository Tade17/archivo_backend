package com.municipalidadsanjose.archivo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "nivel", uniqueConstraints = @UniqueConstraint(columnNames = {"estante_id", "codigo"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Nivel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "nivel_id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estante_id", nullable = false)
    private Estante estante;

    // Único solo dentro de su Estante, no globalmente.
    @Column(nullable = false, length = 50)
    private String codigo;
}
