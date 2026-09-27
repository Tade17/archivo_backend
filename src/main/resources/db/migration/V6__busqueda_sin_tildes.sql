-- Permite buscar expedientes sin escribir las tildes: "camion" encuentra "camión".
-- La búsqueda dentro del texto OCR no la necesita: el diccionario 'spanish' de
-- PostgreSQL ya ignora las tildes.
CREATE EXTENSION IF NOT EXISTS unaccent;
