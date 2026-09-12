package com.municipalidadsanjose.archivo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "rol")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Rol {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "rol_id", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 50, unique = true)
    private String nombre;

    @Column(length = 255)
    private String descripcion;
}
