-- Estado persistente del OCR automático. Los documentos que ya tenían una
-- transcripción se consideran completados; los demás quedan pendientes para
-- que el worker los procese al iniciar la aplicación.
ALTER TABLE documento_digital
    ADD COLUMN ocr_estado VARCHAR(30),
    ADD COLUMN ocr_confianza NUMERIC(5,4),
    ADD COLUMN ocr_paginas INTEGER,
    ADD COLUMN ocr_error TEXT,
    ADD COLUMN ocr_intentos INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN ocr_revisado BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN ocr_actualizado_en TIMESTAMP;

UPDATE documento_digital
SET ocr_estado = CASE
    WHEN NULLIF(trim(ocr_texto), '') IS NOT NULL THEN 'COMPLETADO'
    ELSE 'PENDIENTE'
END;

ALTER TABLE documento_digital
    ALTER COLUMN ocr_estado SET NOT NULL,
    ALTER COLUMN ocr_estado SET DEFAULT 'PENDIENTE';

ALTER TABLE documento_digital
    ADD CONSTRAINT chk_documento_ocr_estado CHECK (
        ocr_estado IN ('PENDIENTE', 'PROCESANDO', 'COMPLETADO', 'REQUIERE_REVISION', 'ERROR')
    ),
    ADD CONSTRAINT chk_documento_ocr_confianza CHECK (
        ocr_confianza IS NULL OR (ocr_confianza >= 0 AND ocr_confianza <= 1)
    );

CREATE INDEX idx_documento_ocr_estado ON documento_digital(ocr_estado);
