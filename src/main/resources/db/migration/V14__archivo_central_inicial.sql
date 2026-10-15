-- Todo archivo físico cuelga de un Archivo Central: las Secciones (estantes)
-- se crean dentro de él y la pantalla de Ubicaciones lo da por existente.
-- Una instalación nueva no lo tenía y no se podía crear la primera sección.
-- Idempotente: si ya hay uno (instalaciones anteriores) no hace nada.
INSERT INTO archivo_central (nombre)
SELECT 'Archivo Central Municipal'
WHERE NOT EXISTS (SELECT 1 FROM archivo_central);
