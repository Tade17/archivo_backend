ALTER TABLE documento_digital
    ADD COLUMN ocr_job_id VARCHAR(32),
    ADD COLUMN ocr_job_capability VARCHAR(64),
    ADD COLUMN ocr_engine_fingerprint VARCHAR(64),
    ADD COLUMN ocr_job_submitted BOOLEAN,
    ADD COLUMN ocr_lease_owner VARCHAR(32),
    ADD COLUMN ocr_lease_expires_at TIMESTAMP,
    ADD COLUMN ocr_next_retry_at TIMESTAMP,
    ADD COLUMN ocr_retries INTEGER,
    ADD COLUMN ocr_paginas_procesadas INTEGER,
    ADD COLUMN ocr_paginas_totales INTEGER,
    ADD COLUMN ocr_fase VARCHAR(30);

CREATE INDEX idx_documento_ocr_queue
    ON documento_digital (ocr_estado, ocr_next_retry_at, fecha_digitalizacion)
    WHERE ocr_estado IN ('PENDIENTE', 'PROCESANDO');
