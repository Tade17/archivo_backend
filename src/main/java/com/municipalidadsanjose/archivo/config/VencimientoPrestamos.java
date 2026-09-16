package com.municipalidadsanjose.archivo.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
@Configuration
@EnableScheduling
public class VencimientoPrestamos {
  private final JdbcTemplate db;
  public VencimientoPrestamos(JdbcTemplate db){this.db=db;}
  @Scheduled(fixedDelay=60000)
  public void actualizar(){
    db.update("UPDATE prestamo SET estado='VENCIDO',fecha_actualizacion=now() WHERE estado='PRESTADO' AND fecha_devolucion_prevista<CURRENT_DATE");
  }
}
