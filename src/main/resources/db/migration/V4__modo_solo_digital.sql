-- El expediente ya no requiere una contraparte física. Se preservan las
-- ubicaciones y préstamos históricos, pero los nuevos registros son digitales.
ALTER TABLE expediente ALTER COLUMN caja_id DROP NOT NULL;
