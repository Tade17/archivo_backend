package com.municipalidadsanjose.archivo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "estado_expediente")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EstadoExpediente {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "estado_id",updatable = false,nullable = false)
    private UUID id;


    @Column(unique = true,length = 50,nullable = false)
    private String nombre; //Archivado ,Activo, En tramite ,etc....
}
