package com.municipalidadsanjose.archivo.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name ="tipo_documental")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TipoDocumental {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tipo_id",updatable = false,nullable = false)
    private UUID id;

    @Column(unique = true,length = 100,nullable = false)
    private String nombre;
}
