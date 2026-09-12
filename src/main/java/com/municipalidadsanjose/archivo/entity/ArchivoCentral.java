package com.municipalidadsanjose.archivo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "archivo_central")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ArchivoCentral {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "archivo_central_id", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 150, unique = true)
    private String nombre;
}
