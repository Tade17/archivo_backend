package com.municipalidadsanjose.archivo.repository;

import com.municipalidadsanjose.archivo.entity.Prestamo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface PrestamoRepository extends JpaRepository<Prestamo, UUID> {
    boolean existsByExpedienteId(UUID expedienteId);
    boolean existsByContenedorId(UUID contenedorId);

    /** Hay un préstamo abierto que alcanza al documento: directo o por su contenedor. */
    @Query("""
            select count(p) > 0 from Prestamo p
             where p.estado = com.municipalidadsanjose.archivo.enums.EstadoPrestamo.PRESTADO
               and (p.expediente.id = :expedienteId or p.contenedor.id = :contenedorId)
            """)
    boolean documentoPrestado(@Param("expedienteId") UUID expedienteId,
                              @Param("contenedorId") UUID contenedorId);

    /** Hay un préstamo abierto sobre el contenedor o sobre alguno de sus documentos. */
    @Query("""
            select count(p) > 0 from Prestamo p left join p.expediente e
             where p.estado = com.municipalidadsanjose.archivo.enums.EstadoPrestamo.PRESTADO
               and (p.contenedor.id = :contenedorId or e.contenedor.id = :contenedorId)
            """)
    boolean contenedorPrestado(@Param("contenedorId") UUID contenedorId);
}
