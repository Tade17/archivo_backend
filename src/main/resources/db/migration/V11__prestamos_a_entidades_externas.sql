-- Préstamos físicos a entidades externas (Fiscalía, Contraloría, etc.).
-- Se presta un documento (expediente) o un archivador/folder completo (contenedor).
-- El solicitante deja de ser un usuario del sistema: es un nombre libre.
-- El vencimiento (semáforo) se calcula al consultar a partir de la fecha prevista;
-- en la tabla el préstamo solo está PRESTADO o DEVUELTO.

ALTER TABLE prestamo ADD COLUMN contenedor_id UUID REFERENCES contenedor(contenedor_id);
ALTER TABLE prestamo ADD COLUMN solicitante_nombre VARCHAR(200);
ALTER TABLE prestamo ADD COLUMN registrado_por_id UUID REFERENCES usuario(usuario_id);

UPDATE prestamo p
   SET solicitante_nombre = u.nombre,
       registrado_por_id = p.solicitante_id
  FROM usuario u
 WHERE u.usuario_id = p.solicitante_id;

UPDATE prestamo SET fecha_devolucion_prevista = fecha_solicitud::date + 7
 WHERE fecha_devolucion_prevista IS NULL;

-- Los estados VENCIDO antiguos pasan a PRESTADO: ahora el vencimiento se calcula.
UPDATE prestamo SET estado = 'PRESTADO' WHERE estado = 'VENCIDO';

ALTER TABLE prestamo ALTER COLUMN solicitante_nombre SET NOT NULL;
ALTER TABLE prestamo ALTER COLUMN registrado_por_id SET NOT NULL;
ALTER TABLE prestamo ALTER COLUMN fecha_devolucion_prevista SET NOT NULL;
ALTER TABLE prestamo ALTER COLUMN expediente_id DROP NOT NULL;
ALTER TABLE prestamo ALTER COLUMN tipo_solicitud SET DEFAULT 'FISICO';

DROP TRIGGER IF EXISTS trg_custodia_fisica ON prestamo;
DROP FUNCTION IF EXISTS validar_custodia_fisica();

ALTER TABLE prestamo DROP COLUMN solicitante_id;

ALTER TABLE prestamo ADD CONSTRAINT chk_prestamo_objeto
    CHECK ((expediente_id IS NOT NULL)::int + (contenedor_id IS NOT NULL)::int = 1);
ALTER TABLE prestamo ADD CONSTRAINT chk_prestamo_estado
    CHECK (estado IN ('PRESTADO', 'DEVUELTO'));

-- Un mismo documento o contenedor no puede tener dos préstamos abiertos a la vez.
CREATE UNIQUE INDEX uq_prestamo_expediente_abierto ON prestamo(expediente_id)
    WHERE estado = 'PRESTADO' AND expediente_id IS NOT NULL;
CREATE UNIQUE INDEX uq_prestamo_contenedor_abierto ON prestamo(contenedor_id)
    WHERE estado = 'PRESTADO' AND contenedor_id IS NOT NULL;
CREATE INDEX idx_prestamo_contenedor ON prestamo(contenedor_id);
