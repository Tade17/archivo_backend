ALTER TABLE documento_digital
    ADD COLUMN ocr_layout TEXT,
    ADD COLUMN ruta_pdf VARCHAR(500),
    ADD COLUMN ocr_version BIGINT NOT NULL DEFAULT 0;
