ALTER TABLE expediente ADD COLUMN numero_tramite VARCHAR(50);
ALTER TABLE expediente ADD COLUMN anio_ingreso INTEGER;
CREATE UNIQUE INDEX uq_expediente_tramite_anio ON expediente (lower(numero_tramite), anio_ingreso) WHERE numero_tramite IS NOT NULL;
ALTER TABLE prestamo ADD COLUMN motivo TEXT;
ALTER TABLE prestamo ADD COLUMN condicion_devolucion VARCHAR(30);
ALTER TABLE prestamo ADD COLUMN observaciones TEXT;
ALTER TABLE prestamo ADD COLUMN caja_devolucion_id UUID REFERENCES caja(caja_id);

INSERT INTO rol(nombre, descripcion) VALUES
('ARCHIVISTA', 'Catalogación, préstamos y consulta de auditoría'),
('DIGITALIZACION', 'Carga de documentos digitalizados'),
('CONSULTA', 'Búsqueda y visualización') ON CONFLICT(nombre) DO NOTHING;
INSERT INTO estado_expediente(nombre) VALUES ('Registrado'), ('Activo'), ('Archivado') ON CONFLICT(nombre) DO NOTHING;
INSERT INTO tipo_documental(nombre) VALUES ('Informe'), ('Carta'), ('Memorando'), ('Solicitud') ON CONFLICT(nombre) DO NOTHING;

-- El bloqueo de la fila padre serializa dos entregas físicas concurrentes.
CREATE FUNCTION validar_custodia_fisica() RETURNS trigger AS $$
BEGIN
  IF NEW.tipo_solicitud = 'FISICO' AND NEW.estado IN ('PRESTADO', 'VENCIDO') THEN
    PERFORM 1 FROM expediente WHERE expediente_id = NEW.expediente_id FOR UPDATE;
    IF EXISTS (SELECT 1 FROM prestamo WHERE expediente_id = NEW.expediente_id
      AND tipo_solicitud = 'FISICO' AND estado IN ('PRESTADO', 'VENCIDO')
      AND prestamo_id <> NEW.prestamo_id) THEN
      RAISE EXCEPTION 'El expediente ya tiene un préstamo físico activo' USING ERRCODE = '23505';
    END IF;
    IF NEW.fecha_devolucion_prevista IS NULL THEN
      RAISE EXCEPTION 'La devolución prevista es obligatoria' USING ERRCODE = '23514';
    END IF;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_custodia_fisica BEFORE INSERT OR UPDATE ON prestamo FOR EACH ROW EXECUTE FUNCTION validar_custodia_fisica();
