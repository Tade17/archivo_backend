-- El sistema es de archivo: los documentos digitalizados son históricos y no
-- cambian de estado a lo largo de su vida (eso corresponde a un módulo de
-- Trámite Documentario, que no forma parte de este alcance). Por eso el estado
-- del documento queda reducido a "Registrado" y desaparecen Activo, En trámite
-- y Archivado. El avance de la digitalización lo lleva el contenedor
-- (PENDIENTE / COMPLETADO), no el documento.
INSERT INTO estado_expediente(nombre) VALUES ('Registrado') ON CONFLICT (nombre) DO NOTHING;

UPDATE expediente
SET estado_id = (SELECT estado_id FROM estado_expediente WHERE nombre = 'Registrado')
WHERE estado_id <> (SELECT estado_id FROM estado_expediente WHERE nombre = 'Registrado');

DELETE FROM estado_expediente WHERE nombre <> 'Registrado';
