-- =========================================================
-- Sistema de Digitalización y Gestión de Archivo Físico Municipal
-- Municipalidad Distrital de San José
-- Motor: PostgreSQL
-- =========================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =========================================================
-- MÓDULO 1: USUARIOS Y ROLES
-- =========================================================

CREATE TABLE rol (
    rol_id      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nombre      VARCHAR(50) NOT NULL UNIQUE,
    descripcion VARCHAR(255)
);

CREATE TABLE usuario (
    usuario_id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nombre              VARCHAR(150) NOT NULL,
    correo              VARCHAR(150) NOT NULL UNIQUE,
    password_hash       VARCHAR(255) NOT NULL,
    rol_id              UUID NOT NULL REFERENCES rol(rol_id),
    activo              BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion      TIMESTAMP NOT NULL DEFAULT now(),
    fecha_actualizacion TIMESTAMP NOT NULL DEFAULT now()
);

-- =========================================================
-- CATÁLOGOS
-- =========================================================

CREATE TABLE area_responsable (
    area_id     UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nombre      VARCHAR(150) NOT NULL UNIQUE,
    codigo      VARCHAR(20) NOT NULL UNIQUE
);

CREATE TABLE tipo_documental (
    tipo_id     UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nombre      VARCHAR(100) NOT NULL UNIQUE -- Acta, Resolucion, Licencia, Contrato...
);

CREATE TABLE estado_expediente (
    estado_id   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nombre      VARCHAR(50) NOT NULL UNIQUE -- Activo, Archivado, En Tramite...
);

-- =========================================================
-- UBICACIÓN FÍSICA/VIRTUAL (jerarquía de 4 niveles)
-- ArchivoCentral -> Estante -> Nivel -> Caja -> Expediente
-- =========================================================

CREATE TABLE archivo_central (
    archivo_central_id  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nombre               VARCHAR(150) NOT NULL UNIQUE
);

CREATE TABLE estante (
    estante_id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    archivo_central_id   UUID NOT NULL REFERENCES archivo_central(archivo_central_id),
    codigo                VARCHAR(50) NOT NULL,
    UNIQUE (archivo_central_id, codigo)
);

CREATE TABLE nivel (
    nivel_id     UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    estante_id   UUID NOT NULL REFERENCES estante(estante_id),
    codigo        VARCHAR(50) NOT NULL,
    UNIQUE (estante_id, codigo)
);

CREATE TABLE caja (
    caja_id    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nivel_id   UUID NOT NULL REFERENCES nivel(nivel_id),
    codigo      VARCHAR(50) NOT NULL,
    UNIQUE (nivel_id, codigo)
);

CREATE INDEX idx_estante_archivo ON estante(archivo_central_id);
CREATE INDEX idx_nivel_estante ON nivel(estante_id);
CREATE INDEX idx_caja_nivel ON caja(nivel_id);

-- =========================================================
-- MÓDULO 2: RECEPCIÓN Y CATALOGACIÓN (campos generalizados)
-- =========================================================

