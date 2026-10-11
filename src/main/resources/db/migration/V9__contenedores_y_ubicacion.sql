-- Ubicación física vuelve al alcance (reunión con el Ing. Yomar, oct 2026).
-- El archivo se organiza como Sección -> Nivel -> Área, que corresponden a las
-- tablas existentes estante -> nivel -> caja. Dentro de cada Área no se guarda
-- el documento suelto sino un contenedor físico (archivador o folder), y los
-- documentos (expedientes) se registran uno por uno dentro del contenedor.
-- El contenedor queda PENDIENTE hasta que el usuario marca a mano que ya subió
-- todos sus documentos (COMPLETADO); puede reabrirse si apareció uno faltante.

CREATE TABLE contenedor (
    contenedor_id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    caja_id                UUID NOT NULL REFERENCES caja(caja_id),
    tipo_contenedor        VARCHAR(20) NOT NULL,
    descripcion            VARCHAR(255) NOT NULL,
    estado_digitalizacion  VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    creado_por_id          UUID NOT NULL REFERENCES usuario(usuario_id),
    fecha_registro         TIMESTAMP NOT NULL DEFAULT now(),
    fecha_actualizacion    TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_contenedor_tipo CHECK (tipo_contenedor IN ('ARCHIVADOR', 'FOLDER')),
    CONSTRAINT chk_contenedor_estado CHECK (estado_digitalizacion IN ('PENDIENTE', 'COMPLETADO'))
);

CREATE INDEX idx_contenedor_caja ON contenedor(caja_id);
CREATE INDEX idx_contenedor_estado ON contenedor(estado_digitalizacion);

ALTER TABLE expediente ADD COLUMN destinatario VARCHAR(255);
ALTER TABLE expediente ADD COLUMN contenedor_id UUID REFERENCES contenedor(contenedor_id);

-- Los expedientes registrados antes de este cambio no tienen ubicación física.
-- Se agrupan en una ubicación y un contenedor provisional "Sin ubicar" para que
-- contenedor_id pueda ser obligatorio; luego pueden reubicarse desde el sistema.
DO $$
DECLARE
    v_archivo UUID;
    v_estante UUID;
    v_nivel   UUID;
    v_caja    UUID;
    v_admin   UUID;
    v_cont    UUID;
    r         RECORD;
BEGIN
    SELECT u.usuario_id INTO v_admin FROM usuario u JOIN rol r2 ON r2.rol_id = u.rol_id
    WHERE r2.nombre = 'ADMIN' ORDER BY u.fecha_creacion LIMIT 1;
    IF v_admin IS NULL THEN
        SELECT usuario_id INTO v_admin FROM usuario ORDER BY fecha_creacion LIMIT 1;
    END IF;

    -- Expedientes que ya tenían caja: un contenedor por caja, conservando su ubicación.
    FOR r IN SELECT DISTINCT caja_id FROM expediente WHERE caja_id IS NOT NULL LOOP
        INSERT INTO contenedor(caja_id, tipo_contenedor, descripcion, creado_por_id)
        VALUES (r.caja_id, 'FOLDER', 'Documentos migrados de esta ubicación', v_admin)
        RETURNING contenedor_id INTO v_cont;
        UPDATE expediente SET contenedor_id = v_cont WHERE caja_id = r.caja_id;
    END LOOP;

    IF EXISTS (SELECT 1 FROM expediente WHERE contenedor_id IS NULL) THEN
        SELECT archivo_central_id INTO v_archivo FROM archivo_central ORDER BY nombre LIMIT 1;
        IF v_archivo IS NULL THEN
            INSERT INTO archivo_central(nombre) VALUES ('Archivo Central Municipal')
            RETURNING archivo_central_id INTO v_archivo;
        END IF;

        INSERT INTO estante(archivo_central_id, codigo) VALUES (v_archivo, 'SIN UBICAR')
        ON CONFLICT (archivo_central_id, codigo) DO NOTHING;
        SELECT estante_id INTO v_estante FROM estante
        WHERE archivo_central_id = v_archivo AND codigo = 'SIN UBICAR';

        INSERT INTO nivel(estante_id, codigo) VALUES (v_estante, 'SIN UBICAR')
        ON CONFLICT (estante_id, codigo) DO NOTHING;
        SELECT nivel_id INTO v_nivel FROM nivel WHERE estante_id = v_estante AND codigo = 'SIN UBICAR';

        INSERT INTO caja(nivel_id, codigo) VALUES (v_nivel, 'SIN UBICAR')
        ON CONFLICT (nivel_id, codigo) DO NOTHING;
        SELECT caja_id INTO v_caja FROM caja WHERE nivel_id = v_nivel AND codigo = 'SIN UBICAR';

        INSERT INTO contenedor(caja_id, tipo_contenedor, descripcion, creado_por_id)
        VALUES (v_caja, 'FOLDER', 'Documentos registrados antes de la ubicación física', v_admin)
        RETURNING contenedor_id INTO v_cont;

        UPDATE expediente SET contenedor_id = v_cont WHERE contenedor_id IS NULL;
    END IF;
END $$;

ALTER TABLE expediente ALTER COLUMN contenedor_id SET NOT NULL;
CREATE INDEX idx_expediente_contenedor ON expediente(contenedor_id);

-- La ubicación del expediente ahora se obtiene a través de su contenedor
-- (expediente -> contenedor -> caja -> nivel -> estante); la caja que tuviera
-- cada expediente ya quedó reflejada en su contenedor arriba.
ALTER TABLE expediente DROP COLUMN caja_id;
