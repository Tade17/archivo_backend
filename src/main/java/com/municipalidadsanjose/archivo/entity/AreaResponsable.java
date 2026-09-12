package com.municipalidadsanjose.archivo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "area_responsable")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AreaResponsable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name ="area_id",updatable = false,nullable = false )
    private UUID id;

    @Column(length = 150,nullable = false,unique = true)
    private String nombre;

    @Column(length = 20,nullable = false,unique = true)
    private String codigo;


}