CREATE TABLE expediente (
    expediente_id        UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    codigo_unico          VARCHAR(50) NOT NULL UNIQUE, -- hash/código para QR, no adivinable
    numero_documento       VARCHAR(50) NOT NULL,
    remitente              VARCHAR(255) NOT NULL,
    area_destino_id       UUID NOT NULL REFERENCES area_responsable(area_id),
    tipo_id                UUID NOT NULL REFERENCES tipo_documental(tipo_id),
    estado_id              UUID NOT NULL REFERENCES estado_expediente(estado_id),
    fecha_documento         DATE NOT NULL,               -- fecha del documento original (no timestamp del registro)
    asunto                 VARCHAR(500) NOT NULL,
    glosa                  TEXT,                        -- resumen/cuerpo: manual al inicio, luego vía OCR/IA
    caja_id                 UUID NOT NULL REFERENCES caja(caja_id),
    numero_folios           INT,
    creado_por_id           UUID NOT NULL REFERENCES usuario(usuario_id),
    fecha_registro           TIMESTAMP NOT NULL DEFAULT now(),
    fecha_actualizacion      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_expediente_codigo ON expediente(codigo_unico);
CREATE INDEX idx_expediente_area_destino ON expediente(area_destino_id);
CREATE INDEX idx_expediente_tipo ON expediente(tipo_id);
CREATE INDEX idx_expediente_caja ON expediente(caja_id);

-- =========================================================
-- MÓDULO 3: DIGITALIZACIÓN
-- =========================================================

CREATE TABLE documento_digital (
    documento_id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    expediente_id            UUID NOT NULL REFERENCES expediente(expediente_id) ON DELETE CASCADE,
    nombre_archivo            VARCHAR(255) NOT NULL,
    ruta_almacenamiento       VARCHAR(500) NOT NULL, -- ruta/clave en el servidor (evaluar cifrado a nivel app)
    tipo_mime                 VARCHAR(100) NOT NULL, -- application/pdf, image/tiff
    hash_sha256                VARCHAR(64) NOT NULL,   -- integridad del archivo
    fecha_digitalizacion       TIMESTAMP NOT NULL DEFAULT now(),
    tecnico_responsable_id    UUID NOT NULL REFERENCES usuario(usuario_id),
    escaner_utilizado          VARCHAR(100),
    resolucion_dpi              INT,
    formato_salida              VARCHAR(20), -- PDF/A, TIFF
    ocr_texto                    TEXT,        -- texto extraído por OCR
    ocr_tsv                       TSVECTOR,    -- columna derivada para búsqueda full-text rápida
    fecha_actualizacion           TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_documento_expediente ON documento_digital(expediente_id);
CREATE INDEX idx_documento_ocr ON documento_digital USING GIN(ocr_tsv);

-- Trigger para mantener ocr_tsv sincronizado con ocr_texto
CREATE FUNCTION documento_ocr_tsv_update() RETURNS trigger AS $$
BEGIN
    NEW.ocr_tsv := to_tsvector('spanish', coalesce(NEW.ocr_texto, ''));
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_documento_ocr_tsv
BEFORE INSERT OR UPDATE ON documento_digital
FOR EACH ROW EXECUTE FUNCTION documento_ocr_tsv_update();

-- =========================================================
-- MÓDULO 4: TAGS (soporte para búsqueda/organización)
-- =========================================================

CREATE TABLE tag (
    tag_id  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nombre  VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE expediente_tag (
    expediente_id   UUID NOT NULL REFERENCES expediente(expediente_id) ON DELETE CASCADE,
    tag_id          UUID NOT NULL REFERENCES tag(tag_id) ON DELETE CASCADE,
    PRIMARY KEY (expediente_id, tag_id)
);

-- =========================================================
-- MÓDULO 5: PRÉSTAMOS Y TRAZABILIDAD
-- =========================================================

CREATE TABLE prestamo (
    prestamo_id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    expediente_id             UUID NOT NULL REFERENCES expediente(expediente_id),
    solicitante_id             UUID NOT NULL REFERENCES usuario(usuario_id),
    tipo_solicitud              VARCHAR(20) NOT NULL, -- FISICO, DIGITAL
    fecha_solicitud             TIMESTAMP NOT NULL DEFAULT now(),
    fecha_devolucion_prevista  DATE,
    fecha_devolucion_real       TIMESTAMP,
    estado                     VARCHAR(20) NOT NULL DEFAULT 'PRESTADO', -- PRESTADO, DEVUELTO, VENCIDO
    fecha_actualizacion          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_prestamo_expediente ON prestamo(expediente_id);
CREATE INDEX idx_prestamo_solicitante ON prestamo(solicitante_id);

-- =========================================================
-- MÓDULO 6: AUDITORÍA Y SEGURIDAD
-- =========================================================

CREATE TABLE auditoria (
    auditoria_id      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    usuario_id        UUID NOT NULL REFERENCES usuario(usuario_id),
    entidad_afectada  VARCHAR(50) NOT NULL, -- 'Expediente', 'DocumentoDigital', etc.
    entidad_id        UUID NOT NULL,
    accion            VARCHAR(20) NOT NULL, -- CREAR, MODIFICAR, DESCARGAR, ELIMINAR
    fecha             TIMESTAMP NOT NULL DEFAULT now(),
    detalle           JSONB -- info adicional opcional (valores antes/despues, IP, etc.)
);

CREATE INDEX idx_auditoria_entidad ON auditoria(entidad_afectada, entidad_id);
CREATE INDEX idx_auditoria_usuario ON auditoria(usuario_id);
