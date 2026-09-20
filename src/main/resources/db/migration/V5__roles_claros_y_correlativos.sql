-- Unifica registro y digitalización en un solo perfil operativo.
INSERT INTO rol(nombre, descripcion) VALUES
('GESTOR_DOCUMENTAL', 'Registra, digitaliza y administra documentos'),
('LECTOR', 'Busca y consulta documentos sin modificarlos')
ON CONFLICT(nombre) DO NOTHING;

UPDATE usuario SET rol_id=(SELECT rol_id FROM rol WHERE nombre='GESTOR_DOCUMENTAL')
WHERE rol_id IN (SELECT rol_id FROM rol WHERE nombre IN ('ARCHIVISTA','DIGITALIZACION'));

UPDATE usuario SET rol_id=(SELECT rol_id FROM rol WHERE nombre='LECTOR')
WHERE rol_id IN (SELECT rol_id FROM rol WHERE nombre='CONSULTA');

DELETE FROM rol WHERE nombre IN ('ARCHIVISTA','DIGITALIZACION','CONSULTA');

-- Correlativo seguro por año. El bloqueo de la fila evita números repetidos
-- aunque dos personas registren al mismo tiempo.
CREATE TABLE correlativo_expediente (
    anio INTEGER PRIMARY KEY,
    ultimo_numero INTEGER NOT NULL
);
